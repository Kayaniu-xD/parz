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
		// IllegalMap first: it knows the secrets of every room
		for (JsonObject room : read("/assets/parz/rooms.json")) {
			RoomInfo info = info(room, "secrets", -1);
			SECRETS_BY_NAME.put(normalise(info.name()), info.secrets());
			addCores(room, info, BY_LEGACY_CORE);
		}
		for (JsonObject room : read("/assets/parz/rooms-odin.json")) {
			String name = room.get("name").getAsString();
			int secrets = room.has("maxSecrets") ? room.get("maxSecrets").getAsInt()
					: SECRETS_BY_NAME.getOrDefault(normalise(name), 0);
			RoomInfo info = new RoomInfo(name, type(room), secrets,
					room.has("crypts") ? room.get("crypts").getAsInt() : 0,
					room.has("shape") ? room.get("shape").getAsString() : "");
			addCores(room, info, BY_MODERN_CORE);
		}
		ParzClient.LOGGER.info("Loaded {} modern and {} legacy dungeon room hashes", BY_MODERN_CORE.size(), BY_LEGACY_CORE.size());
	}

	private static RoomInfo info(JsonObject room, String secretsKey, int missing) {
		return new RoomInfo(room.get("name").getAsString(), type(room),
				room.has(secretsKey) ? room.get(secretsKey).getAsInt() : missing,
				room.has("crypts") ? room.get("crypts").getAsInt() : 0,
				room.has("shape") ? room.get("shape").getAsString() : "");
	}

	private static String type(JsonObject room) {
		String type = room.has("type") ? room.get("type").getAsString().toLowerCase(Locale.ROOT) : "normal";
		return type.equals("champion") ? "yellow" : type;
	}

	private static void addCores(JsonObject room, RoomInfo info, Map<Integer, RoomInfo> target) {
		if (room.has("cores")) {
			for (JsonElement core : room.getAsJsonArray("cores")) {
				target.put(core.getAsInt(), info);
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
