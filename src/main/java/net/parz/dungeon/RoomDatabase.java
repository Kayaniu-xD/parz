package net.parz.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.parz.ParzClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * All known dungeon rooms (name, type, secrets) and the hashes ("cores") that recognise them. Two data sets are
 * shipped inside the mod:
 * - rooms-odin.json from Odin (BSD 3-Clause): hashes of the blocks of modern Minecraft, matched first.
 * - rooms.json from IllegalMap (MIT): hashes of the old 1.8 block ids, used when the first one does not know a room.
 * Their licenses are next to the files.
 */
public final class RoomDatabase {
	public record RoomInfo(String name, String type, int secrets, int crypts, String shape) {
	}

	private static final Map<Integer, RoomInfo> BY_MODERN_CORE = new HashMap<>();
	private static final Map<Integer, RoomInfo> BY_LEGACY_CORE = new HashMap<>();
	private static final Map<String, Integer> SECRETS_BY_NAME = new HashMap<>();
	private static boolean loaded = false;

	private RoomDatabase() {
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			// IllegalMap first: it knows the secrets of every room
			for (JsonObject room : read("/assets/parz/rooms.json")) {
				RoomInfo info = new RoomInfo(text(room, "name", ""), type(room), number(room, "secrets", -1),
						number(room, "crypts", 0), text(room, "shape", ""));
				SECRETS_BY_NAME.put(normalise(info.name()), info.secrets());
				addCores(room, info, BY_LEGACY_CORE);
			}
			for (JsonObject room : read("/assets/parz/rooms-odin.json")) {
				String name = text(room, "name", "");
				int secrets = number(room, "maxSecrets", SECRETS_BY_NAME.getOrDefault(normalise(name), 0));
				RoomInfo info = new RoomInfo(name, type(room), secrets, number(room, "crypts", 0), text(room, "shape", ""));
				addCores(room, info, BY_MODERN_CORE);
			}
			ParzClient.LOGGER.info("Loaded {} modern and {} legacy dungeon room hashes", BY_MODERN_CORE.size(), BY_LEGACY_CORE.size());
		} catch (Exception e) {
			// the room names are optional: a broken data file must never stop the game from starting
			ParzClient.LOGGER.warn("Could not read the dungeon room data", e);
		}
	}

	/** A number of a JSON object; a missing key or null gives the fallback. */
	private static int number(JsonObject object, String key, int fallback) {
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsInt();
	}

	private static String text(JsonObject object, String key, String fallback) {
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsString();
	}

	private static String type(JsonObject room) {
		String type = text(room, "type", "normal").toLowerCase(Locale.ROOT);
		return type.equals("champion") ? "yellow" : type;
	}

	private static void addCores(JsonObject room, RoomInfo info, Map<Integer, RoomInfo> target) {
		JsonElement cores = room.get("cores");
		if (cores != null && cores.isJsonArray()) {
			for (JsonElement core : cores.getAsJsonArray()) {
				if (!core.isJsonNull()) {
					target.put(core.getAsInt(), info);
				}
			}
		}
	}

	private static java.util.List<JsonObject> read(String resource) {
		java.util.List<JsonObject> rooms = new java.util.ArrayList<>();
		try (InputStream in = RoomDatabase.class.getResourceAsStream(resource)) {
			if (in == null) {
				ParzClient.LOGGER.warn("{} is missing in the mod", resource);
				return rooms;
			}
			JsonArray array = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement element : array) {
				rooms.add(element.getAsJsonObject());
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not load {}", resource, e);
		}
		return rooms;
	}

	private static String normalise(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	public static RoomInfo byModernCore(int core) {
		return BY_MODERN_CORE.get(core);
	}

	public static RoomInfo byLegacyCore(int core) {
		return BY_LEGACY_CORE.get(core);
	}

	public static int size() {
		return BY_MODERN_CORE.size() + BY_LEGACY_CORE.size();
	}
}
