package net.parz.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.parz.ParzClient;
import net.parz.skyblock.HypixelResources;
import net.parz.skyblock.SkyBlockCatalog;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the dashboard and the HUD show. Saved in config/parz-dashboard.json.
 */
public final class DashboardData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-dashboard.json");

	private static DashboardData instance = new DashboardData();

	/** Session start (not saved). */
	public static final long SESSION_START = System.currentTimeMillis();

	/** Real items captured from /collection this session (heads keep their texture). */
	public static final Map<String, ItemStack> CAPTURED_ITEMS = new HashMap<>();
	private static final Set<String> ICON_DECODE_FAILED = new HashSet<>();

	// ---- saved data -------------------------------------------------------------
	public Map<String, Skill> skills = new LinkedHashMap<>();
	public Map<String, Collection> collections = new LinkedHashMap<>();
	/** Total time played with Minecraft in a world, in milliseconds (all sessions, kept between restarts). */
	public long playtimeMs = 0;
	/** Pinned HUD trackers: "skill:farming" or "col:wheat". */
	public List<String> hudPinned = new ArrayList<>();
	/** HUD positions per tracker key: [x, y]. */
	public Map<String, int[]> hudPositions = new HashMap<>();
	/** HUD size per tracker key (1.0 = normal), changed with the mouse wheel in /parz hud. */
	public Map<String, Float> hudScales = new HashMap<>();
	/** Summary per category from the main /collections menu, e.g. "Maxed 12/17 - Unlocked 100%". */
	public Map<String, String> categorySummary = new HashMap<>();
	/** Minecraft name the skills and collections belong to. */
	public String username = "";
	/** Pets seen in the /pets menu, per page ("1", "2", ...). A page is replaced when it is scanned again. */
	public Map<String, List<OwnedPet>> petPages = new LinkedHashMap<>();
	/** Number of pages of the pets menu when it was last scanned. */
	public int petPageCount = 0;

	/** A pet of the player: name without "Pet", rarity 0 (common) - 5 (mythic), level and its saved item icon. */
	public static final class OwnedPet {
		public String name = "";
		public int rarity = 0;
		public int level = 1;
		public String iconData = "";
	}

	public static final class Skill {
		public String name = "";
		/** Level from the tab list, -1 if unknown. */
		public int level = -1;
		public boolean maxed = false;
		public double current = 0;
		public double needed = 0;
		/** Percentage to the next level (0-100), -1 if unknown. */
		public double percent = -1;
		public long lastUpdate = 0;
		/** XP gained in this game session (not saved). */
		public transient double sessionXp = 0;

		public float progress() {
			if (maxed) return 1f;
			if (percent >= 0) return (float) Math.min(1.0, percent / 100.0);
			if (needed > 0) return (float) Math.min(1.0, current / needed);
			return 0f;
		}

		public boolean known() {
			return level >= 0 || percent >= 0 || needed > 0;
		}

		/** Max level from the Hypixel API (or -1). */
		public int maxLevel() {
			HypixelResources.SkillInfo info = HypixelResources.skill(name);
			return info != null ? info.maxLevel() : -1;
		}

		/** Estimated total XP in this skill (-1 if unknown). */
		public double totalXp() {
			HypixelResources.SkillInfo info = HypixelResources.skill(name);
			if (info == null || level < 0) return -1;
			double base = info.totalXpFor(level);
			if (maxed || level >= info.maxLevel()) return base;
			double inLevel;
			if (needed > 0) {
				inLevel = current;
			} else {
				double span = info.totalXpFor(level + 1) - base;
				inLevel = percent >= 0 ? span * percent / 100.0 : 0;
			}
			return base + inLevel;
		}

		/** XP still needed to reach the max level (-1 if unknown). */
		public double xpToMax() {
			HypixelResources.SkillInfo info = HypixelResources.skill(name);
			double total = totalXp();
			if (info == null || total < 0) return -1;
			if (maxed || level >= info.maxLevel()) return 0;
			return Math.max(0, info.maxXp() - total);
		}
	}

	public static final class Collection {
		public String name = "";
		public String category = "";
		public String itemId = "";
		/** The real item (e.g. a player head with its texture) as JSON, so the icon survives a restart. */
		public String iconData = "";
		/** Tier as number (0 = none/locked). */
		public int tierNumber = 0;
		public double total = 0;
		public double current = 0;
		public double needed = 0;
		/** Percentage to the next tier, -1 if unknown. */
		public double percent = -1;
		/** "UNKNOWN", "LOCKED", "PROGRESS" or "MAXED". */
		public String status = "UNKNOWN";
		public long lastUpdate = 0;

		public float progress() {
			if ("MAXED".equals(status)) return 1f;
			if (percent >= 0) return (float) Math.min(1.0, percent / 100.0);
			return 0f;
		}

		/**
		 * Sets the collected amount and recalculates tier, progress and status from the
		 * tier table of the Hypixel API (used when items are gained or lost between menu visits).
		 */
		public void setTotal(double newTotal) {
			total = Math.max(0, newTotal);
			HypixelResources.CollectionInfo info = HypixelResources.collection(name);
			if (info != null && info.tierAmounts().length > 0 && info.maxTier() > 0) {
				int tier = 0;
				for (int t = 1; t <= info.tierAmounts().length; t++) {
					if (total >= info.amountForTier(t)) {
						tier = t;
					}
				}
				tierNumber = tier;
				if (tier >= info.maxTier()) {
					status = "MAXED";
					percent = 100;
					current = total;
					needed = 0;
				} else {
					if (!("LOCKED".equals(status) && total <= 0)) {
						status = "PROGRESS";
					}
					needed = info.amountForTier(tier + 1);
					current = total;
					percent = needed > 0 ? Math.min(100, total / needed * 100) : -1;
				}
			} else {
				// no tier table: only move the numbers we know
				if ("LOCKED".equals(status) && total > 0) {
					status = "PROGRESS";
				}
				if ("PROGRESS".equals(status) && needed > 0) {
					current = total;
					percent = Math.min(100, total / needed * 100);
				}
			}
		}

		/**
		 * Boss collections (Bonzo ... Kuudra) count kills and have no tier table: the kill count is
		 * kept in current. Adds kills and updates the progress to the next tier. The next visit of
		 * the Boss menu in /collections sets the exact values (tier included) again.
		 */
		public void addKills(double amount) {
			current = Math.max(0, current + amount);
			total = current;
			if (needed > 0) {
				percent = Math.min(100, current / needed * 100);
			}
		}

		/** Amount needed for the last tier (-1 if unknown). */
		public long maxAmount() {
			HypixelResources.CollectionInfo info = HypixelResources.collection(name);
			return info != null && info.maxAmount() > 0 ? info.maxAmount() : -1;
		}

		public int maxTier() {
			HypixelResources.CollectionInfo info = HypixelResources.collection(name);
			return info != null ? info.maxTier() : -1;
		}

		/** How many items are still missing to max this collection (-1 if unknown). */
		public double neededToMax() {
			long max = maxAmount();
			if (max < 0 || "UNKNOWN".equals(status)) return -1;
			if ("MAXED".equals(status)) return 0;
			return Math.max(0, max - total);
		}

		/** Progress towards the maxed collection (0..1). */
		public float maxProgress() {
			long max = maxAmount();
			if ("MAXED".equals(status)) return 1f;
			if (max <= 0) return 0f;
			return (float) Math.min(1.0, total / max);
		}
	}

	// ---- access -----------------------------------------------------------------

	public static DashboardData get() {
		return instance;
	}

	public static void load() {
		DashboardData loaded = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				loaded = GSON.fromJson(reader, DashboardData.class);
			} catch (Exception e) {
				ParzClient.LOGGER.error("Could not read {}", FILE, e);
			}
		}
		instance = loaded != null ? loaded : new DashboardData();
		if (instance.skills == null) instance.skills = new LinkedHashMap<>();
		if (instance.collections == null) instance.collections = new LinkedHashMap<>();
		if (instance.hudPinned == null) instance.hudPinned = new ArrayList<>();
		if (instance.hudPositions == null) instance.hudPositions = new HashMap<>();
		if (instance.hudScales == null) instance.hudScales = new HashMap<>();
		if (instance.categorySummary == null) instance.categorySummary = new HashMap<>();
		if (instance.petPages == null) instance.petPages = new LinkedHashMap<>();
		instance.fillCatalog();
		applyHypixelResources();
	}

	/**
	 * Remembers which Minecraft account the skills and collections belong to. If a different
	 * account is playing now, the old skills and collections are deleted and start fresh
	 * (the HUD layout is kept).
	 */
	public static void ensureAccount(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		DashboardData data = instance;
		boolean otherAccount = data.username != null && !data.username.isEmpty() && !data.username.equalsIgnoreCase(name);
		if (otherAccount) {
			ParzClient.LOGGER.info("Account changed ({} -> {}): removing the old skills and collections", data.username, name);
			data.skills.clear();
			data.collections.clear();
			data.categorySummary.clear();
			data.petPages.clear();
			data.petPageCount = 0;
			CAPTURED_ITEMS.clear();
			ICON_DECODE_FAILED.clear();
			data.fillCatalog();
			applyHypixelResources();
		}
		if (otherAccount || !name.equals(data.username)) {
			data.username = name;
			save();
		}
	}

	/** Makes sure every known skill and collection exists (so the grid shows all of them). */
	private void fillCatalog() {
		// older versions used other keys -> normalise to lower case
		Map<String, Skill> normalised = new LinkedHashMap<>();
		skills.forEach((k, v) -> normalised.putIfAbsent(k.toLowerCase(Locale.ROOT), v));
		skills = normalised;
		for (SkyBlockCatalog.Entry e : SkyBlockCatalog.SKILLS) {
			Skill skill = skills.computeIfAbsent(e.key(), k -> new Skill());
			skill.name = e.name();
		}
		// the old "Dungeons" floor counters (Catacombs Floor I-VII) are gone: floor runs count in Boss now
		collections.values().removeIf(c -> "Dungeons".equals(c.category));
		for (List<SkyBlockCatalog.Entry> list : SkyBlockCatalog.COLLECTIONS.values()) {
			for (SkyBlockCatalog.Entry e : list) {
				Collection c = collections.computeIfAbsent(e.key(), k -> new Collection());
				c.name = e.name();
				c.category = e.category();
				if (c.itemId == null || c.itemId.isEmpty()) {
					c.itemId = e.itemId();
				}
			}
		}
	}

	// ---- boss collections ---------------------------------------------------------

	public static final String BOSS_CATEGORY = "Boss";
	/** Catacombs floor 1-7 = these bosses (Floor / Master Mode Floor 1 is Bonzo, ... 7 is Necron). */
	public static final List<String> FLOOR_BOSSES = List.of("Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron");
	/** Display order in the Boss tab: the floor bosses first, Kuudra at the end. */
	public static final List<String> BOSS_ORDER = List.of("Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron", "Kuudra");

	/** Position of a boss in the Boss tab (unknown bosses come after Kuudra). */
	public static int bossIndex(String name) {
		int index = BOSS_ORDER.indexOf(name);
		return index >= 0 ? index : BOSS_ORDER.size();
	}

	/**
	 * Adds every collection from the Hypixel API (exact names + categories) and removes
	 * placeholder entries that do not exist on Hypixel.
	 */
	public static void applyHypixelResources() {
		if (!HypixelResources.hasCollections()) {
			return;
		}
		DashboardData data = instance;
		Map<String, HypixelResources.CollectionInfo> api = HypixelResources.allCollections();
		for (Map.Entry<String, HypixelResources.CollectionInfo> e : api.entrySet()) {
			Collection c = data.collections.computeIfAbsent(e.getKey(), k -> new Collection());
			c.name = e.getValue().name();
			c.category = e.getValue().category();
			if (c.itemId == null || c.itemId.isEmpty()) {
				SkyBlockCatalog.Entry known = SkyBlockCatalog.findCollection(c.name);
				c.itemId = known != null ? known.itemId() : "paper";
			}
		}
		data.collections.entrySet().removeIf(e -> !api.containsKey(e.getKey()) && "UNKNOWN".equals(e.getValue().status));
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			ParzClient.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	// ---- items --------------------------------------------------------------------

	public static ItemStack iconFor(Collection c) {
		String key = c.name.toLowerCase(Locale.ROOT);
		ItemStack captured = CAPTURED_ITEMS.get(key);
		if (captured == null && c.iconData != null && !c.iconData.isEmpty() && !ICON_DECODE_FAILED.contains(key)) {
			captured = decodeIcon(c.iconData);
			if (captured != null) {
				CAPTURED_ITEMS.put(key, captured);
			} else {
				ICON_DECODE_FAILED.add(key);
			}
		}
		return captured != null ? captured : SkyBlockCatalog.stack(c.itemId);
	}

	/**
	 * Remembers the real item of a collection (kept in memory and saved to disk).
	 * Returns true if the saved icon changed.
	 */
	public static boolean captureIcon(Collection c, String key, ItemStack stack) {
		ItemStack icon = stack.copy();
		icon.remove(DataComponents.LORE); // lore is huge and not needed for the icon
		ItemStack old = CAPTURED_ITEMS.put(key, icon);
		boolean hasSaved = c.iconData != null && !c.iconData.isEmpty();
		if (old != null && hasSaved && ItemStack.isSameItemSameComponents(old, icon)) {
			return false;
		}
		String encoded = encodeIcon(icon);
		if (encoded.isEmpty() || encoded.equals(c.iconData)) {
			return false;
		}
		c.iconData = encoded;
		ICON_DECODE_FAILED.remove(key);
		return true;
	}

	public static String encodeIcon(ItemStack stack) {
		try {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection == null) {
				return "";
			}
			RegistryOps<JsonElement> ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			return ItemStack.CODEC.encodeStart(ops, stack).result().map(JsonElement::toString).orElse("");
		} catch (Exception e) {
			return "";
		}
	}

	public static ItemStack decodeIcon(String data) {
		try {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection == null) {
				return null;
			}
			RegistryOps<JsonElement> ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			return ItemStack.CODEC.parse(ops, JsonParser.parseString(data)).result()
					.filter(stack -> !stack.isEmpty()).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	public static ItemStack iconFor(Skill s) {
		return SkyBlockCatalog.stack(SkyBlockCatalog.skillItem(s.name));
	}

	// ---- HUD pins -----------------------------------------------------------------

	public static String key(Skill s) {
		return "skill:" + s.name.toLowerCase(Locale.ROOT);
	}

	public static String key(Collection c) {
		return "col:" + c.name.toLowerCase(Locale.ROOT);
	}

	public boolean isPinned(String key) {
		return hudPinned.contains(key);
	}

	public void togglePin(String key) {
		if (!hudPinned.remove(key)) {
			hudPinned.add(key);
		}
		save();
	}

	public Skill skillByKey(String key) {
		return key.startsWith("skill:") ? skills.get(key.substring(6)) : null;
	}

	public Collection collectionByKey(String key) {
		return key.startsWith("col:") ? collections.get(key.substring(4)) : null;
	}

	// ---- playtime -----------------------------------------------------------------

	/** Playtime of all sessions including the one that is running now (not yet added). */
	public long totalPlaytimeMs() {
		return playtimeMs;
	}

	/** Playtime of the running game session (in a world), not saved. */
	private static long sessionPlaytimeMs = 0;

	public static long sessionPlaytimeMs() {
		return sessionPlaytimeMs;
	}

	public static void addPlaytime(long ms) {
		ms = Math.max(0, ms);
		instance.playtimeMs += ms;
		sessionPlaytimeMs += ms;
	}

	public List<Collection> collectionsIn(String category) {
		List<Collection> list = new ArrayList<>();
		for (Collection c : collections.values()) {
			if (c.category.equalsIgnoreCase(category)) list.add(c);
		}
		return list;
	}

	/** Categories that exist (catalog order first, then extra ones like Rift). */
	public List<String> categories() {
		List<String> list = new ArrayList<>(SkyBlockCatalog.COLLECTION_CATEGORIES);
		for (Collection c : collections.values()) {
			if (!c.category.isEmpty() && list.stream().noneMatch(x -> x.equalsIgnoreCase(c.category))) {
				list.add(c.category);
			}
		}
		return list;
	}
}
