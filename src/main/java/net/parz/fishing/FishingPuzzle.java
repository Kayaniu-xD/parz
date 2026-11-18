package net.parz.fishing;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.parz.Debug;
import net.parz.config.ParzConfigDefinition;
import net.parz.hollows.ItemIcons;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A memory game for the three big lava sea creatures of the Crimson Isle: Lord Jawbus, Thunder and Ragnarok.
 * When one of them is dead, a board with every item it can drop is shown, all cards face up. Then every card of an
 * item you did NOT get is turned around; the cards of your drops stay open (and glow).
 *
 * How it knows: the spawn message of the creature starts it, its name tag disappearing means it is dead, and what
 * you got is read from the "... DROP!" chat lines and from the items that arrive in your inventory.
 */
public final class FishingPuzzle {
	/** One thing a creature can drop. {@code keys} are lower case parts of the name that identify it in the chat. */
	private record Loot(String name, String amount, String chance, double percent, String... keys) {
	}

	/** A creature: how it announces itself, the part of its name tag, and everything it drops. */
	private record Creature(String id, String title, String announcement, String tag, int color, List<Loot> loot) {
	}

	private static final List<Creature> CREATURES = List.of(
			new Creature("jawbus", "Lord Jawbus", "lord jawbus has arrived", "lord jawbus", 0xFFFF5555, List.of(
					new Loot("Silver Magmafish", "16x", "100%", 100, "silver magmafish"),
					new Loot("Magma Lord Fragment", "1x", "50%", 50, "magma lord fragment"),
					new Loot("Bobbin' Scriptures", "1x", "2.5%", 2.5, "bobbin' scriptures", "bobbin scriptures"),
					new Loot("Radioactive Vial", "1x", "1.25%", 1.25, "radioactive vial"),
					new Loot("Carmine Dye", "1x", "0.004%", 0.004, "carmine dye"))),
			new Creature("thunder", "Thunder", "thunder emerges", "thunder", 0xFF55FFFF, List.of(
					new Loot("Silver Magmafish", "4x", "100%", 100, "silver magmafish"),
					new Loot("Thunder Fragment", "1x", "50%", 50, "thunder fragment"),
					new Loot("Enchanted Book (Flash I)", "1x", "4%", 4, "flash"),
					new Loot("Carmine Dye", "1x", "0.002%", 0.002, "carmine dye"))),
			new Creature("ragnarok", "Ragnarok", "ragnarok is here", "ragnarok", 0xFFFFAA00, List.of(
					new Loot("Enchanted Magma Cream", "8x", "100%", 100, "enchanted magma cream"),
					new Loot("Enchanted Coal", "8x", "100%", 100, "enchanted coal"),
					new Loot("Enchanted Bone", "8x", "100%", 100, "enchanted bone"),
					new Loot("Whipped Magma Cream", "1x", "5%", 5, "whipped magma cream"),
					new Loot("Enchanted Coal Block", "1x", "5%", 5, "enchanted coal block"),
					new Loot("Enchanted Bone Block", "1x", "5%", 5, "enchanted bone block"),
					new Loot("Chain of the End Times", "1x", "2%", 2, "chain of the end times"),
					new Loot("Brimstone Handle", "1x", "1.5%", 1.5, "brimstone handle"),
					new Loot("Burnt Texts", "1x", "1%", 1, "burnt texts"),
					new Loot("Carmine Dye", "1x", "0.004%", 0.004, "carmine dye"))));

	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final long ACTIVE_MS = 8 * 60_000L;
	private static final long DEATH_GRACE_MS = 1500;
	private static final long DROP_WINDOW_MS = 4500;
	/** A creature that vanishes farther away than this was not killed by you (it was unloaded). */
	private static final double MAX_KILL_DISTANCE = 48;

	// ---- what is going on while you fish ----
	private static Creature active = null;
	private static long activeSince = 0;
	private static boolean seenTag = false;
	private static double lastDistance = 0;
	private static long lastSeenTag = 0;
	/** When the drops are complete and the board is shown (0 = not decided yet). */
	private static long showAt = 0;
	private static final Set<Loot> got = new HashSet<>();
	private static Map<String, Integer> inventory = null;
	private static int scanTick = 0;

	// ---- the board ----
	private static boolean playing = false;
	private static Creature board = null;
	private static final Set<Loot> boardGot = new HashSet<>();
	private static long startMs = 0;
	private static long endMs = 0;
	private static final boolean[] flipSound = new boolean[16];

	private static final int CARD_W = 52;
	private static final int CARD_H = 66;
	private static final int GAP = 6;
	private static final int MAX_COLUMNS = 5;
	private static final long SHOW_ALL_MS = 700;
	private static final long FLIP_STEP_MS = 170;
	private static final long FLIP_MS = 350;

	private FishingPuzzle() {
	}

	public static void register() {
		List<String> names = new ArrayList<>();
		for (Creature creature : CREATURES) {
			for (Loot loot : creature.loot()) {
				names.add(loot.name().replace(" (Flash I)", ""));
			}
		}
		ItemIcons.prefetch(names);
		ClientReceiveMessageEvents.GAME.register(Debug.message("fishing chat", (message, overlay) -> {
			if (!overlay && enabled()) {
				onMessage(FORMATTING.matcher(message.getString()).replaceAll("").trim());
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("fishing tick", FishingPuzzle::tick));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "fishing_puzzle"),
				Debug.hud("fishing puzzle", FishingPuzzle::render));
	}

	private static boolean enabled() {
		return ParzConfigDefinition.FISHING_PUZZLE.isEnabled() && SkyBlockDetector.featuresActive();
	}

	// ======================================================================================
	// Watching the fight
	// ======================================================================================

	private static void onMessage(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (Creature creature : CREATURES) {
			if (lower.contains(creature.announcement())) {
				startWatching(creature, System.currentTimeMillis());
				Debug.log("fishing", () -> creature.title() + " spawned, waiting for its death");
				return;
			}
		}
		if (active != null && lower.contains("drop!")) {
			Loot loot = match(active, lower);
			if (loot != null) {
				gotLoot(loot, "chat: " + text);
			}
		}
	}

	/** The loot of the creature whose name is in the text (the longest key wins: "enchanted coal block" before "enchanted coal"). */
	private static Loot match(Creature creature, String lowerText) {
		Loot best = null;
		int bestLength = 0;
		for (Loot loot : creature.loot()) {
			for (String key : loot.keys()) {
				if (lowerText.contains(key) && key.length() > bestLength) {
					best = loot;
					bestLength = key.length();
				}
			}
		}
		return best;
	}

	private static void gotLoot(Loot loot, String why) {
		if (got.add(loot)) {
			Debug.log("fishing", () -> "drop: " + loot.name() + " (" + why + ")");
		}
		// the creature may not have been seen die (its name tag is not found): the drops start the board as well
		if (showAt == 0) {
			showAt = System.currentTimeMillis() + DROP_WINDOW_MS;
		}
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (!enabled() || client.player == null || client.level == null) {
			active = null;
			return;
		}
		if (active != null && now - activeSince > ACTIVE_MS) {
			active = null;
		}
		// the name tag of a creature: while it is there it lives. This also finds a creature whose spawn message
		// was not seen (somebody else fished it up, you joined late, ...).
		if (++scanTick >= 5) {
			scanTick = 0;
			Creature seen = null;
			double distance = 0;
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.player.Player) {
					continue;
				}
				// the name tag, or the name of the mob itself
				Component component = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName() : entity.getName();
				String name = FORMATTING.matcher(component.getString()).replaceAll("").toLowerCase(Locale.ROOT);
				Creature creature = creatureOfTag(name);
				if (creature != null && (active == null || creature == active)) {
					double d = entity.distanceTo(client.player);
					if (seen == null || d < distance) {
						seen = creature;
						distance = d;
					}
				}
			}
			if (seen != null) {
				if (active == null) {
					startWatching(seen, now);
					final Creature found = seen;
					Debug.log("fishing", () -> found.title() + " found by its name tag, waiting for its death");
				}
				seenTag = true;
				lastSeenTag = now;
				lastDistance = distance;
			} else if (active != null && seenTag && now - lastSeenTag > DEATH_GRACE_MS && showAt == 0) {
				if (lastDistance <= MAX_KILL_DISTANCE || !got.isEmpty()) {
					showAt = now + DROP_WINDOW_MS;
					Debug.log("fishing", () -> active.title() + " is dead (last seen " + Math.round(lastDistance) + " blocks away), collecting the drops");
				} else {
					Debug.log("fishing", () -> active.title() + " vanished " + Math.round(lastDistance) + " blocks away: no kill");
					active = null;
				}
			}
		}
		if (active == null) {
			return;
		}
		// what arrives in the inventory
		Map<String, Integer> current = new HashMap<>();
		for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
			ItemStack stack = client.player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				current.merge(FORMATTING.matcher(stack.getHoverName().getString()).replaceAll("").toLowerCase(Locale.ROOT),
						stack.getCount(), Integer::sum);
			}
		}
		if (inventory != null) {
			for (Map.Entry<String, Integer> entry : current.entrySet()) {
				if (entry.getValue() > inventory.getOrDefault(entry.getKey(), 0)) {
					Loot loot = match(active, entry.getKey());
					if (loot != null) {
						gotLoot(loot, "inventory");
					}
				}
			}
		}
		inventory = current;
		if (showAt != 0 && now >= showAt) {
			Creature creature = active;
			Set<Loot> result = new HashSet<>(got);
			active = null;
			showAt = 0;
			start(creature, result);
		}
	}

	private static void startWatching(Creature creature, long now) {
		active = creature;
		activeSince = now;
		seenTag = false;
		showAt = 0;
		got.clear();
		inventory = null;
	}

	/** Part of a name tag that only a living mob has: its level "[Lv597]", its health "419M/600M" or the heart. */
	private static final Pattern MOB_TAG = Pattern.compile("lv\\s*\\d+|\\d[kmb]?\\s*/\\s*\\d|❤");
	private static final Set<String> LOGGED_NAMES = new HashSet<>();

	/**
	 * The creature of a name tag (lower case, colors removed), or null. The tag does not always look the same
	 * ("[Lv597] [symbols] Lord Jawbus 419M/600M [heart]"), so only the name has to be in it, together with
	 * something that shows it is a living mob (the level, the health or the heart).
	 */
	private static Creature creatureOfTag(String name) {
		for (Creature creature : CREATURES) {
			if (name.contains(creature.tag())) {
				if (MOB_TAG.matcher(name).find()) {
					return creature;
				}
				if (LOGGED_NAMES.add(name)) {
					Debug.log("fishing", () -> "a name with \"" + creature.tag() + "\" that does not look like a mob: \"" + name + "\"");
				}
			}
		}
		return null;
	}

	// ======================================================================================
	// The board
	// ======================================================================================

	/** /pz fishing [jawbus|thunder|ragnarok]: a test with some random drops. */
	public static void test(String id) {
		Random random = new Random();
		Creature creature = CREATURES.get(random.nextInt(CREATURES.size()));
		for (Creature candidate : CREATURES) {
			if (candidate.id().equalsIgnoreCase(id)) {
				creature = candidate;
			}
		}
		Set<Loot> result = new HashSet<>();
		for (Loot loot : creature.loot()) {
			if (loot.percent() >= 100 || random.nextDouble() < 0.3) {
				result.add(loot);
			}
		}
		start(creature, result);
	}

	private static void start(Creature creature, Set<Loot> result) {
		board = creature;
		boardGot.clear();
		boardGot.addAll(result);
		java.util.Arrays.fill(flipSound, false);
		int flips = creature.loot().size() - boardGot.size();
		long flipEnd = SHOW_ALL_MS + Math.max(0, flips) * FLIP_STEP_MS + FLIP_MS;
		endMs = flipEnd + 4000;
		startMs = System.currentTimeMillis();
		playing = true;
		sound(1.0f);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!playing || board == null) {
			return;
		}
		long t = System.currentTimeMillis() - startMs;
		if (t > endMs + 250) {
			playing = false;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();

		List<Loot> loot = board.loot();
		int columns = Math.min(MAX_COLUMNS, loot.size());
		int rows = (loot.size() + columns - 1) / columns;
		int panelW = columns * CARD_W + (columns - 1) * GAP + 36;
		int panelH = 34 + rows * CARD_H + (rows - 1) * GAP + 34;

		double slide = 1.0;
		if (t < 150) {
			slide = easeOut(t / 150.0);
		} else if (t > endMs) {
			slide = 1.0 - easeOut((t - endMs) / 250.0);
		}
		float scale = (float) Math.min(1.6, Math.min(screenH * 0.8 / panelH, screenW * 0.9 / panelW));
		int x = (screenW - panelW) / 2;
		int y = (int) Math.round((screenH - panelH) / 2.0 - (1.0 - slide) * ((screenH + panelH * scale) / 2.0 + 10));
		// the rest of the screen gets darker while the memory is shown (fades in and out with the board)
		int dim = (int) Math.round(0xB0 * Math.max(0, Math.min(1, slide)));
		graphics.fill(0, 0, screenW, screenH, dim << 24);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + panelW / 2f, y + panelH / 2f);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-(x + panelW / 2f), -(y + panelH / 2f));

		graphics.fill(x, y, x + panelW, y + panelH, 0xEE0E0E14);
		graphics.outline(x, y, panelW, panelH, 0xFF8A6A1F);
		graphics.outline(x + 2, y + 2, panelW - 4, panelH - 4, 0xFF2A2A33);
		String title = board.title() + " - Loot Memory";
		graphics.text(font, title, x + (panelW - font.width(title)) / 2, y + 9, board.color(), true);
		boolean allDone = t >= SHOW_ALL_MS + (loot.size() - boardGot.size()) * FLIP_STEP_MS + FLIP_MS;
		String hint = t < SHOW_ALL_MS ? "Remember the loot ..." : allDone ? "These are your drops" : "Turning around what you did not get ...";
		graphics.text(font, hint, x + (panelW - font.width(hint)) / 2, y + 21, 0xFF9A9AA6, true);

		int flipIndex = 0;
		for (int i = 0; i < loot.size(); i++) {
			Loot item = loot.get(i);
			int col = i % columns;
			int row = i / columns;
			int cx = x + 18 + col * (CARD_W + GAP);
			int cy = y + 34 + row * (CARD_H + GAP);
			boolean has = boardGot.contains(item);
			double flip = 0; // 0 = face up, 1 = turned around
			if (!has) {
				long start = SHOW_ALL_MS + flipIndex * FLIP_STEP_MS;
				flip = Math.max(0, Math.min(1, (t - start) / (double) FLIP_MS));
				if (flip >= 0.5 && flipIndex < flipSound.length && !flipSound[flipIndex]) {
					flipSound[flipIndex] = true;
					sound(0.8f + (flipIndex % 5) * 0.12f);
				}
				flipIndex++;
			}
			drawCard(graphics, font, item, has, flip, cx, cy, t);
		}

		// what you got
		StringBuilder gotText = new StringBuilder();
		for (Loot item : loot) {
			if (boardGot.contains(item) && item.percent() < 100) {
				gotText.append(gotText.isEmpty() ? "" : ", ").append(item.name());
			}
		}
		String footer = !allDone ? "" : gotText.isEmpty() ? "Only the common drops this time" : "Rare: " + gotText;
		int footerColor = gotText.isEmpty() ? 0xFF8A8A96 : 0xFFFFFF55;
		for (FormattedCharSequence line : font.split(Component.literal(footer), panelW - 24)) {
			graphics.text(font, line, x + (panelW - font.width(line)) / 2, y + panelH - 22, footerColor, true);
			break;
		}
		graphics.pose().popMatrix();
	}

	/** A card: face up (picture, name, chance) or turned around (back); flip goes 0 to 1 and squeezes it like a real card. */
	private static void drawCard(GuiGraphicsExtractor graphics, Font font, Loot item, boolean has, double flip, int cx, int cy, long t) {
		boolean back = flip >= 0.5;
		double squeeze = Math.abs(Math.cos(flip * Math.PI)); // 1 -> 0 -> 1
		float midX = cx + CARD_W / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(midX, 0);
		graphics.pose().scale((float) Math.max(0.02, squeeze), 1f);
		graphics.pose().translate(-midX, 0);

		int rarity = rarityColor(item.percent());
		if (back) {
			graphics.fill(cx, cy, cx + CARD_W, cy + CARD_H, 0xFF1B2A4A);
			graphics.outline(cx, cy, CARD_W, CARD_H, 0xFF4A5A7A);
			graphics.outline(cx + 3, cy + 3, CARD_W - 6, CARD_H - 6, 0xFF2E3F66);
			graphics.text(font, "?", cx + (CARD_W - font.width("?")) / 2, cy + CARD_H / 2 - 4, 0xFF6A7FA8, true);
		} else {
			boolean glow = has && t > SHOW_ALL_MS && (t / 300) % 2 == 0;
			graphics.fill(cx, cy, cx + CARD_W, cy + CARD_H, 0xFF16161E);
			graphics.outline(cx, cy, CARD_W, CARD_H, has && t > SHOW_ALL_MS ? (glow ? 0xFFFFFF55 : 0xFFFFAA00) : rarity);
			drawIcon(graphics, item.name().replace(" (Flash I)", ""), cx + (CARD_W - 24) / 2, cy + 5);
			// the name and the chance are small
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx + CARD_W / 2f, cy + 33);
			graphics.pose().scale(0.7f, 0.7f);
			int lineY = 0;
			List<FormattedCharSequence> lines = font.split(Component.literal(item.amount() + " " + item.name()), (int) ((CARD_W - 4) / 0.7f));
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				graphics.text(font, lines.get(i), -font.width(lines.get(i)) / 2, lineY, 0xFFFFFFFF, true);
				lineY += 9;
			}
			graphics.pose().popMatrix();
			String chance = item.chance();
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx + CARD_W / 2f, cy + CARD_H - 10);
			graphics.pose().scale(0.7f, 0.7f);
			graphics.text(font, chance, -font.width(chance) / 2, 0, rarity, true);
			graphics.pose().popMatrix();
		}
		graphics.pose().popMatrix();
	}

	private static int rarityColor(double percent) {
		if (percent <= 0.05) return 0xFFFF55FF;
		if (percent <= 2) return 0xFFFFAA00;
		if (percent <= 6) return 0xFFC25BFF;
		if (percent < 100) return 0xFF6E8CFF;
		return 0xFF55FF55;
	}

	// ======================================================================================
	// Icons (the wiki picture, a vanilla item until it is downloaded) and small helpers
	// ======================================================================================

	private static final Map<String, ItemStack> FALLBACKS = new HashMap<>();

	private static void drawIcon(GuiGraphicsExtractor graphics, String name, int x, int y) {
		Identifier texture = ItemIcons.textureFor(name);
		if (texture != null) {
			int size = ItemIcons.size();
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 24, 24, size, size, size, size);
			return;
		}
		ItemStack stack = FALLBACKS.computeIfAbsent(name, FishingPuzzle::fallback);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	private static ItemStack fallback(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		ItemStack stack;
		if (n.contains("magmafish")) stack = new ItemStack(Items.MAGMA_CREAM);
		else if (n.contains("dye")) stack = new ItemStack(Items.DYE.pick(DyeColor.RED));
		else if (n.contains("fragment")) stack = new ItemStack(Items.NETHER_STAR);
		else if (n.contains("vial")) stack = new ItemStack(Items.POTION);
		else if (n.contains("scriptures") || n.contains("texts") || n.contains("book") || n.contains("flash")) stack = new ItemStack(Items.ENCHANTED_BOOK);
		else if (n.contains("magma cream")) stack = new ItemStack(Items.MAGMA_CREAM);
		else if (n.contains("coal")) stack = new ItemStack(Items.COAL);
		else if (n.contains("bone")) stack = new ItemStack(Items.BONE);
		else if (n.contains("chain")) stack = new ItemStack(Items.IRON_BARS);
		else if (n.contains("brimstone")) stack = new ItemStack(Items.BLAZE_ROD);
		else stack = new ItemStack(Items.PAPER);
		if (n.contains("enchanted") || n.contains("block") || n.contains("fragment")) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
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
}
