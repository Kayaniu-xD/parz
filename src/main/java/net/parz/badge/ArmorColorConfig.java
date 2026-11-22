package net.parz.badge;

import net.fabricmc.loader.api.FabricLoader;
import net.parz.Debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How YOU want your armor to look for other Parz users (and for yourself): a single RGB color, or none.
 * Only the armor pieces whose equipment asset is "dyeable" actually show a color (in practice: leather
 * armor). Other materials (iron, diamond, netherite, ...) are not affected; that is a limit of how
 * Minecraft itself renders armor, not of this feature. Saved in config/parz-armor-color.txt.
 */
public final class ArmorColorConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-armor-color.txt");

	/** Your chosen color (0xRRGGBB), or null if you did not set one. */
	private static volatile Integer color = null;

	private ArmorColorConfig() {
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				String text = Files.readString(FILE).trim();
				color = text.isEmpty() ? null : parse(text);
			}
		} catch (Exception e) {
			Debug.error("armor color config", e);
			color = null;
		}
	}

	private static void write() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, color == null ? "" : String.format("#%06X", color));
		} catch (Exception e) {
			Debug.error("armor color config", e);
		}
	}

	/** Your own color, or null if you did not set one. */
	public static Integer color() {
		return color;
	}

	/** Parses "#RRGGBB" or "RRGGBB" into 0xRRGGBB; throws IllegalArgumentException if that is not valid. */
	public static int parse(String text) {
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6) {
			throw new IllegalArgumentException("A color must look like #RRGGBB");
		}
		try {
			return Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("A color must look like #RRGGBB");
		}
	}

	/** Sets your color (throws IllegalArgumentException if invalid), saves it and tells the registry at once. */
	public static void apply(String text) {
		int rgb = parse(text);
		color = rgb;
		write();
		ModUsers.armorColorChanged();
	}

	public static void reset() {
		color = null;
		write();
		ModUsers.armorColorChanged();
	}

	/** The color as sent to the registry: 6 lower-case hex digits, or "" if you did not set one. */
	public static String wire() {
		return color == null ? "" : String.format(Locale.ROOT, "%06x", color);
	}
}
