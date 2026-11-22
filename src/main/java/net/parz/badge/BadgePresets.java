package net.parz.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Name looks that you saved with a name of their own, to load them again later (/pz customname, "Presets").
 * Saved in config/parz-badge-presets.json.
 */
public final class BadgePresets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-badge-presets.json");
	private static final Type LIST_TYPE = new TypeToken<List<Preset>>() {
	}.getType();
	public static final int MAX_PRESETS = 50;
	public static final int MAX_NAME = 24;

	public static final class Preset {
		public String name = "";
		public BadgeConfig.Data data = new BadgeConfig.Data();
	}

	private static List<Preset> presets = new ArrayList<>();

	private BadgePresets() {
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					List<Preset> loaded = GSON.fromJson(reader, LIST_TYPE);
					if (loaded != null) {
						presets = new ArrayList<>(loaded);
						presets.removeIf(preset -> preset == null || preset.name == null || preset.data == null);
					}
				}
			}
		} catch (Exception e) {
			Debug.error("badge presets", e);
		}
	}

	private static void write() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(presets, LIST_TYPE, writer);
			}
		} catch (Exception e) {
			Debug.error("badge presets", e);
		}
	}

	public static List<Preset> list() {
		return presets;
	}

	/** Saves the settings under a name (an existing preset with that name is replaced). Throws IllegalArgumentException if it does not work. */
	public static void put(String name, BadgeConfig.Data data) {
		String clean = name == null ? "" : name.trim();
		if (clean.isEmpty()) {
			throw new IllegalArgumentException("Give the preset a name");
		}
		BadgeConfig.compute(data); // only valid looks are saved
		Preset preset = null;
		for (Preset existing : presets) {
			if (existing.name.equalsIgnoreCase(clean)) {
				preset = existing;
			}
		}
		if (preset == null) {
			if (presets.size() >= MAX_PRESETS) {
				throw new IllegalArgumentException("Too many presets (at most " + MAX_PRESETS + ")");
			}
			preset = new Preset();
			presets.add(preset);
		}
		preset.name = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
		preset.data = data.copy();
		write();
	}

	public static void remove(int index) {
		if (index >= 0 && index < presets.size()) {
			presets.remove(index);
			write();
		}
	}
}
