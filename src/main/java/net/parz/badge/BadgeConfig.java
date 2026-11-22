package net.parz.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How YOU want your name to look for other Parz users (and for yourself): one text with a color for every
 * letter, or a JSON text component. Saved in config/parz-badge.json. The default is "[ዞ] Name".
 */
public final class BadgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-badge.json");

	/** The saved settings. */
	public static final class Data {
		public boolean json = false;
		/** The whole text, "{name}" stands for your real Minecraft name. */
		public String text = "[ዞ] {name}";
		/** The color of the letters that have no color of their own. */
		public String baseColor = "#FF5555";
		/** One color per unit of the text ("" = the base color); a unit is a character or "{name}". */
		public List<String> colors = new ArrayList<>(List.of("#FF5555", "#FFAA00", "#FF5555", "", "#FF5555"));
		public String jsonText = "";

		// the first version (left / symbol / right / colors): only read to convert an old file
		public String left = null;
		public String symbol = null;
		public String right = null;
		public String bracketColor = null;
		public String symbolColor = null;
		public String nameColor = null;
		public String name = null;

		public Data copy() {
			Data copy = new Data();
			copy.json = json;
			copy.text = text;
			copy.baseColor = baseColor;
			copy.colors = new ArrayList<>(colors);
			copy.jsonText = jsonText;
			return copy;
		}
	}

	private static Data data = new Data();
	private static List<BadgeFormat.Run> runs = null;

	private BadgeConfig() {
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = convertOld(loaded);
					}
				}
			}
		} catch (Exception e) {
			Debug.error("badge config", e);
		}
		runs = null;
	}

	/** A file of the first version had left / symbol / right and three colors: it becomes one text with colors. */
	private static Data convertOld(Data loaded) {
		if (loaded.left == null && loaded.symbol == null && loaded.right == null) {
			if (loaded.colors == null) {
				loaded.colors = new ArrayList<>();
			}
			return loaded;
		}
		Data result = new Data();
		result.json = loaded.json;
		result.jsonText = loaded.jsonText == null ? "" : loaded.jsonText;
		String left = loaded.left == null ? "[" : loaded.left;
		String symbol = loaded.symbol == null ? "ዞ" : loaded.symbol;
		String right = loaded.right == null ? "]" : loaded.right;
		String name = loaded.name == null || loaded.name.isBlank() ? "{name}" : loaded.name;
		String bracket = loaded.bracketColor == null ? "#FF5555" : loaded.bracketColor;
		String symbolColor = loaded.symbolColor == null ? "#FFAA00" : loaded.symbolColor;
		String nameColor = loaded.nameColor == null ? "#FF5555" : loaded.nameColor;
		result.text = left + symbol + right + " " + name;
		result.baseColor = nameColor;
		result.colors = new ArrayList<>();
		for (String ignored : BadgeFormat.tokenize(left)) result.colors.add(bracket);
		for (String ignored : BadgeFormat.tokenize(symbol)) result.colors.add(symbolColor);
		for (String ignored : BadgeFormat.tokenize(right)) result.colors.add(bracket);
		result.colors.add("");
		for (String ignored : BadgeFormat.tokenize(name)) result.colors.add(nameColor);
		return result;
	}

	private static void write() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			Debug.error("badge config", e);
		}
	}

	public static Data data() {
		return data;
	}

	/** The format of your name: what is saved, or the default if that is not valid. */
	public static List<BadgeFormat.Run> runs() {
		if (runs == null) {
			try {
				runs = compute(data);
			} catch (RuntimeException e) {
				runs = defaultRuns();
			}
		}
		return runs;
	}

	public static List<BadgeFormat.Run> defaultRuns() {
		return compute(new Data());
	}

	/** The format the given settings make; throws IllegalArgumentException with the reason if they are wrong. */
	public static List<BadgeFormat.Run> compute(Data settings) {
		if (settings.json) {
			return BadgeFormat.fromComponentJson(settings.jsonText);
		}
		int base = BadgeFormat.parseColor(settings.baseColor);
		if (base < 0) {
			throw new IllegalArgumentException("A color must look like #FF5555");
		}
		List<String> units = BadgeFormat.tokenize(settings.text == null ? "" : settings.text);
		List<Integer> colors = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			String own = i < settings.colors.size() ? settings.colors.get(i) : "";
			if (own == null || own.isBlank()) {
				colors.add(base);
			} else {
				int rgb = BadgeFormat.parseColor(own);
				if (rgb < 0) {
					throw new IllegalArgumentException("A color must look like #FF5555");
				}
				colors.add(rgb);
			}
		}
		return BadgeFormat.build(units, colors);
	}

	/** Takes over the settings if they are valid (throws IllegalArgumentException if not), saves them and tells the registry. */
	public static void apply(Data settings) {
		List<BadgeFormat.Run> result = compute(settings);
		data = settings;
		runs = result;
		write();
		ModUsers.formatChanged();
	}

	public static void reset() {
		apply(new Data());
	}

	/** The format as it is sent to the others. */
	public static String wire() {
		return BadgeFormat.toWire(runs());
	}
}
