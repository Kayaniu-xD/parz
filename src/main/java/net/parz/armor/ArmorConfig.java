package net.parz.armor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;
import net.parz.badge.ModUsers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YOUR armor look (see {@link ArmorLook}): chosen in /pz customarmor, saved in config/parz-armor.json and sent to
 * the registry at once, so every other Parz user sees it. A change is saved for good: it is there again the next
 * time you start the game and for everybody who is online.
 */
public final class ArmorConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-armor.json");

	private static volatile ArmorLook look = ArmorLook.NONE;

	/** True only while the armor menu draws the preview of your player, which then always wears a full armor set. */
	public static volatile boolean previewing = false;

	private ArmorConfig() {
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			String skin = text(json, "skin");
			look = new ArmorLook(
					ArmorLook.validHash(skin) ? skin : null,
					rgb(json, "chest"), rgb(json, "legs"), rgb(json, "feet"));
		} catch (Exception e) {
			Debug.error("armor config", e);
			look = ArmorLook.NONE;
		}
	}

	private static void write() {
		try {
			ArmorLook now = look;
			JsonObject json = new JsonObject();
			if (now.skin() != null) {
				json.addProperty("skin", now.skin());
			}
			add(json, "chest", now.chest());
			add(json, "legs", now.legs());
			add(json, "feet", now.feet());
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Debug.error("armor config", e);
		}
	}

	/** Your own look. */
	public static ArmorLook look() {
		return look;
	}

	/** Takes this as your look, saves it and tells the registry at once. */
	public static void set(ArmorLook next) {
		look = next == null ? ArmorLook.NONE : next;
		write();
		ModUsers.armorChanged();
	}

	/** The look as sent to the registry. */
	public static String wire() {
		return look.wire();
	}

	private static String text(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
	}

	private static Integer rgb(JsonObject json, String key) {
		try {
			String text = text(json, key);
			return text == null ? null : Integer.parseInt(text, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void add(JsonObject json, String key, Integer rgb) {
		if (rgb != null) {
			json.addProperty(key, String.format("%06x", rgb & 0xFFFFFF));
		}
	}
}
