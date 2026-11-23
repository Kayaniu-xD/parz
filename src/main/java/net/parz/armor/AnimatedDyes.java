package net.parz.armor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.parz.Debug;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The animated dyes of Hypixel SkyBlock (Aurora, Rose, Portal, ...). Hypixel changes the color of a dyed piece all the time;
 * every dye is a list of colors (frames) that are shown one after the other and then start again. The frames are the ones of
 * the public SkyBlock item repository, kept in the resource parz/animated_dyes.json.
 *
 * <p>In an {@link ArmorLook} an animated dye is a color value with the bit {@link #FLAG} set (the rest is the number of the
 * dye in {@link #all()}); {@link #resolve(Integer)} turns such a value into the color of this moment.
 */
public final class AnimatedDyes {
	/** Set in a color value of an {@link ArmorLook} for an animated dye. */
	public static final int FLAG = 0x1000000;
	/** How long one frame is shown (Minecraft's own animations run at 20 frames per second). */
	private static final long FRAME_MS = 50;

	/** One animated dye: id in lower case ("aurora"), name ("Aurora Dye") and its colors (0xRRGGBB). */
	public record Dye(String id, String name, int[] frames) {
		/** The color of this moment. */
		public int now() {
			return frames[(int) (System.currentTimeMillis() / FRAME_MS % frames.length)];
		}

		/** The color value of an ArmorLook for this dye. */
		public int code() {
			return FLAG | all().indexOf(this);
		}
	}

	private static volatile List<Dye> dyes = null;

	private AnimatedDyes() {
	}

	/** All animated dyes, sorted by name. */
	public static List<Dye> all() {
		List<Dye> list = dyes;
		if (list == null) {
			synchronized (AnimatedDyes.class) {
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
		try (InputStream in = AnimatedDyes.class.getResourceAsStream("/parz/animated_dyes.json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String key : json.keySet()) {
					JsonArray array = json.getAsJsonArray(key);
					int[] frames = new int[array.size()];
					int n = 0;
					for (JsonElement element : array) {
						try {
							frames[n++] = Integer.parseInt(element.getAsString(), 16) & 0xFFFFFF;
						} catch (NumberFormatException ignored) {
							// a bad frame is left out
						}
					}
					if (n > 0) {
						list.add(new Dye(key.toLowerCase(Locale.ROOT), title(key) + " Dye", java.util.Arrays.copyOf(frames, n)));
					}
				}
			}
		} catch (Exception e) {
			Debug.error("animated dyes", e);
		}
		list.sort(Comparator.comparing(Dye::name));
		return List.copyOf(list);
	}

	/** "PASTEL_SKY" -> "Pastel Sky". */
	private static String title(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
			if (!word.isEmpty()) {
				out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	public static boolean isAnimated(Integer color) {
		return color != null && (color & FLAG) != 0;
	}

	/** The dye of a color value, or null (not animated, or a dye this version does not know). */
	public static Dye dyeOf(Integer color) {
		if (!isAnimated(color)) {
			return null;
		}
		int index = color & 0xFFFF;
		List<Dye> list = all();
		return index < list.size() ? list.get(index) : null;
	}

	/** The dye with this id ("aurora"), or null. */
	public static Dye byId(String id) {
		for (Dye dye : all()) {
			if (dye.id().equals(id)) {
				return dye;
			}
		}
		return null;
	}

	/** The color to draw now: a normal color as it is, an animated dye as its color of this moment (null stays null). */
	public static Integer resolve(Integer color) {
		if (color == null || !isAnimated(color)) {
			return color;
		}
		Dye dye = dyeOf(color);
		return dye == null ? null : dye.now();
	}
}
