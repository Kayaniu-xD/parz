package net.parz.dashboard;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.parz.ParzClient;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the collections up to date while you play (between two /collections visits):
 * <ul>
 *   <li>Items that land in your inventory (no menu open) raise the collection,
 *       items that disappear lower it again.</li>
 *   <li>Items that go into a sack are not in the inventory. They are read from the chat message
 *       "[Sacks] +618 items. (Last 19s)": its hover text lists every item.</li>
 *   <li>Enchanted items count as the items they are made of (Enchanted Cobblestone = 160 Cobblestone).</li>
 *   <li>Losses caused by selling (Bazaar / NPC) or by menus (crafting, ...) are not subtracted.</li>
 * </ul>
 * Opening /collections always overwrites everything with the exact values of the server.
 */
public final class CollectionTracker {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern SACK_LAST = Pattern.compile("\\(Last (\\d+)s\\)");
	/** "+114 Corrupted Fragment (Nether Sack)" / "-64 Cobblestone (Mining Sack)" */
	private static final Pattern SACK_LINE = Pattern.compile("^\\s*([+\\-−])\\s*([\\d,]+)x?\\s+(.+?)(?:\\s+\\([^)]*\\))?\\s*$");

	/** "Master Mode The Catacombs - Floor VI" (header of the end-of-run summary). */
	private static final Pattern DUNGEON_HEADER = Pattern.compile("(Master Mode )?(?:The )?Catacombs - Floor ([IVX]+|\\d)\\b");
	/** "Master Mode - M7" / "The Catacombs - F7" (newer header of the end-of-run summary). */
	private static final Pattern DUNGEON_HEADER_SHORT = Pattern.compile("^(Master Mode|(?:The )?Catacombs)\\s*[-\u2013\u2014]\\s*([FM])(\\d)\\b");
	/** "Floor V completion" / "Master Mode Floor VI completed". */
	private static final Pattern DUNGEON_DONE = Pattern.compile("(Master(?: Mode)? )?(?:The Catacombs )?Floor ([IVX]+|\\d) [Cc]omplet(?:ion|ed)");
	/** Lines of the summary that only appear when the boss was defeated. */
	private static final Pattern DUNGEON_CONFIRM = Pattern.compile("^(?:Defeated .+ in \\d|Team Score:)");
	private static final long DUNGEON_CONFIRM_MS = 10_000L;
	private static final long DUNGEON_COOLDOWN_MS = 60_000L;

	private static final long TRADE_WINDOW_MS = 15_000L;
	private static final int SETTLE_TICKS = 10;
	private static final int JOIN_SETTLE_TICKS = 60;

	private record Recipe(String collectionKey, long multiplier) {
	}

	/** Names that differ between sacks / items and the collection names. */
	private static final Map<String, String> PLAIN_ALIAS = Map.of(
			"ink sack", "ink sac",
			"slime ball", "slimeball",
			"raw porkchop", "pork",
			"porkchop", "pork",
			"clay ball", "clay",
			"raw mutton", "mutton",
			"raw rabbit", "rabbit");

	/** "Enchanted <name>": name of the base item as used by the collections. */
	private static final Map<String, String> ENCHANTED_ALIAS = Map.of(
			"iron", "iron ingot",
			"gold", "gold ingot",
			"lapis", "lapis lazuli",
			"quartz", "nether quartz",
			"glowstone", "glowstone dust",
			"sugar", "sugar cane",
			"cocoa", "cocoa beans");

	/** Enchanted items that are made from other enchanted items or that need a different amount. */
	private static final Map<String, Recipe> SPECIAL = Map.of(
			"sugar cane", new Recipe("sugar cane", 25_600),
			"baked potato", new Recipe("potato", 25_600),
			"grilled pork", new Recipe("pork", 25_600),
			"packed ice", new Recipe("ice", 25_600),
			"hay bale", new Recipe("wheat", 1_296),
			"bread", new Recipe("wheat", 60));

	private static Map<String, Integer> baseline = null;
	private static Object lastLevel = null;
	private static int settleTicks = JOIN_SETTLE_TICKS;
	private static long lastMenuMs = 0;
	private static long lastSaleMs = 0;
	private static long lastBuyMs = 0;
	private static int pendingFloor = 0;
	private static boolean pendingMaster = false;
	private static long pendingMs = 0;
	private static int lastRunFloor = 0;
	private static boolean lastRunMaster = false;
	private static long lastRunMs = 0;

	private CollectionTracker() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register(net.parz.Debug.message("collections chat", (message, overlay) -> {
			if (overlay || !enabled()) {
				return;
			}
			String text = clean(message.getString()).trim();
			detectTrade(text);
			detectDungeon(text);
			if (text.startsWith("[Sacks]")) {
				onSackMessage(message, text);
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(net.parz.Debug.tick("collections tick", CollectionTracker::onTick));
	}

	private static boolean enabled() {
		return SkyBlockDetector.featuresActive() && ParzConfigDefinition.LIVE_COLLECTIONS.isEnabled();
	}

	private static boolean subtractEnabled() {
		return ParzConfigDefinition.SUBTRACT_COLLECTIONS.isEnabled();
	}

	// ---- inventory ---------------------------------------------------------------------

	private static void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			baseline = null;
			return;
		}
		if (client.level != lastLevel) {
			lastLevel = client.level;
			baseline = null;
			settleTicks = JOIN_SETTLE_TICKS;
		}

		boolean menuOpen = client.gui.screen() instanceof AbstractContainerScreen<?>;
		if (menuOpen) {
			lastMenuMs = System.currentTimeMillis();
			settleTicks = SETTLE_TICKS; // items that come back when a menu closes are not new
		}

		Map<String, Integer> current = countInventory(client.player.getInventory());
		if (baseline == null || settleTicks > 0 || !enabled()) {
			baseline = current;
			if (settleTicks > 0 && !menuOpen) {
				settleTicks--;
			}
			return;
		}

		Set<String> names = new HashSet<>(current.keySet());
		names.addAll(baseline.keySet());
		boolean sold = System.currentTimeMillis() - lastSaleMs <= TRADE_WINDOW_MS;
		boolean bought = System.currentTimeMillis() - lastBuyMs <= TRADE_WINDOW_MS;
		for (String name : names) {
			int delta = current.getOrDefault(name, 0) - baseline.getOrDefault(name, 0);
			if (delta > 0 && !bought) {
				apply(name, delta);
			} else if (delta < 0 && subtractEnabled() && !sold) {
				apply(name, delta);
			} else if (delta != 0) {
				net.parz.Debug.log("collection", () -> "inventory change of " + name + " (" + delta + ") ignored: "
						+ (delta > 0 ? "just bought" : subtractEnabled() ? "just sold" : "subtracting is off"));
			}
		}
		baseline = current;
	}

	private static Map<String, Integer> countInventory(Inventory inventory) {
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String name = clean(stack.getHoverName().getString()).trim().toLowerCase(Locale.ROOT);
			if (resolve(name) != null) {
				counts.merge(name, stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	// ---- sacks (chat) ------------------------------------------------------------------

	private static void onSackMessage(Component message, String text) {
		String hover = findHoverText(message);
		if (hover == null) {
			return;
		}
		long windowMs = TRADE_WINDOW_MS;
		Matcher last = SACK_LAST.matcher(text);
		if (last.find()) {
			windowMs = Integer.parseInt(last.group(1)) * 1000L + 3_000L;
		}
		long now = System.currentTimeMillis();
		boolean bought = now - lastBuyMs <= windowMs;
		boolean sold = now - lastSaleMs <= windowMs;
		boolean menuRecent = now - lastMenuMs <= windowMs;

		for (String line : hover.split("\\R")) {
			Matcher m = SACK_LINE.matcher(clean(line));
			if (!m.matches()) {
				continue;
			}
			long amount;
			try {
				amount = Long.parseLong(m.group(2).replace(",", ""));
			} catch (NumberFormatException e) {
				continue;
			}
			String name = m.group(3).trim().toLowerCase(Locale.ROOT);
			if ("+".equals(m.group(1))) {
				if (!bought) {
					apply(name, amount);
				} else {
					net.parz.Debug.log("sacks", () -> "+" + amount + " " + name + " ignored (just bought)");
				}
			} else if (subtractEnabled() && !sold && !menuRecent) {
				apply(name, -amount);
			} else {
				net.parz.Debug.log("sacks", () -> "-" + amount + " " + name + " ignored (sold, menu open or subtracting off)");
			}
		}
	}

	/** The text of the first hover event that lists the sack items. */
	private static String findHoverText(Component message) {
		String[] found = new String[1];
		message.visit((style, content) -> {
			if (found[0] == null) {
				found[0] = hoverOf(style);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	private static String hoverOf(Style style) {
		if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
			String text = value.getString();
			if (text.contains("items:")) {
				return text;
			}
		}
		return null;
	}

	// ---- dungeon floors ----------------------------------------------------------------

	/**
	 * Counts finished Catacombs runs of floor 1-7: a normal run adds 1, a Master Mode run adds 2.
	 * The end-of-run summary "The Catacombs - Floor V" only counts when "Defeated ... in ..." or
	 * "Team Score:" follows right after it.
	 */
	private static void detectDungeon(String text) {
		long now = System.currentTimeMillis();

		Matcher done = DUNGEON_DONE.matcher(text);
		if (done.find()) {
			net.parz.Debug.log("dungeon", () -> "completion line recognised: " + text);
			completeFloor(parseFloor(done.group(2)), done.group(1) != null);
			return;
		}
		Matcher shortHeader = DUNGEON_HEADER_SHORT.matcher(text);
		if (shortHeader.find()) {
			net.parz.Debug.log("dungeon", () -> "run header recognised: " + text + " (waiting for \"Defeated ... in ...\")");
			pendingFloor = parseFloor(shortHeader.group(3));
			pendingMaster = shortHeader.group(1).startsWith("Master") || shortHeader.group(2).equals("M");
			pendingMs = now;
			return;
		}
		Matcher header = DUNGEON_HEADER.matcher(text);
		if (header.find()) {
			net.parz.Debug.log("dungeon", () -> "run header recognised: " + text + " (waiting for \"Defeated ... in ...\")");
			pendingFloor = parseFloor(header.group(2));
			pendingMaster = header.group(1) != null;
			pendingMs = now;
			return;
		}
		if (pendingFloor > 0 && now - pendingMs <= DUNGEON_CONFIRM_MS && DUNGEON_CONFIRM.matcher(text).find()) {
			net.parz.Debug.log("dungeon", () -> "confirmation line recognised: " + text);
			completeFloor(pendingFloor, pendingMaster);
			pendingFloor = 0;
		}
	}

	private static int parseFloor(String text) {
		int floor = text.chars().allMatch(Character::isDigit) ? Integer.parseInt(text) : DashboardTracker.romanToInt(text);
		return floor >= 1 && floor <= 7 ? floor : 0;
	}

	private static void completeFloor(int floor, boolean master) {
		long now = System.currentTimeMillis();
		if (floor == 0) {
			return;
		}
		// the same run can be announced by several lines
		if (floor == lastRunFloor && master == lastRunMaster && now - lastRunMs < DUNGEON_COOLDOWN_MS) {
			net.parz.Debug.log("dungeon", () -> "run already counted, skipped");
			return;
		}
		lastRunFloor = floor;
		lastRunMaster = master;
		lastRunMs = now;
		// Floor 1-7 = Bonzo ... Necron: the run counts in that boss collection (normal +1, Master Mode +2)
		String boss = DashboardData.FLOOR_BOSSES.get(floor - 1);
		int kills = master ? 2 : 1;
		DashboardData.Collection c = DashboardData.get().collections.get(boss.toLowerCase(Locale.ROOT));
		// never opened in /collections yet: the real kills are unknown, the next menu visit fixes that
		if (c != null && !"UNKNOWN".equals(c.status)) {
			c.addKills(kills);
			c.lastUpdate = now;
			DashboardTracker.markDirty();
			net.parz.Debug.log("dungeon", () -> "run counted: " + boss + " +" + kills + " (now " + c.total + ")");
		} else {
			net.parz.Debug.log("dungeon", () -> "run NOT counted: the " + boss + " collection is "
					+ (c == null ? "not known yet" : "UNKNOWN") + " - open it once in /collections");
		}
		ParzClient.LOGGER.info("Dungeon run finished: {}Floor {} -> {} +{}", master ? "Master Mode " : "", floor, boss, kills);
	}

	// ---- sales / purchases -------------------------------------------------------------

	/** Remembers Bazaar / NPC trades: sold items are not "lost", bought ones are not "collected". */
	private static void detectTrade(String text) {
		long now = System.currentTimeMillis();
		if (text.startsWith("[Bazaar]")) {
			if (text.contains("Sold") || text.contains("Sell Offer") || text.contains("from selling")
					|| text.contains("Instant Sell") || text.contains("Sell Order")) {
				lastSaleMs = now;
			net.parz.Debug.log("trade", () -> "sale recognised: " + text);
			} else if (text.contains("Bought") || text.contains("Claimed") || text.contains("Buy Order")
					|| text.contains("Instant Buy")) {
				lastBuyMs = now;
			net.parz.Debug.log("trade", () -> "purchase recognised: " + text);
			}
		} else if (text.startsWith("You sold ")) {
			lastSaleMs = now;
			net.parz.Debug.log("trade", () -> "sale recognised: " + text);
		} else if (text.startsWith("You bought ") || text.startsWith("You purchased ")) {
			lastBuyMs = now;
			net.parz.Debug.log("trade", () -> "purchase recognised: " + text);
		}
	}

	// ---- applying the change -----------------------------------------------------------

	/** Changes the collection of an item (enchanted items count as what they are made of). */
	private static void apply(String itemName, long amount) {
		Recipe recipe = resolve(itemName);
		if (recipe == null) {
			return;
		}
		DashboardData.Collection c = DashboardData.get().collections.get(recipe.collectionKey());
		// never opened in /collections yet: the real total is unknown, the next menu visit fixes that
		if (c == null || "UNKNOWN".equals(c.status)) {
			net.parz.Debug.log("collection", () -> "no data for the " + recipe.collectionKey() + " collection yet (open /collections), "
					+ amount + " " + itemName + " not counted");
			return;
		}
		double newTotal = Math.max(0, c.total + amount * recipe.multiplier());
		if (newTotal == c.total) {
			return;
		}
		c.setTotal(newTotal);
		c.lastUpdate = System.currentTimeMillis();
		DashboardTracker.markDirty();
		net.parz.Debug.log("collection", () -> c.name + " " + (amount >= 0 ? "+" : "") + amount * recipe.multiplier() + " -> " + c.total
				+ " (" + itemName + ")");
		ParzClient.LOGGER.debug("Collection {} {}{} -> {}", c.name, amount >= 0 ? "+" : "", amount * recipe.multiplier(), c.total);
	}

	/** Which collection an item belongs to and how much one item is worth. Null if it is none. */
	private static Recipe resolve(String rawName) {
		String name = rawName.toLowerCase(Locale.ROOT).trim();
		Map<String, DashboardData.Collection> collections = DashboardData.get().collections;
		String plain = PLAIN_ALIAS.getOrDefault(name, name);
		if (collections.containsKey(plain)) {
			return new Recipe(plain, 1);
		}
		if (!name.startsWith("enchanted ")) {
			return null;
		}

		String base = name.substring("enchanted ".length());
		long multiplier = 160;
		if (base.startsWith("block of ")) {
			base = base.substring("block of ".length());
			multiplier = 25_600;
		} else if (base.endsWith(" block")) {
			base = base.substring(0, base.length() - " block".length());
			multiplier = 25_600;
		}

		Recipe special = SPECIAL.get(base);
		if (special != null && multiplier == 160 && collections.containsKey(special.collectionKey())) {
			return special;
		}
		base = ENCHANTED_ALIAS.getOrDefault(base, PLAIN_ALIAS.getOrDefault(base, base));
		return collections.containsKey(base) ? new Recipe(base, multiplier) : null;
	}

	private static String clean(String text) {
		return FORMATTING.matcher(text).replaceAll("");
	}
}
