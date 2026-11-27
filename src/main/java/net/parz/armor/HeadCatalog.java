package net.parz.armor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every SkyBlock item that is a head (all the "skull" items: armor heads, pets' items, minions, ...) and every skin.
 * The items come from the public Hypixel items API (no key needed) and are kept in config/parz-heads.json, so the menu
 * opens at once and works offline; they are fetched again when the saved list is older than a few days. The skins, which
 * that API does not list, are part of Parz ({@link SkinList}).
 */
public final class HeadCatalog {
	/** One head: the SkyBlock item id, its name, the texture hash and the rarity (may be empty). */
	public record Head(String id, String name, String hash, String tier) {
		/** Lower-case text the search looks in. */
		public String searchText() {
			return (name + " " + id.replace('_', ' ') + (SkinList.isAnimated(hash) ? " animated" : "")).toLowerCase(Locale.ROOT);
		}

		/** True if every word of the (lower-case) search is found: "celest goldor" finds "Celestial Goldor's ...". */
		public boolean matches(String query) {
			return containsWords(searchText(), query);
		}
	}

	/** True if every word of the query is somewhere in the text (an empty query always is). */
	public static boolean containsWords(String text, String query) {
		for (String word : query.split("\\s+")) {
			if (!text.contains(word)) {
				return false;
			}
		}
		return true;
	}

	private static final String API = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-heads.json");
	private static final long MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;
	private static final String TEXTURE_PREFIX = "textures.minecraft.net/texture/";

	/** The skins and the heads of the API. */
	private static volatile List<Head> heads = List.of();
	/** Only the heads of the API. */
	private static volatile List<Head> official = List.of();
	private static volatile String status = "Loading the heads...";
	private static boolean started = false;

	private HeadCatalog() {
	}

	/** All heads, sorted by name; empty until they are loaded. */
	public static List<Head> heads() {
		return heads;
	}

	/** A short text while the list is empty ("Loading ...", or why it failed); empty text once there are heads. */
	public static String status() {
		return heads.isEmpty() ? status : "";
	}

	/** The head with this texture, or null. */
	public static Head byHash(String hash) {
		for (Head head : heads) {
			if (head.hash().equals(hash)) {
				return head;
			}
		}
		return null;
	}

	/** Starts loading the list (once per game start); returns at once. */
	public static synchronized void ensureLoaded() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(HeadCatalog::load, "parz-heads");
		thread.setDaemon(true);
		thread.start();
	}

	/** Puts the heads of the API together with the skins; a skin of the same item takes its place. */
	private static void use(List<Head> fromApi) {
		official = fromApi;
		List<Head> all = new ArrayList<>(SkinList.skins());
		Set<String> skins = new HashSet<>();
		all.forEach(skin -> skins.add(skin.id()));
		for (Head head : fromApi) {
			if (!skins.contains(head.id())) {
				all.add(head);
			}
		}
		all.sort(Comparator.comparing((Head head) -> head.name().toLowerCase(Locale.ROOT)).thenComparing(Head::id));
		heads = all;
	}

	private static void load() {
		use(official); // the skins are there at once
		long fetched = 0;
		try {
			if (Files.exists(FILE)) {
				JsonObject saved = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
				List<Head> list = new ArrayList<>();
				for (JsonElement element : saved.getAsJsonArray("heads")) {
					JsonArray row = element.getAsJsonArray();
					String hash = row.get(2).getAsString();
					if (ArmorLook.validHash(hash)) {
						list.add(new Head(row.get(0).getAsString(), row.get(1).getAsString(), hash, row.get(3).getAsString()));
					}
				}
				if (!list.isEmpty()) {
					use(list);
					fetched = saved.get("fetched").getAsLong();
				}
			}
		} catch (Exception e) {
			Debug.error("head list", e);
		}
		if (!official.isEmpty() && System.currentTimeMillis() - fetched < MAX_AGE_MS) {
			return;
		}
		try {
			fetch();
		} catch (Exception e) {
			Debug.error("head list", e);
			status = "Could not load the heads (" + e.getMessage() + "). Reopen the menu to try again.";
			synchronized (HeadCatalog.class) {
				if (official.isEmpty()) {
					started = false;
				}
			}
		}
	}

	private static void fetch() throws Exception {
		status = "Downloading the heads from Hypixel...";
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(API))
				.header("User-Agent", "ParzMod")
				.timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Hypixel answered " + response.statusCode());
		}
		List<Head> list = new ArrayList<>();
		for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			String hash = hashOf(item);
			if (hash == null || !item.has("id") || !item.has("name")) {
				continue;
			}
			list.add(new Head(item.get("id").getAsString(), item.get("name").getAsString(), hash,
					item.has("tier") ? item.get("tier").getAsString() : ""));
		}
		if (list.isEmpty()) {
			throw new IllegalStateException("the answer has no heads");
		}
		list.sort(Comparator.comparing((Head head) -> head.name().toLowerCase(Locale.ROOT)).thenComparing(Head::id));
		use(list);

		JsonObject saved = new JsonObject();
		saved.addProperty("fetched", System.currentTimeMillis());
		JsonArray rows = new JsonArray();
		for (Head head : list) {
			JsonArray row = new JsonArray();
			row.add(head.id());
			row.add(head.name());
			row.add(head.hash());
			row.add(head.tier());
			rows.add(row);
		}
		saved.add("heads", rows);
		Files.createDirectories(FILE.getParent());
		Files.writeString(FILE, saved.toString(), StandardCharsets.UTF_8);
	}

	/** The texture hash of an item of the API ("skin" holds a base64 text with the texture address), or null. */
	private static String hashOf(JsonObject item) {
		try {
			if (!item.has("skin") || !item.get("skin").isJsonObject()) {
				return null;
			}
			String value = item.getAsJsonObject("skin").get("value").getAsString();
			String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
			String url = JsonParser.parseString(json).getAsJsonObject()
					.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
			int at = url.indexOf(TEXTURE_PREFIX);
			if (at < 0) {
				return null;
			}
			String hash = url.substring(at + TEXTURE_PREFIX.length()).toLowerCase(Locale.ROOT);
			return ArmorLook.validHash(hash) ? hash : null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
