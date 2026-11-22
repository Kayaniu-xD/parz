package net.parz.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * How YOU want your name to look for other Parz users (and for yourself): the simple fields of the menu or a
 * JSON text component. Saved in config/parz-badge.json. The default is "[ዞ] Name".
 */
public final class BadgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-badge.json");

	/** The saved settings. */
	public static final class Data {
		public boolean json = false;
		public String left = "[";
		public String symbol = "ዞ";
		public String right = "]";
		public String bracketColor = "#FF5555";
		public String symbolColor = "#FFAA00";
		public String nameColor = "#FF5555";
		/** The name itself: empty = your real Minecraft name. */
		public String name = "";
		public String jsonText = "";
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
						data = loaded;
					}
				}
			}
		} catch (Exception e) {
			Debug.error("badge config", e);
		}
		runs = null;
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
		int bracket = BadgeFormat.parseColor(settings.bracketColor);
		int symbol = BadgeFormat.parseColor(settings.symbolColor);
		int name = BadgeFormat.parseColor(settings.nameColor);
		if (bracket < 0 || symbol < 0 || name < 0) {
			throw new IllegalArgumentException("A color must look like #FF5555");
		}
		return BadgeFormat.simple(settings.left, settings.symbol, settings.right, bracket, symbol, name, settings.name);
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

	/** The format as it is sent to the others ("" if it is the default, they use theirs then). */
	public static String wire() {
		return BadgeFormat.toWire(runs());
	}
}
