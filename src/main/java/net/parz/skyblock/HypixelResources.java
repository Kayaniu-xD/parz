package net.parz.skyblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.parz.ParzClient;
import net.parz.dashboard.DashboardData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Official SkyBlock reference data from the public Hypixel API (no API key needed):
 * every collection with its tier amounts, and the XP table of every skill.
 * Cached in config/parz-hypixel-*.json so it also works offline.
 */
public final class HypixelResources {
	private static final String COLLECTIONS_URL = "https://api.hypixel.net/v2/resources/skyblock/collections";
	private static final String SKILLS_URL = "https://api.hypixel.net/v2/resources/skyblock/skills";
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
	private static final Path COLLECTIONS_CACHE = CONFIG.resolve("parz-hypixel-collections.json");
	private static final Path SKILLS_CACHE = CONFIG.resolve("parz-hypixel-skills.json");

	public record CollectionInfo(String name, String category, int maxTier, long[] tierAmounts) {
		public long maxAmount() {
			return tierAmounts.length == 0 ? 0 : tierAmounts[tierAmounts.length - 1];
		}

		/** Amount needed for a tier (1-based), or -1. */
		public long amountForTier(int tier) {
			return tier >= 1 && tier <= tierAmounts.length ? tierAmounts[tier - 1] : -1;
		}
	}

	public record SkillInfo(String name, int maxLevel, double[] totalXp) {
		/** Total XP needed to reach a level (0 for level 0). */
		public double totalXpFor(int level) {
			if (level <= 0) return 0;
			int i = Math.min(level, totalXp.length) - 1;
			return totalXp[i];
		}

		public double maxXp() {
			return totalXp.length == 0 ? 0 : totalXp[totalXp.length - 1];
		}
	}

	/** Key: collection name in lower case. */
	private static volatile Map<String, CollectionInfo> collections = Map.of();
	/** Key: skill name in lower case. */
	private static volatile Map<String, SkillInfo> skills = Map.of();

	private HypixelResources() {
	}

	public static CollectionInfo collection(String name) {
		return collections.get(name.toLowerCase(Locale.ROOT));
	}

	public static SkillInfo skill(String name) {
		return skills.get(name.toLowerCase(Locale.ROOT));
	}

	public static boolean hasCollections() {
		return !collections.isEmpty();
	}

	/** Loads the cache right away, then refreshes from the API in the background. */
	public static void load() {
		try {
			if (Files.exists(COLLECTIONS_CACHE)) {
				parseCollections(Files.readString(COLLECTIONS_CACHE));
			}
			if (Files.exists(SKILLS_CACHE)) {
				parseSkills(Files.readString(SKILLS_CACHE));
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not read Hypixel resource cache", e);
		}

		CompletableFuture.runAsync(() -> {
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			fetch(http, COLLECTIONS_URL, COLLECTIONS_CACHE, true);
			fetch(http, SKILLS_URL, SKILLS_CACHE, false);
			Minecraft client = Minecraft.getInstance();
			if (client != null) {
				client.execute(DashboardData::applyHypixelResources);
			}
		});
	}

	private static void fetch(HttpClient http, String url, Path cache, boolean isCollections) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "Parz-Mod")
					.GET().build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				ParzClient.LOGGER.warn("Hypixel API {} returned {}", url, response.statusCode());
				return;
			}
			if (isCollections) {
				parseCollections(response.body());
			} else {
				parseSkills(response.body());
			}
			Files.writeString(cache, response.body());
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not load {} (using cache if available)", url, e);
		}
	}

	private static void parseCollections(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("collections")) {
			return;
		}
		Map<String, CollectionInfo> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> catEntry : root.getAsJsonObject("collections").entrySet()) {
			JsonObject category = catEntry.getValue().getAsJsonObject();
			String categoryName = category.has("name") ? category.get("name").getAsString() : catEntry.getKey();
			if (!category.has("items")) {
				continue;
			}
			for (Map.Entry<String, JsonElement> itemEntry : category.getAsJsonObject("items").entrySet()) {
				JsonObject item = itemEntry.getValue().getAsJsonObject();
				String name = item.get("name").getAsString();
				int maxTier = item.has("maxTiers") ? item.get("maxTiers").getAsInt() : 0;
				List<Long> amounts = new ArrayList<>();
				if (item.has("tiers")) {
					for (JsonElement tier : item.getAsJsonArray("tiers")) {
						amounts.add(tier.getAsJsonObject().get("amountRequired").getAsLong());
					}
				}
				long[] array = amounts.stream().mapToLong(Long::longValue).toArray();
				result.put(name.toLowerCase(Locale.ROOT), new CollectionInfo(name, categoryName,
						maxTier > 0 ? maxTier : array.length, array));
			}
		}
		if (!result.isEmpty()) {
			collections = result;
		}
	}

	private static void parseSkills(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("skills")) {
			return;
		}
		Map<String, SkillInfo> result = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("skills").entrySet()) {
			JsonObject skill = entry.getValue().getAsJsonObject();
			String name = skill.has("name") ? skill.get("name").getAsString() : entry.getKey();
			List<Double> xp = new ArrayList<>();
			if (skill.has("levels")) {
				for (JsonElement level : skill.getAsJsonArray("levels")) {
					xp.add(level.getAsJsonObject().get("totalExpRequired").getAsDouble());
				}
			}
			int maxLevel = skill.has("maxLevel") ? skill.get("maxLevel").getAsInt() : xp.size();
			double[] array = xp.stream().mapToDouble(Double::doubleValue).toArray();
			result.put(name.toLowerCase(Locale.ROOT), new SkillInfo(name, maxLevel, array));
		}
		if (!result.isEmpty()) {
			skills = result;
		}
	}

	/** All collections from the API (empty until loaded). */
	public static Map<String, CollectionInfo> allCollections() {
		return collections;
	}
}
