package net.parz.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.ParzClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores all on/off values in config/parz.json.
 */
public final class ParzConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz.json");
	private static final Map<String, Boolean> VALUES = new ConcurrentHashMap<>();

	private ParzConfig() {
	}

	public static boolean get(String key, boolean defaultValue) {
		return VALUES.getOrDefault(key, defaultValue);
	}

	public static void set(String key, boolean value) {
		VALUES.put(key, value);
		save();
	}

	public static void load() {
		VALUES.clear();
		if (!Files.exists(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root != null && root.isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
					if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
						VALUES.put(entry.getKey(), entry.getValue().getAsBoolean());
					}
				}
			}
		} catch (Exception e) {
			ParzClient.LOGGER.error("Could not read {}", FILE, e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		VALUES.forEach(root::addProperty);
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			ParzClient.LOGGER.error("Could not write {}", FILE, e);
		}
	}
}
