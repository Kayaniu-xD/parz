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
import java.util.Map;

/**
 * All known dungeon rooms (name, type, secrets) and the hashes ("cores") that recognise them. The data is the
 * rooms.json of IllegalMap (MIT license, see rooms.json.LICENSE.txt), shipped inside the mod.
 */
public final class RoomDatabase {
	public record RoomInfo(String name, String type, int secrets, int crypts, String shape) {
	}

	private static final Map<Integer, RoomInfo> BY_CORE = new HashMap<>();
	private static boolean loaded = false;

	private RoomDatabase() {
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream in = RoomDatabase.class.getResourceAsStream("/assets/parz/rooms.json")) {
			if (in == null) {
				ParzClient.LOGGER.warn("rooms.json is missing in the mod");
				return;
			}
			JsonArray rooms = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement element : rooms) {
				JsonObject room = element.getAsJsonObject();
				RoomInfo info = new RoomInfo(
						room.get("name").getAsString(),
						room.has("type") ? room.get("type").getAsString() : "normal",
						room.has("secrets") ? room.get("secrets").getAsInt() : 0,
						room.has("crypts") ? room.get("crypts").getAsInt() : 0,
						room.has("shape") ? room.get("shape").getAsString() : "");
				if (room.has("cores")) {
					for (JsonElement core : room.getAsJsonArray("cores")) {
						BY_CORE.put(core.getAsInt(), info);
					}
				}
			}
			ParzClient.LOGGER.info("Loaded {} dungeon room hashes", BY_CORE.size());
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not load rooms.json", e);
		}
	}

	public static RoomInfo byCore(int core) {
		return BY_CORE.get(core);
	}

	public static int size() {
		return BY_CORE.size();
	}
}
