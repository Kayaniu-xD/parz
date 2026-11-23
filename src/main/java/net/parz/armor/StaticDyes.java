package net.parz.armor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.parz.Debug;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dyes of Hypixel SkyBlock that do not change their color (Carmine, Necron, Pure White, ...), with the name they have in
 * the game (list and colors of the SkyBlock wiki, kept in the resource parz/static_dyes.json). Animated dyes are in {@link AnimatedDyes}.
 */
public final class StaticDyes {
	/** One dye: its name in SkyBlock ("Carmine Dye") and its color (0xRRGGBB). */
	public record Dye(String name, int rgb) {
	}

	private static volatile List<Dye> dyes = null;

	private StaticDyes() {
	}

	/** All static dyes, sorted by name. */
	public static List<Dye> all() {
		List<Dye> list = dyes;
		if (list == null) {
			synchronized (StaticDyes.class) {
				if (dyes == null) {
					dyes = load();
				}
				list = dyes;
			}
		}
		return list;
	}

	private static List<Dye> load() {
		List<Dye> list = new ArrayList<>();
		try (InputStream in = StaticDyes.class.getResourceAsStream("/parz/static_dyes.json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String name : json.keySet()) {
					try {
						list.add(new Dye(name, Integer.parseInt(json.get(name).getAsString(), 16) & 0xFFFFFF));
					} catch (NumberFormatException ignored) {
						// a bad entry is left out
					}
				}
			}
		} catch (Exception e) {
			Debug.error("static dyes", e);
		}
		list.sort(Comparator.comparing(Dye::name));
		return List.copyOf(list);
	}

	/** The name of the SkyBlock dye with exactly this color, or null. */
	public static String nameOf(int rgb) {
		for (Dye dye : all()) {
			if (dye.rgb() == (rgb & 0xFFFFFF)) {
				return dye.name();
			}
		}
		return null;
	}
}
