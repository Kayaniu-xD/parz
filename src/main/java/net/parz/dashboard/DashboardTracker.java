package net.parz.dashboard;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.parz.skyblock.HypixelResources;
import net.parz.skyblock.SkyBlockCatalog;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the dashboard data on Hypixel SkyBlock:
 * - Skill levels + % from the TAB LIST ("Farming 50: 45.3%")
 * - Skill XP gains from the action bar ("+12.5 Farming (1,234/5,000)") -> XP per hour
 * - Collections from the /collection menus
 */
public final class DashboardTracker {
	private static final String SKILL_NAMES = "Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Hunting";

	/** Tab list: "Farming 50: 45.3%", "Mining XXV: 12%", "Combat 60: MAX". */
	private static final Pattern TAB_SKILL = Pattern.compile("(" + SKILL_NAMES + ") ([0-9]+|[IVXLCDM]+): ([\\d.,]+%|MAX)");
	/** Action bar: "+12.5 Farming (1,234/5,000)" or "+12.5 Farming (45.2%)". */
	private static final Pattern SKILL_XP = Pattern.compile("\\+([\\d,.]+[kKmM]?) (" + SKILL_NAMES + ") \\(([^)]+)\\)");
	private static final Pattern FRACTION = Pattern.compile("([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)");
	private static final Pattern PERCENT = Pattern.compile("([\\d.,]+)%");
	private static final Pattern COLLECTION_PROGRESS = Pattern.compile("Progress to .*?: ([\\d.,]+)%");
	private static final Pattern TOTAL_COLLECTED = Pattern.compile("Total [Cc]ollected: ([\\d,.]+[kKmMbB]?)");
	private static final Pattern SKILL_ITEM = Pattern.compile("^(" + SKILL_NAMES + ")(?: ([0-9]+|[IVXLCDM]+))?$");
	private static final Pattern SKILL_PROGRESS = Pattern.compile("Progress to Level .*?: ([\\d.,]+)%");
	private static final Pattern CATEGORY_MAXED = Pattern.compile("Collections Maxed Out: (\\d+)/(\\d+)");
	private static final Pattern CATEGORY_UNLOCKED = Pattern.compile("Collections Unlocked: ([\\d.,]+)%");
	private static final Pattern ROMAN_TIER = Pattern.compile("^(.*?) ([IVXLCDM]+)$");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	/** XP/h window. */
	private static final long RATE_WINDOW_MS = 10 * 60_000L;
	/** If no XP for this long, the rate counts as paused (0). */
	private static final long RATE_PAUSE_MS = 90_000L;
	private static final Map<String, Deque<double[]>> XP_EVENTS = new HashMap<>();

	private static int tickCounter = 0;
	private static boolean dirty = false;
	private static long lastSaveMs = 0;
	private static long lastPlaytimeTick = 0;
	private static long lastPlaytimeSave = 0;
	/** "collections" / "skills" while that menu is open, otherwise null. */
	private static String openMenu = null;

	private DashboardTracker() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register(net.parz.Debug.message("dashboard chat", (message, overlay) -> {
			if (overlay && SkyBlockDetector.featuresActive()) {
				onActionBar(clean(message.getString()));
			}
		}));

		ClientTickEvents.END_CLIENT_TICK.register(net.parz.Debug.tick("dashboard tick", client -> {
			tickCounter++;
			countPlaytime(client);
			// changes are written to disk shortly after they happen (also if SkyBlock
			// detection is off), so nothing is lost on a crash or when the game closes
			if (tickCounter % 40 == 0 && System.currentTimeMillis() - lastSaveMs >= 20_000L) {
				saveIfDirty();
			}
			if (!SkyBlockDetector.featuresActive()) {
				return;
			}
			// menus are read (almost) instantly: every 2 ticks while they are open
			if (tickCounter % 2 == 0) {
				scanMenus(client);
			}
			if (tickCounter % 20 == 0) {
				readTabList(client);
			}
		}));
	}

	/** Adds the time spent in a world to the playtime that is saved in config/parz-dashboard.json. */
	private static void countPlaytime(Minecraft client) {
		long now = System.currentTimeMillis();
		if (client.player == null) {
			lastPlaytimeTick = 0;
			return;
		}
		if (lastPlaytimeTick > 0) {
			// a long freeze counts as at most 5 seconds
			DashboardData.addPlaytime(Math.min(now - lastPlaytimeTick, 5_000L));
		}
		lastPlaytimeTick = now;
		if (now - lastPlaytimeSave >= 60_000L) {
			lastPlaytimeSave = now;
			dirty = true;
		}
	}

	/** Marks the dashboard data as changed so it is written to disk soon. */
	public static void markDirty() {
		dirty = true;
	}

	/** Writes pending changes to config/parz-dashboard.json right now. */
	public static void saveIfDirty() {
		if (dirty) {
			dirty = false;
			lastSaveMs = System.currentTimeMillis();
			DashboardData.save();
		}
	}

	// ---- skills: tab list -------------------------------------------------------------

	private static void readTabList(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return;
		}
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name == null) {
				continue;
			}
			Matcher m = TAB_SKILL.matcher(clean(name.getString()));
			while (m.find()) {
				DashboardData.Skill skill = skill(m.group(1));
				skill.level = parseLevel(m.group(2));
				if ("MAX".equals(m.group(3))) {
					skill.maxed = true;
					skill.percent = 100;
				} else {
					skill.maxed = false;
					skill.percent = parseNumber(m.group(3).replace("%", ""));
				}
				skill.lastUpdate = System.currentTimeMillis();
				dirty = true;
			}
		}
	}

	// ---- skills: action bar (XP gains, XP/h) -----------------------------------------

	private static void onActionBar(String text) {
		Matcher matcher = SKILL_XP.matcher(text);
		long now = System.currentTimeMillis();
		while (matcher.find()) {
			DashboardData.Skill skill = skill(matcher.group(2));
			double gained = parseNumber(matcher.group(1));
			skill.sessionXp += gained;
			XP_EVENTS.computeIfAbsent(skill.name, k -> new ArrayDeque<>()).addLast(new double[]{now, gained});

			String inside = matcher.group(3);
			Matcher fraction = FRACTION.matcher(inside);
			if (fraction.find()) {
				skill.current = parseNumber(fraction.group(1));
				skill.needed = parseNumber(fraction.group(2));
				if (skill.needed > 0) {
					skill.percent = skill.current / skill.needed * 100.0;
				}
			} else {
				Matcher percent = PERCENT.matcher(inside);
				if (percent.find()) {
					skill.percent = parseNumber(percent.group(1));
				}
			}
			skill.lastUpdate = now;
			net.parz.Debug.log("skills", () -> "action bar: " + skill.name + " +" + gained + " xp (" + Math.round(skill.percent * 10) / 10.0 + "%)");
			dirty = true;
		}
	}

	/** Skill XP per hour (last 10 minutes), 0 when you stopped training. */
	public static double xpPerHour(String skillName) {
		Deque<double[]> events = XP_EVENTS.get(skillName);
		if (events == null || events.isEmpty()) {
			return 0;
		}
		long now = System.currentTimeMillis();
		while (!events.isEmpty() && now - events.peekFirst()[0] > RATE_WINDOW_MS) {
			events.pollFirst();
		}
		if (events.isEmpty() || now - events.peekLast()[0] > RATE_PAUSE_MS) {
			return 0;
		}
		double sum = 0;
		for (double[] e : events) {
			sum += e[1];
		}
		double span = Math.max(60_000, now - events.peekFirst()[0]);
		return sum / span * 3_600_000.0;
	}

	private static DashboardData.Skill skill(String name) {
		DashboardData.Skill skill = DashboardData.get().skills.computeIfAbsent(
				name.toLowerCase(Locale.ROOT), k -> new DashboardData.Skill());
		skill.name = name;
		return skill;
	}

	// ---- collections (/collection) -------------------------------------------------

	private static void scanMenus(Minecraft client) {
		Screen screen = client.gui.screen();
		String kind = null;
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			String title = clean(screen.getTitle().getString()).trim();
			if (title.contains("Collection")) {
				kind = "collections";
				scanCollectionMenu(containerScreen, title);
			} else if (title.contains("Skills")) {
				kind = "skills";
				scanSkillsMenu(containerScreen);
			}
		}
		// the skills / collections menu was just closed: write everything it showed to disk
		if (kind == null && openMenu != null) {
			saveIfDirty();
		}
		if (!java.util.Objects.equals(kind, openMenu)) {
			final String shown = kind;
			net.parz.Debug.log("menu", () -> shown == null ? "a menu of Parz was closed, data saved" : "reading the " + shown + " menu");
		}
		openMenu = kind;
	}

	/** /skills ("Your Skills"): level, % and XP of every skill. */
	private static void scanSkillsMenu(AbstractContainerScreen<?> containerScreen) {
		for (Slot slot : containerScreen.getMenu().slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			Matcher nameMatch = SKILL_ITEM.matcher(clean(stack.getHoverName().getString()).trim());
			if (!nameMatch.matches()) {
				continue;
			}
			String joined = String.join("\n", loreOf(stack));
			Matcher progress = SKILL_PROGRESS.matcher(joined);
			boolean hasProgress = progress.find();
			boolean maxed = joined.contains("Max Skill level") || joined.contains("MAX LEVEL") || joined.contains("Maxed");
			if (!hasProgress && !maxed) {
				continue;
			}
			DashboardData.Skill skill = skill(nameMatch.group(1));
			skill.level = nameMatch.group(2) != null ? parseLevel(nameMatch.group(2)) : 0;
			if (hasProgress) {
				skill.maxed = false;
				skill.percent = parseNumber(progress.group(1));
				Matcher fraction = FRACTION.matcher(joined.substring(progress.end()));
				if (fraction.find()) {
					skill.current = parseNumber(fraction.group(1));
					skill.needed = parseNumber(fraction.group(2));
				}
			} else {
				skill.maxed = true;
				skill.percent = 100;
			}
			skill.lastUpdate = System.currentTimeMillis();
			dirty = true;
		}
	}

	/** /collections and every collection category menu. */
	private static void scanCollectionMenu(AbstractContainerScreen<?> containerScreen, String title) {
		String category = title.replace("Collections", "").replace("Collection", "").trim();

		for (Slot slot : containerScreen.getMenu().slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			List<String> lore = loreOf(stack);
			if (lore.isEmpty()) {
				continue;
			}
			String joined = String.join("\n", lore);
			boolean locked = joined.contains("Find this item");
			Matcher total = TOTAL_COLLECTED.matcher(joined);
			boolean hasTotal = total.find();
			Matcher progress = COLLECTION_PROGRESS.matcher(joined);
			boolean hasProgress = progress.find();
			// main /collections menu: one item per category with a summary
			Matcher catMaxed = CATEGORY_MAXED.matcher(joined);
			if (catMaxed.find()) {
				String catName = clean(stack.getHoverName().getString())
						.replace("Collections", "").replace("Collection", "").trim();
				Matcher unlocked = CATEGORY_UNLOCKED.matcher(joined);
				String summary = "Maxed " + catMaxed.group(1) + "/" + catMaxed.group(2)
						+ (unlocked.find() ? "  -  Unlocked " + unlocked.group(1) + "%" : "");
				if (!catName.isEmpty() && !summary.equals(DashboardData.get().categorySummary.get(catName))) {
					DashboardData.get().categorySummary.put(catName, summary);
					dirty = true;
				}
				continue;
			}
			if (!locked && !hasTotal && !hasProgress) {
				continue; // not a collection item (buttons, glass panes, ...)
			}

			String name = clean(stack.getHoverName().getString()).trim();
			int tier = 0;
			Matcher roman = ROMAN_TIER.matcher(name);
			if (roman.matches()) {
				name = roman.group(1).trim();
				tier = romanToInt(roman.group(2));
			}
			if (name.isEmpty()) {
				continue;
			}
			String key = name.toLowerCase(Locale.ROOT);

			DashboardData.Collection c = DashboardData.get().collections.computeIfAbsent(key, k -> new DashboardData.Collection());
			c.name = name;
			HypixelResources.CollectionInfo apiInfo = HypixelResources.collection(name);
			SkyBlockCatalog.Entry known = SkyBlockCatalog.findCollection(name);
			if (apiInfo != null) {
				c.category = apiInfo.category();
			} else if (known != null) {
				c.category = known.category();
			} else if (!category.isEmpty() && (c.category == null || c.category.isEmpty())) {
				c.category = category;
			}
			if (!locked) {
				c.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				if (DashboardData.captureIcon(c, key, stack)) {
					dirty = true;
				}
			}
			c.tierNumber = tier;
			if (hasTotal) {
				c.total = parseNumber(total.group(1));
			}
			if (locked) {
				c.status = "LOCKED";
				c.percent = 0;
				c.tierNumber = 0;
			} else if (hasProgress) {
				c.status = "PROGRESS";
				c.percent = parseNumber(progress.group(1));
				Matcher fraction = FRACTION.matcher(joined.substring(progress.end()));
				if (fraction.find()) {
					c.current = parseNumber(fraction.group(1));
					c.needed = parseNumber(fraction.group(2));
				} else if (apiInfo != null && apiInfo.amountForTier(tier + 1) > 0) {
					c.current = c.total;
					c.needed = apiInfo.amountForTier(tier + 1);
				}
			} else if (apiInfo != null && tier < apiInfo.maxTier() && c.total < apiInfo.maxAmount()) {
				// no progress line but not at the last tier -> still in progress
				c.status = "PROGRESS";
				c.current = c.total;
				c.needed = apiInfo.amountForTier(tier + 1);
				c.percent = c.needed > 0 ? Math.min(100, c.total / c.needed * 100) : -1;
			} else {
				c.status = "MAXED";
				c.percent = 100;
			}
			c.lastUpdate = System.currentTimeMillis();
			dirty = true;
		}
	}

	private static List<String> loreOf(ItemStack stack) {
		List<String> lines = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				lines.add(clean(line.getString()));
			}
		}
		return lines;
	}

	// ---- helpers ----------------------------------------------------------------------

	private static String clean(String text) {
		return text.indexOf('§') < 0 ? text : FORMATTING.matcher(text).replaceAll("");
	}

	private static int parseLevel(String text) {
		if (text.chars().allMatch(Character::isDigit)) {
			try {
				return Integer.parseInt(text);
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return romanToInt(text);
	}

	public static int romanToInt(String roman) {
		int result = 0;
		int previous = 0;
		for (int i = roman.length() - 1; i >= 0; i--) {
			int value = switch (roman.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1000;
				default -> 0;
			};
			result += value < previous ? -value : value;
			previous = Math.max(previous, value);
		}
		return result;
	}

	/** Parses "1,234.5", "12.3k", "1.2M" ... */
	public static double parseNumber(String text) {
		String value = text.replace(",", "").trim();
		double multiplier = 1;
		if (!value.isEmpty()) {
			char last = Character.toLowerCase(value.charAt(value.length() - 1));
			if (last == 'k') multiplier = 1_000;
			if (last == 'm') multiplier = 1_000_000;
			if (last == 'b') multiplier = 1_000_000_000;
			if (multiplier != 1) value = value.substring(0, value.length() - 1);
		}
		try {
			return Double.parseDouble(value) * multiplier;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
