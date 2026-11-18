package net.parz.hollows;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.parz.Debug;
import net.parz.config.ParzConfigDefinition;
import net.parz.pets.PetCollection;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The slot machine for a Crystal Nucleus run. When the loot bundle of a run is shown in the chat ("CRYSTAL NUCLEUS
 * LOOT BUNDLE" followed by the rewards) three reels spin on your screen and stop on the rarest item you got.
 * With a Divan's Alloy all three reels stop on the alloy, like three sevens: jackpot.
 * <p>
 * How rare an item is comes from the loot table of the SkyBlock wiki (the weight of the item: the smaller, the
 * rarer). Items that are not in the table are rated by the color of their name.
 */
public final class NucleusSlot {
	/** A reward of the loot bundle. weight: the smaller, the rarer. */
	private record Drop(String name, int quantity, double weight) {
	}

	/** Sum of all weights of the loot table; chance of an item per roll = weight / TOTAL_WEIGHT. */
	private static final double TOTAL_WEIGHT = 307_125.0;
	private static final Pattern QUANTITY_FIRST = Pattern.compile("^(\\d[\\d,]*)\\s*[x×]\\s+(.+)$");
	private static final Pattern QUANTITY_LAST = Pattern.compile("^(.+?)\\s*[x×]\\s*(\\d[\\d,]*)$");
	private static final Pattern QUANTITY_BRACKET = Pattern.compile("^(.+?)\\s*\\((\\d[\\d,]*)\\)$");
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final double[] RARITY_WEIGHTS = {20000, 10000, 3000, 800, 150, 30};

	// ---- reading the chat ----
	private static boolean collecting = false;
	private static long lastLineMs = 0;
	private static final List<Drop> collected = new ArrayList<>();

	// ---- the animation ----
	private static boolean playing = false;
	private static long startMs = 0;
	private static boolean jackpot = false;
	private static Drop best = null;
	private static final List<String>[] STRIPS = newStrips();
	private static final long[] STOP_MS = new long[3];
	private static final boolean[] stopped = new boolean[3];
	private static long endMs = 0;

	private static final int CELL = 40;
	private static final int GAP = 8;
	private static final int PANEL_W = 3 * CELL + 2 * GAP + 36;
	private static final int PANEL_H = 118;
	private static final int GOLD = 0xFFFFAA00;

	@SuppressWarnings("unchecked")
	private static List<String>[] newStrips() {
		return new List[]{new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>()};
	}

	private NucleusSlot() {
	}

	public static void register() {
		// the pictures of everything the Nucleus can give are fetched now, so they are there when the machine runs
		ItemIcons.prefetch(java.util.List.of("Divan's Alloy", "Divan Fragment", "Jade Dye", "Prehistoric Egg", "Precious Pearl",
				"Wishing Compass", "Claw Fossil", "Helix Fossil", "Quick Claw", "Recall Potion", "Treasurite", "Gemstone Mixture",
				"Enchanted Book", "Gold Essence", "Diamond Essence", "Jaderald", "Dwarven O's Gemstone Grahams", "Oil Barrel",
				"Pickonimbus 2000", "Bob-omb", "Ruby Crystal", "Jasper Crystal", "Fine Jasper Gemstone", "Flawed Jasper Gemstone",
				"Flawless Jasper Gemstone", "Flawless Ruby Gemstone", "Flawed Ruby Gemstone", "Fine Ruby Gemstone"));
		ClientReceiveMessageEvents.GAME.register(Debug.message("nucleus chat", (message, overlay) -> {
			if (!overlay && enabled()) {
				onMessage(message);
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("nucleus tick", client -> {
			// the rewards are all read: nothing came for a moment, so the list is complete
			if (collecting && System.currentTimeMillis() - lastLineMs > 2500) {
				finish();
			}
		}));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "nucleus_slot"),
				Debug.hud("nucleus slot machine", NucleusSlot::render));
	}

	private static boolean enabled() {
		return ParzConfigDefinition.NUCLEUS_SLOT.isEnabled() && SkyBlockDetector.featuresActive();
	}

	// ======================================================================================
	// Reading the loot bundle
	// ======================================================================================

	private static void onMessage(Component message) {
		String all = FORMATTING.matcher(message.getString()).replaceAll("");
		boolean singleLine = all.indexOf('\n') < 0;
		for (String raw : all.split("\n")) {
			String line = raw.strip();
			if (line.toUpperCase(Locale.ROOT).contains("CRYSTAL NUCLEUS LOOT BUNDLE")) {
				collecting = true;
				collected.clear();
				lastLineMs = System.currentTimeMillis();
				Debug.log("nucleus", () -> "loot bundle banner recognised, reading the rewards");
				continue;
			}
			if (!collecting || line.isEmpty() || line.chars().noneMatch(Character::isLetterOrDigit)) {
				continue; // empty lines and separators (------, ▬▬▬)
			}
			String upper = line.toUpperCase(Locale.ROOT);
			if (upper.equals("REWARDS") || upper.startsWith("REWARDS")) {
				continue;
			}
			if (upper.startsWith("PICK IT UP")) {
				if (!collected.isEmpty()) {
					finish();
				}
				continue;
			}
			if (upper.startsWith("GEMSTONE POWDER") || upper.startsWith("MITHRIL POWDER") || upper.contains("HOTM")
					|| upper.contains("HEART OF THE MOUNTAIN")) {
				continue; // not an item
			}
			Drop drop = parse(line, singleLine ? message : null);
			if (drop != null) {
				collected.add(drop);
				lastLineMs = System.currentTimeMillis();
				Debug.log("nucleus", () -> "reward: " + drop.quantity() + "x " + drop.name() + " (weight " + drop.weight() + ")");
				if (collected.size() >= 40) {
					finish();
					return;
				}
			}
		}
	}

	/** One line of the rewards: "Flawed Ruby Gemstone x12", "12x Treasurite", "Prehistoric Egg" ... */
	private static Drop parse(String line, Component colored) {
		String text = line.replaceFirst("^[+•*\\-\\s]+", "").strip();
		int quantity = 1;
		Matcher m;
		if ((m = QUANTITY_FIRST.matcher(text)).matches()) {
			quantity = number(m.group(1));
			text = m.group(2);
		} else if ((m = QUANTITY_LAST.matcher(text)).matches()) {
			quantity = number(m.group(2));
			text = m.group(1);
		} else if ((m = QUANTITY_BRACKET.matcher(text)).matches()) {
			quantity = number(m.group(2));
			text = m.group(1);
		}
		text = text.strip();
		if (text.length() < 3) {
			return null;
		}
		double weight = weightOf(text);
		if (weight < 0 && colored != null) {
			int rarity = PetCollection.rarityOf(colored); // the rarity colors are the same as for pets
			weight = rarity >= 0 ? RARITY_WEIGHTS[Math.min(rarity, RARITY_WEIGHTS.length - 1)] : -1;
		}
		return new Drop(text, quantity, weight < 0 ? 5000 : weight);
	}

	private static int number(String text) {
		try {
			return Integer.parseInt(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** The weight of an item in the loot table of the Crystal Nucleus (smaller = rarer), -1 if it is not in it. */
	static double weightOf(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.contains("jade dye")) return 0.1;
		if (n.contains("alloy")) return 17;
		if (n.contains("flawless jasper")) return 5;
		if (n.contains("flawless")) return 15;
		if (n.contains("quick claw")) return 50;
		if (n.contains("fine jasper")) return 200;
		if (n.contains("treasurite")) return 1000;
		if (n.contains("recall potion")) return 250;
		if (n.contains("wishing compass") || n.contains("precious pearl") || n.contains("claw fossil")) return 500;
		if (n.contains("gemstone mixture") || n.contains("fortune")) return 700;
		if (n.contains("gold essence") || n.contains("diamond essence")) return 750;
		if (n.contains("fine ")) return 900;
		if (n.contains("jaderald") || n.contains("grahams")) return 1450;
		if (n.contains("flawed jasper")) return 1500;
		if (n.contains("crystal")) return 2500;
		if (n.contains("helix fossil") || n.contains("lapidary")) return 4000;
		if (n.contains("prehistoric egg")) return 5000;
		if (n.contains("divan fragment")) return 6000;
		if (n.contains("oil barrel")) return 8000;
		if (n.contains("pickonimbus") || n.contains("bob-omb")) return 10000;
		if (n.contains("flawed")) return 8000;
		return -1;
	}

	private static void finish() {
		collecting = false;
		if (collected.isEmpty()) {
			return;
		}
		List<Drop> drops = new ArrayList<>(collected);
		collected.clear();
		Drop rarest = drops.get(0);
		boolean alloy = false;
		for (Drop drop : drops) {
			if (drop.weight() < rarest.weight()) {
				rarest = drop;
			}
			alloy |= drop.name().toLowerCase(Locale.ROOT).contains("alloy");
		}
		if (alloy) {
			for (Drop drop : drops) {
				if (drop.name().toLowerCase(Locale.ROOT).contains("alloy")) {
					rarest = drop;
				}
			}
		}
		final Drop shown = rarest;
		final boolean shownAlloy = alloy;
		Debug.log("nucleus", () -> drops.size() + " rewards read, rarest: " + shown.name() + (shownAlloy ? " (ALLOY: jackpot)" : ""));
		start(drops, rarest, alloy);
	}

	/** A test run for /pz nucleus. */
	public static void test(boolean withAlloy) {
		List<Drop> drops = new ArrayList<>();
		drops.add(new Drop("Flawed Ruby Gemstone", 24, weightOf("Flawed Ruby Gemstone")));
		drops.add(new Drop("Prehistoric Egg", 1, weightOf("Prehistoric Egg")));
		drops.add(new Drop("Precious Pearl", 1, weightOf("Precious Pearl")));
		drops.add(new Drop("Divan Fragment", 1, weightOf("Divan Fragment")));
		Drop rarest;
		if (withAlloy) {
			rarest = new Drop("Divan's Alloy", 1, weightOf("Divan's Alloy"));
			drops.add(rarest);
		} else {
			rarest = new Drop("Fine Jasper Gemstone", 1, weightOf("Fine Jasper Gemstone"));
			drops.add(rarest);
		}
		start(drops, rarest, withAlloy);
	}

	// ======================================================================================
	// The animation
	// ======================================================================================

	private static void start(List<Drop> drops, Drop rarest, boolean alloy) {
		Random random = new Random();
		best = rarest;
		jackpot = alloy;

		// what the reels show while they spin: the rewards, and some other loot of the Nucleus
		List<String> pool = new ArrayList<>();
		for (Drop drop : drops) {
			pool.add(drop.name());
		}
		for (String extra : new String[]{"Ruby Crystal", "Jasper Crystal", "Prehistoric Egg", "Divan Fragment", "Precious Pearl",
				"Flawed Amber Gemstone", "Oil Barrel", "Helix Fossil", "Wishing Compass", "Recall Potion", "Divan's Alloy"}) {
			pool.add(extra);
		}
		List<String> others = new ArrayList<>();
		for (Drop drop : drops) {
			if (!drop.name().equals(rarest.name())) {
				others.add(drop.name());
			}
		}
		if (others.isEmpty()) {
			others.add("Prehistoric Egg");
		}
		for (int reel = 0; reel < 3; reel++) {
			List<String> strip = STRIPS[reel];
			strip.clear();
			int length = 26 + reel * 9 + (alloy && reel == 2 ? 10 : 0);
			String last = "";
			for (int i = 0; i < length - 1; i++) {
				String pick;
				do {
					pick = pool.get(random.nextInt(pool.size()));
				} while (pick.equals(last) && pool.size() > 1);
				last = pick;
				strip.add(pick);
			}
			// the last item is where the reel stops: three alloys for the jackpot, otherwise the rarest item
			// in the middle with other rewards at its sides
			String target = alloy || reel == 1 ? rarest.name() : others.get(random.nextInt(others.size()));
			strip.add(target);
		}
		STOP_MS[0] = 2200;
		STOP_MS[1] = 3100;
		STOP_MS[2] = alloy ? 5200 : 4000; // the last reel of the jackpot takes its time
		java.util.Arrays.fill(stopped, false);
		long resultTime = STOP_MS[2] + (alloy ? 6500 : 4500);
		endMs = resultTime + 500;
		startMs = System.currentTimeMillis();
		playing = true;
		sound(1.0f);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!playing) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		long t = System.currentTimeMillis() - startMs;
		if (t > endMs + 500) {
			playing = false;
			return;
		}
		Font font = client.font;
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();

		// slides in from the top and out again
		double slide = 1.0;
		if (t < 400) {
			slide = easeOut(t / 400.0);
		} else if (t > endMs) {
			slide = 1.0 - easeOut((t - endMs) / 500.0);
		}
		int x = (screenW - PANEL_W) / 2;
		// in the middle of the screen; it comes in from the top
		int y = (int) Math.round((screenH - PANEL_H) / 2.0 - (1.0 - slide) * ((screenH + PANEL_H * 1.8) / 2.0 + 10));

		// the whole machine is drawn bigger, around its own middle (so it stays in the middle of the screen)
		float scale = (float) Math.min(1.8, Math.min(screenH * 0.8 / PANEL_H, screenW * 0.9 / PANEL_W));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + PANEL_W / 2f, y + PANEL_H / 2f);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-(x + PANEL_W / 2f), -(y + PANEL_H / 2f));

		long flash = t / 140;
		boolean allReelsStopped = stopped[0] && stopped[1] && stopped[2];
		int border = jackpot && allReelsStopped ? (flash % 2 == 0 ? 0xFFFFFF55 : GOLD) : 0xFF8A6A1F;

		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xEE0E0E14);
		graphics.outline(x, y, PANEL_W, PANEL_H, border);
		graphics.outline(x + 2, y + 2, PANEL_W - 4, PANEL_H - 4, 0xFF2A2A33);
		String title = jackpot && allReelsStopped ? "J A C K P O T !" : "NUCLEUS RUN";
		int titleColor = jackpot && allReelsStopped ? (flash % 2 == 0 ? 0xFFFFFF55 : 0xFFFFFFFF) : GOLD;
		graphics.text(font, title, x + (PANEL_W - font.width(title)) / 2, y + 8, titleColor, true);

		// the reels
		int reelsX = x + 18;
		int reelsY = y + 24;
		for (int reel = 0; reel < 3; reel++) {
			int rx = reelsX + reel * (CELL + GAP);
			graphics.fill(rx - 1, reelsY - 1, rx + CELL + 1, reelsY + CELL + 1, border);
			graphics.fill(rx, reelsY, rx + CELL, reelsY + CELL, 0xFF05050A);
			drawReel(graphics, reel, rx, reelsY, t);
			// the shadow at the top and bottom of the window, like a real reel
			graphics.fill(rx, reelsY, rx + CELL, reelsY + 4, 0x66000000);
			graphics.fill(rx, reelsY + CELL - 4, rx + CELL, reelsY + CELL, 0x66000000);
		}

		// the result
		if (allReelsStopped) {
			int textY = reelsY + CELL + 10;
			if (jackpot) {
				center(graphics, font, "Divan's Alloy x3", x, textY, flash % 2 == 0 ? 0xFFFFFF55 : GOLD);
				center(graphics, font, chanceText(best), x, textY + 12, 0xFFAAAAAA);
			} else {
				center(graphics, font, "Rarest drop", x, textY, 0xFF9A9AA6);
				center(graphics, font, best.name() + (best.quantity() > 1 ? " x" + best.quantity() : ""), x, textY + 11, rarityColor(best.weight()));
			}
			if (!jackpot) {
				center(graphics, font, chanceText(best), x, textY + 22, 0xFF8A8A96);
			}
			if (jackpot) {
				sparkles(graphics, x + PANEL_W / 2, y + PANEL_H / 2, t);
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawReel(GuiGraphicsExtractor graphics, int reel, int rx, int ry, long t) {
		List<String> strip = STRIPS[reel];
		if (strip.isEmpty()) {
			return;
		}
		int last = strip.size() - 1;
		double progress = Math.min(1.0, t / (double) STOP_MS[reel]);
		// fast at the start and slower and slower, in the jackpot the last reel crawls to the end
		double eased = 1.0 - Math.pow(1.0 - progress, jackpot && reel == 2 ? 4 : 3);
		double position = last * eased; // in items; the item at this position is in the middle
		if (progress >= 1.0 && !stopped[reel]) {
			stopped[reel] = true;
			sound(jackpot && reel == 2 ? 1.6f : 1.2f + reel * 0.15f);
		}
		graphics.enableScissor(rx, ry, rx + CELL, ry + CELL);
		int first = Math.max(0, (int) Math.floor(position) - 1);
		int lastShown = Math.min(last, (int) Math.ceil(position) + 1);
		for (int k = first; k <= lastShown; k++) {
			double offset = (position - k) * CELL; // 0 = in the middle of the window, positive = already below it
			int iy = (int) Math.round(ry + (CELL - 32) / 2.0 + offset);
			drawIcon(graphics, strip.get(k), rx + (CELL - 32) / 2, iy);
		}
		graphics.disableScissor();
	}

	private static void sparkles(GuiGraphicsExtractor graphics, int cx, int cy, long t) {
		int[] colors = {0xFFFFFF55, 0xFFFFAA00, 0xFFFFFFFF, 0xFFFF8855};
		for (int i = 0; i < 40; i++) {
			double angle = i * 2.399963 + t * 0.0011 * (1 + i % 3);
			double radius = 40 + ((t * 0.045 + i * 17) % 70);
			int px = cx + (int) Math.round(Math.cos(angle) * radius * 1.5);
			int py = cy + (int) Math.round(Math.sin(angle) * radius * 0.85);
			int size = 1 + (i % 3);
			graphics.fill(px, py, px + size, py + size, colors[i % colors.length]);
		}
	}

	private static void center(GuiGraphicsExtractor graphics, Font font, String text, int panelX, int y, int color) {
		graphics.text(font, text, panelX + (PANEL_W - font.width(text)) / 2, y, color, true);
	}

	private static String chanceText(Drop drop) {
		if (drop.weight() <= 0) {
			return "";
		}
		long oneIn = Math.round(TOTAL_WEIGHT / drop.weight());
		return "1 in " + String.format(Locale.ROOT, "%,d", oneIn) + " per roll";
	}

	private static int rarityColor(double weight) {
		if (weight <= 20) return 0xFFFF55FF;   // mythic
		if (weight <= 300) return 0xFFFFAA00;  // legendary
		if (weight <= 1500) return 0xFFC25BFF; // epic
		if (weight <= 5000) return 0xFF6E8CFF; // rare
		return 0xFF55FF55;                     // uncommon
	}

	private static double easeOut(double x) {
		double v = Math.max(0, Math.min(1, x));
		return 1 - Math.pow(1 - v, 3);
	}

	private static void sound(float pitch) {
		try {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
		} catch (RuntimeException ignored) {
			// no sound is not a problem
		}
	}

	// ======================================================================================
	// Icons: an item of the game that looks like the reward
	// ======================================================================================

	/** Cached vanilla stand-ins for the items that have no picture (yet). */
	private static final java.util.Map<String, ItemStack> FALLBACKS = new java.util.HashMap<>();

	/** An item 32x32: the picture of the wiki, or - until it is downloaded or if there is none - a vanilla item. */
	private static void drawIcon(GuiGraphicsExtractor graphics, String name, int x, int y) {
		Identifier texture = ItemIcons.textureFor(name);
		if (texture != null) {
			int size = ItemIcons.size();
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 32, 32, size, size, size, size);
			return;
		}
		ItemStack stack = FALLBACKS.computeIfAbsent(name, NucleusSlot::iconFor);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(2f, 2f);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	private static ItemStack iconFor(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		ItemStack stack;
		if (n.contains("alloy")) {
			stack = new ItemStack(Items.NETHERITE_INGOT);
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			return stack;
		}
		if (n.contains("jade dye")) return new ItemStack(Items.DYE.pick(DyeColor.LIME));
		if (n.contains("divan fragment")) return new ItemStack(Items.PRISMARINE_SHARD);
		if (n.contains("prehistoric egg")) return new ItemStack(Items.TURTLE_EGG);
		if (n.contains("precious pearl")) return new ItemStack(Items.ENDER_PEARL);
		if (n.contains("wishing compass")) return new ItemStack(Items.COMPASS);
		if (n.contains("claw fossil")) return new ItemStack(Items.BONE);
		if (n.contains("helix fossil")) return new ItemStack(Items.NAUTILUS_SHELL);
		if (n.contains("quick claw")) return new ItemStack(Items.FEATHER);
		if (n.contains("recall potion")) return new ItemStack(Items.POTION);
		if (n.contains("treasurite")) return new ItemStack(Items.IRON_INGOT);
		if (n.contains("gemstone mixture")) return new ItemStack(Items.AMETHYST_CLUSTER);
		if (n.contains("book") || n.contains("fortune") || n.contains("lapidary")) return new ItemStack(Items.ENCHANTED_BOOK);
		if (n.contains("gold essence")) return new ItemStack(Items.GOLD_INGOT);
		if (n.contains("diamond essence")) return new ItemStack(Items.DIAMOND);
		if (n.contains("jaderald")) return new ItemStack(Items.EMERALD);
		if (n.contains("grahams")) return new ItemStack(Items.COOKIE);
		if (n.contains("oil barrel")) return new ItemStack(Items.BARREL);
		if (n.contains("pickonimbus")) return new ItemStack(Items.DIAMOND_PICKAXE);
		if (n.contains("bob-omb")) return new ItemStack(Items.TNT);
		// gemstones and crystals: the color tells which one
		DyeColor color = null;
		if (n.contains("ruby")) color = DyeColor.RED;
		else if (n.contains("jasper")) color = DyeColor.PINK;
		else if (n.contains("amethyst")) color = DyeColor.PURPLE;
		else if (n.contains("jade")) color = DyeColor.LIME;
		else if (n.contains("amber")) color = DyeColor.ORANGE;
		else if (n.contains("sapphire")) color = DyeColor.LIGHT_BLUE;
		else if (n.contains("topaz")) color = DyeColor.YELLOW;
		if (color != null) {
			stack = n.contains("crystal") ? new ItemStack(Items.END_CRYSTAL) : new ItemStack(Items.DYE.pick(color));
			if (n.contains("fine") || n.contains("flawless") || n.contains("crystal")) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return stack;
		}
		return new ItemStack(Items.PAPER);
	}
}
