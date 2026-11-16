package net.parz.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

	// ---- saved data -------------------------------------------------------------
	public List<Goal> goals = new ArrayList<>();
	public Map<String, Skill> skills = new LinkedHashMap<>();
	public Map<String, Collection> collections = new LinkedHashMap<>();
	public int goalsCompletedTotal = 0;
	/** Pinned HUD trackers: "skill:farming" or "col:wheat". */
	public List<String> hudPinned = new ArrayList<>();
	/** HUD positions per tracker key: [x, y]. */
	public Map<String, int[]> hudPositions = new HashMap<>();
	/** Summary per category from the main /collections menu, e.g. "Maxed 12/17 - Unlocked 100%". */
	public Map<String, String> categorySummary = new HashMap<>();

	public static final class Goal {
		public String name = "";
		public long target = 0;
		public long current = 0;
		public boolean done = false;
		public int reminderMinutes = 0;
		public long lastReminder = 0;
		public long created = System.currentTimeMillis();

		public float progress() {
			if (done) return 1f;
			if (target <= 0) return 0f;
			return Math.min(1f, (float) current / (float) target);
		}
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
		if (instance.goals == null) instance.goals = new ArrayList<>();
		if (instance.skills == null) instance.skills = new LinkedHashMap<>();
		if (instance.collections == null) instance.collections = new LinkedHashMap<>();
		if (instance.hudPinned == null) instance.hudPinned = new ArrayList<>();
		if (instance.hudPositions == null) instance.hudPositions = new HashMap<>();
		if (instance.categorySummary == null) instance.categorySummary = new HashMap<>();
		instance.fillCatalog();
		applyHypixelResources();
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
		ItemStack captured = CAPTURED_ITEMS.get(c.name.toLowerCase(Locale.ROOT));
		return captured != null ? captured : SkyBlockCatalog.stack(c.itemId);
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

	// ---- goal helpers -------------------------------------------------------------

	public void addGoal(String name, long target, int reminderMinutes) {
		Goal goal = new Goal();
		goal.name = name;
		goal.target = Math.max(0, target);
		goal.reminderMinutes = Math.max(0, reminderMinutes);
		goal.lastReminder = System.currentTimeMillis();
		goals.add(goal);
		save();
	}

	public void setDone(Goal goal, boolean done) {
		if (goal.done == done) return;
		goal.done = done;
		if (done) {
			goalsCompletedTotal++;
			if (goal.target > 0) goal.current = Math.max(goal.current, goal.target);
		}
		save();
	}

	public void changeProgress(Goal goal, long amount) {
		goal.current = Math.max(0, goal.current + amount);
		if (goal.target > 0 && goal.current >= goal.target && !goal.done) {
			setDone(goal, true);
		} else {
			if (goal.target > 0 && goal.current < goal.target && goal.done) goal.done = false;
			save();
		}
	}

	public void removeGoal(Goal goal) {
		goals.remove(goal);
		save();
	}

	public long openGoals() {
		return goals.stream().filter(g -> !g.done).count();
	}

	public long doneGoals() {
		return goals.stream().filter(g -> g.done).count();
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
