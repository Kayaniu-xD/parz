package net.parz.fishing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.parz.Debug;
import net.parz.ParzClient;
import net.parz.config.ParzConfigDefinition;
import net.parz.dungeon.DungeonInfo;
import net.parz.gui.HudEditorScreen;
import net.parz.hud.HudTrackers;
import net.parz.mixin.ItemModelListAccessor;
import net.parz.skyblock.SkyBlockDetector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Trophy Froggs": a HUD list of the trophy frogs of the Lotus Atoll with the tiers (bronze, silver, gold, diamond)
 * you already have and the ones you are missing. It is shown on the Lotus Atoll while you hold a water fishing rod.
 *
 * <p>The numbers come from two places: the menu "Trophy Frogs" of Researcher Ribery (read when you open it), and the
 * chat message of every catch ("TROPHY FROG! You caught your 491st Wetlands Frog BRONZE!"), whose number is how
 * many of that frog and tier you have now. Saved per Minecraft account in config/parz-frogs.json.
 */
public final class TrophyFrogs {
	/** The tiers in the order of the columns. */
	public enum Tier {
		BRONZE(0xFF74747F), SILVER(0xFFC4C4CE), GOLD(0xFFFFAA00), DIAMOND(0xFF55FFFF);

		private final int color;

		Tier(int color) {
			this.color = color;
		}
	}

	/** Every trophy frog, sorted by rarity (the color of its name). */
	public enum Frog {
		COMMON("Common Frog", ChatFormatting.WHITE, 0xFFFFFFFF),
		EXPLODING("Exploding Frog", ChatFormatting.GREEN, 0xFF55FF55),
		LEAP("Leap Frog", ChatFormatting.GREEN, 0xFF55FF55),
		WETLANDS("Wetlands Frog", ChatFormatting.GREEN, 0xFF55FF55),
		REALITY_HOPPER("Reality Hopper", ChatFormatting.GREEN, 0xFF55FF55),
		BLESSED("Blessed Frog", ChatFormatting.BLUE, 0xFF5555FF),
		BULLFROG("Bullfrog", ChatFormatting.BLUE, 0xFF5555FF),
		SEA("Sea Frog", ChatFormatting.BLUE, 0xFF5555FF),
		CAVE("Cave Frog", ChatFormatting.DARK_PURPLE, 0xFFAA00AA),
		HIGHLANDS("Highlands Frog", ChatFormatting.DARK_PURPLE, 0xFFAA00AA),
		TREE("Tree Frog", ChatFormatting.DARK_PURPLE, 0xFFAA00AA),
		PUDDLE_JUMPER("Puddle Jumper", ChatFormatting.GOLD, 0xFFFFAA00);

		public final String title;
		public final ChatFormatting rarity;
		private final int color;

		Frog(String title, ChatFormatting rarity, int color) {
			this.title = title;
			this.rarity = rarity;
			this.color = color;
		}

		static Frog byName(String name) {
			for (Frog frog : values()) {
				if (frog.title.equalsIgnoreCase(name)) {
					return frog;
				}
			}
			return null;
		}
	}

	/** One catch message: you now have this many of this frog in this tier. */
	public record Catch(Frog frog, Tier tier, int count) {
	}

	/** The number of a tier that is not known yet. */
	public static final int UNKNOWN = -1;

	private static final Pattern CAUGHT = Pattern.compile(
			"^\\s*TROPHY FROG! You caught your ([\\d,]+)(?:st|nd|rd|th) (.+?) (BRONZE|SILVER|GOLD|DIAMOND)(?: x\\d+)?!.*");
	/** A line of the menu: "Bronze ✔ (123)" or "Diamond ✖". */
	private static final Pattern TIER_LINE = Pattern.compile(
			"^[^A-Za-z]*(Bronze|Silver|Gold|Diamond)\\s+([^\\s(]+)(?:\\s*\\(([\\d,]+)\\))?[^A-Za-z0-9]*$");
	private static final String MENU_TITLE = "Trophy Frogs";
	private static final List<String> SIDEBAR_AREAS = List.of("Lotus Atoll", "Lotus Eater's Cave", "Lotus Highlands", "Tewtil Tunnel");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-frogs.json");
	private static final int ROW = 11;

	private static final int[][] counts = new int[Frog.values().length][Tier.values().length];
	/** True once the menu of Researcher Ribery was read. */
	private static boolean synced = false;
	private static boolean visible = false;
	private static int ticks = 0;
	/** The menu that is open and what was already said about it. */
	private static Screen menu = null;
	private static int menuReads = 0;
	private static boolean menuReported = false;
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	static {
		clear();
	}

	private TrophyFrogs() {
	}

	private static void clear() {
		for (int[] tiers : counts) {
			Arrays.fill(tiers, UNKNOWN);
		}
		synced = false;
	}

	public static void register() {
		load();
		// read the message before other mods can hide or change it
		Identifier early = Identifier.fromNamespaceAndPath("parz", "trophy_frogs");
		ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ClientReceiveMessageEvents.ALLOW_GAME.register(early, (message, overlay) -> {
			try {
				if (!overlay && enabled() && !Debug.isOwnMessage(message.getString())) {
					Catch caught = parseCatch(message.getString());
					if (caught != null) {
						apply(caught);
					}
				}
			} catch (RuntimeException e) {
				Debug.error("trophy frog chat", e);
			}
			return true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("trophy frogs", TrophyFrogs::tick));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "trophy_frogs"),
				Debug.hud("trophy frogs", (graphics, deltaTracker) -> render(graphics)));
	}

	private static boolean enabled() {
		return ParzConfigDefinition.TROPHY_FROGS.isEnabled() && SkyBlockDetector.featuresActive();
	}

	/** True while the list is shown (Lotus Atoll, water fishing rod in the hand). */
	public static boolean visible() {
		return visible;
	}

	/** How many you have of this frog in this tier, or {@link #UNKNOWN}. */
	public static int count(Frog frog, Tier tier) {
		return counts[frog.ordinal()][tier.ordinal()];
	}

	/**
	 * The text without color codes and without the icon characters of Hypixel's resource pack (the catch message
	 * starts with one, before "TROPHY FROG!").
	 */
	private static String strip(String text) {
		return text.replaceAll("§.|\\p{Co}", "").trim();
	}

	// ---- catches -----------------------------------------------------------------------------

	/** The catch a chat message reports, or null if it is something else. */
	public static Catch parseCatch(String message) {
		Matcher m = CAUGHT.matcher(strip(message));
		if (!m.matches()) {
			return null;
		}
		Frog frog = Frog.byName(m.group(2));
		if (frog == null) {
			return null;
		}
		try {
			return new Catch(frog, Tier.valueOf(m.group(3)), Integer.parseInt(m.group(1).replace(",", "")));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void apply(Catch caught) {
		counts[caught.frog().ordinal()][caught.tier().ordinal()] = caught.count();
		save();
	}

	// ---- the menu of Researcher Ribery ---------------------------------------------------------

	private static void tick(Minecraft client) {
		if (++ticks % 5 != 0) {
			return;
		}
		visible = enabled() && client.player != null && holdsWaterRod(client.player.getMainHandItem()) && onLotusAtoll(client);
		Screen screen = client.gui.screen();
		if (enabled() && screen instanceof AbstractContainerScreen<?> container && MENU_TITLE.equals(strip(screen.getTitle().getString()))) {
			if (screen != menu) {
				menu = screen;
				menuReads = 0;
				menuReported = false;
			}
			readMenu(client, container);
		} else {
			menu = null;
		}
	}

	/** Takes the numbers of every frog the open menu shows. */
	private static void readMenu(Minecraft client, AbstractContainerScreen<?> container) {
		boolean[] seen = new boolean[Frog.values().length];
		int found = 0;
		int hidden = 0;
		boolean changed = false;
		List<String> contents = new ArrayList<>();
		for (Slot slot : container.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (slot.container instanceof Inventory || stack.isEmpty()) {
				continue;
			}
			String name = strip(stack.getHoverName().getString());
			List<String> lore = loreOf(stack);
			if (!name.isEmpty()) {
				contents.add(name + " (" + stack.getItem() + ") " + lore);
			}
			Frog frog = Frog.byName(name);
			int[] read = frog == null ? null : readLore(lore);
			if (read == null) {
				if (stack.is(Items.DYE.gray())) {
					hidden++; // a frog you never caught is a gray dye
				}
				continue;
			}
			seen[frog.ordinal()] = true;
			found++;
			for (int i = 0; i < read.length; i++) {
				if (read[i] != UNKNOWN && counts[frog.ordinal()][i] != read[i]) {
					counts[frog.ordinal()][i] = read[i];
					changed = true;
				}
			}
		}
		// the menu shows all twelve: what is not there by name was never caught
		boolean complete = found + hidden == Frog.values().length;
		if (complete) {
			for (Frog frog : Frog.values()) {
				if (!seen[frog.ordinal()] && Arrays.stream(counts[frog.ordinal()]).anyMatch(count -> count != 0)) {
					Arrays.fill(counts[frog.ordinal()], 0);
					changed = true;
				}
			}
		}
		menuReads++;
		if ((found > 0 || complete) && !synced) {
			synced = true;
			changed = true;
		}
		if (changed) {
			save();
		}
		// said once per opening, after the items had time to arrive
		if (!menuReported && menuReads >= 4 && !contents.isEmpty()) {
			menuReported = true;
			// kept in the log, so a menu that is read wrong can be fixed
			ParzClient.LOGGER.info("Trophy Frogs menu: {} frogs read, {} not caught yet; contents: {}", found, hidden, contents);
			if (client.player != null) {
				boolean ok = found > 0 || complete;
				client.player.sendSystemMessage(Component.literal(ok
						? "[Parz] Trophy frogs read from Researcher Ribery (" + found + " of " + Frog.values().length + " discovered)."
						: "[Parz] Could not read the Trophy Frogs menu.").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		}
	}

	/** The number of every tier from the lines of a frog item ({@link #UNKNOWN} without a line), or null without any. */
	private static int[] readLore(List<String> lore) {
		int[] read = new int[Tier.values().length];
		Arrays.fill(read, UNKNOWN);
		boolean any = false;
		for (String line : lore) {
			Matcher m = TIER_LINE.matcher(line);
			if (!m.matches()) {
				continue;
			}
			int tier = Tier.valueOf(m.group(1).toUpperCase(Locale.ROOT)).ordinal();
			if (m.group(3) != null) {
				read[tier] = Integer.parseInt(m.group(3).replace(",", ""));
			} else {
				// a check mark without a number still means you have it
				read[tier] = m.group(2).startsWith("✔") || m.group(2).startsWith("✓") ? 1 : 0;
			}
			any = true;
		}
		return any ? read : null;
	}

	private static List<String> loreOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				// only the color codes: the mark behind a tier may be an icon character
				lines.add(line.getString().replaceAll("§.", "").trim());
			}
		}
		return lines;
	}

	// ---- when it is shown ----------------------------------------------------------------------

	/** A SkyBlock fishing rod that is not a lava rod. */
	private static boolean holdsWaterRod(ItemStack stack) {
		if (!stack.is(Items.FISHING_ROD)) {
			return false;
		}
		boolean rod = false;
		for (String line : loreOf(stack)) {
			if (line.contains("Lava Rod")) {
				return false;
			}
			// the rarity line, for example "LEGENDARY FISHING ROD" (not a Grappling Hook or a weapon)
			rod |= line.contains("FISHING ROD");
		}
		return rod;
	}

	/** The area of the tab list, or the location line of the sidebar if the tab list has none. */
	private static boolean onLotusAtoll(Minecraft client) {
		if (client.getConnection() != null) {
			for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
				Component name = info.getTabListDisplayName();
				if (name == null) {
					continue;
				}
				String line = strip(name.getString());
				if (line.startsWith("Area:")) {
					return line.contains("Lotus Atoll");
				}
			}
		}
		for (String line : DungeonInfo.sidebar(client)) {
			// the location line is only a symbol and the name; a quest that mentions the atoll has more words
			String words = line.replaceAll("[^A-Za-z' ]", " ").trim().replaceAll(" +", " ");
			if (SIDEBAR_AREAS.contains(words)) {
				return true;
			}
		}
		return false;
	}

	// ---- the list ------------------------------------------------------------------------------

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!visible || !enabled() || client.gui.screen() instanceof HudEditorScreen) {
			return;
		}
		int[] position = HudTrackers.position(HudTrackers.FROG_KEY, 0, client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight());
		HudTrackers.draw(graphics, client.font, HudTrackers.FROG_KEY, position[0], position[1]);
	}

	private static int nameWidth(Font font) {
		int width = 0;
		for (Frog frog : Frog.values()) {
			width = Math.max(width, font.width(frog.title));
		}
		return width;
	}

	private static int countWidth(Font font) {
		int width = font.width("000");
		for (Frog frog : Frog.values()) {
			width = Math.max(width, font.width(total(frog)));
		}
		return width;
	}

	/** All catches of a frog as text, "?" if nothing is known. */
	private static String total(Frog frog) {
		int sum = 0;
		boolean known = false;
		for (int count : counts[frog.ordinal()]) {
			if (count != UNKNOWN) {
				sum += count;
				known = true;
			}
		}
		return known ? String.format(Locale.ROOT, "%,d", sum) : "?";
	}

	public static int width() {
		Font font = Minecraft.getInstance().font;
		int table = 4 + nameWidth(font) + 5 + 10 + 3 + countWidth(font) + 5 + Tier.values().length * 9 + 3;
		return synced ? table : Math.max(table, font.width(syncHint()) + 8);
	}

	public static int height() {
		// one line stays for the "all complete" text
		return heightOf(Math.max(1, listed().size()));
	}

	/** The height with every frog listed. */
	public static int fullHeight() {
		return heightOf(Frog.values().length);
	}

	private static int heightOf(int rows) {
		return 4 + 12 + rows * ROW + (synced ? 0 : ROW) + 2;
	}

	/** True once you have all four trophies of this frog. */
	public static boolean complete(Frog frog) {
		for (int count : counts[frog.ordinal()]) {
			if (count <= 0) {
				return false;
			}
		}
		return true;
	}

	/** The frogs in the list: all of them, or only those still missing a trophy (setting "Hide Completed Frogs"). */
	private static List<Frog> listed() {
		boolean hide = ParzConfigDefinition.TROPHY_FROGS_HIDE_COMPLETE.isEnabled();
		return Arrays.stream(Frog.values()).filter(frog -> !hide || !complete(frog)).toList();
	}

	private static String syncHint() {
		return "Talk to Researcher Ribery to sync";
	}

	/** The list with its top left corner at x, y, in normal size (without a background, the text has a shadow). */
	public static void drawUnscaled(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		graphics.text(font, Component.literal("Trophy Frog Display").withStyle(ChatFormatting.BOLD), x + 4, y + 4, 0xFFFFE040, true);
		int iconX = x + 4 + nameWidth(font) + 5;
		int countX = iconX + 13;
		int marksX = countX + countWidth(font) + 5;
		int rowY = y + 16;
		List<Frog> listed = listed();
		if (listed.isEmpty()) {
			graphics.text(font, "All trophy frogs complete!", x + 4, rowY + 1, 0xFF55FF55, true);
			rowY += ROW;
		}
		for (Frog frog : listed) {
			graphics.text(font, frog.title, x + 4, rowY + 1, frog.color, true);
			graphics.pose().pushMatrix();
			graphics.pose().translate(iconX, rowY);
			graphics.pose().scale(0.625f, 0.625f);
			graphics.item(icon(frog), 0, 0);
			graphics.pose().popMatrix();
			graphics.text(font, total(frog), countX, rowY + 1, frog.color, true);
			for (Tier tier : Tier.values()) {
				int count = count(frog, tier);
				String mark = count == UNKNOWN ? "?" : count > 0 ? "✔" : "✖";
				int color = count == UNKNOWN ? 0xFF606068 : count > 0 ? tier.color : 0xFFFF5555;
				graphics.text(font, mark, marksX + tier.ordinal() * 9, rowY + 1, color, true);
			}
			rowY += ROW;
		}
		if (!synced) {
			graphics.text(font, syncHint(), x + 4, rowY + 1, 0xFFA0A0A8, true);
		}
	}

	/** The frog of the highest tier you have, drawn with the model of the Hypixel resource pack. */
	private static ItemStack icon(Frog frog) {
		Tier best = Tier.BRONZE;
		for (Tier tier : Tier.values()) {
			if (count(frog, tier) > 0) {
				best = tier;
			}
		}
		Identifier model = Identifier.fromNamespaceAndPath("hypixel_skyblock", "item/fishing/trophy/frog/"
				+ frog.title.toLowerCase(Locale.ROOT).replace(' ', '_') + "_" + best.name().toLowerCase(Locale.ROOT));
		boolean there = ((ItemModelListAccessor) Minecraft.getInstance().getModelManager()).parz$itemModels().containsKey(model);
		return ICONS.computeIfAbsent(there ? model.toString() : "none", key -> {
			if (!there) {
				return new ItemStack(Items.FROGSPAWN); // without the resource pack of Hypixel
			}
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, model);
			return stack;
		});
	}

	// ---- the saved numbers ---------------------------------------------------------------------

	private static String account() {
		return Minecraft.getInstance().getUser().getName();
	}

	private static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!account().equals(json.get("account").getAsString())) {
				return; // the numbers of another account
			}
			JsonObject frogs = json.getAsJsonObject("frogs");
			for (Frog frog : Frog.values()) {
				if (frogs.has(frog.title)) {
					JsonArray tiers = frogs.getAsJsonArray(frog.title);
					for (int i = 0; i < Math.min(tiers.size(), Tier.values().length); i++) {
						counts[frog.ordinal()][i] = tiers.get(i).getAsInt();
					}
				}
			}
			synced = json.has("synced") && json.get("synced").getAsBoolean();
		} catch (Exception e) {
			Debug.error("trophy frog file", e);
			clear();
		}
	}

	private static void save() {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("account", account());
			json.addProperty("synced", synced);
			JsonObject frogs = new JsonObject();
			for (Frog frog : Frog.values()) {
				JsonArray tiers = new JsonArray();
				for (int count : counts[frog.ordinal()]) {
					tiers.add(count);
				}
				frogs.add(frog.title, tiers);
			}
			json.add("frogs", frogs);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Debug.error("trophy frog file", e);
		}
	}
}
