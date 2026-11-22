package net.parz.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How a name looks: a short list of colored pieces of text ("runs"). It comes from the simple menu fields or
 * from a Minecraft text component in JSON, and it is what is sent to other Parz users. Everything is limited
 * and cleaned (no clicks, no hover texts, no random characters), because other people's screens show it.
 *
 * "{name}" in a text is replaced by the real name of the player.
 */
public final class BadgeFormat {
	public static final int MAX_CHARS = 48;
	public static final int MAX_RUNS = 30;
	/** The wire format must stay below the 700 characters of the registry. */
	private static final int MAX_WIRE = 650;

	/** One piece: text, color (RGB, -1 = no color) and flags ("b" bold, "i" italic, "u" underlined, "s" strikethrough). */
	public record Run(String text, int rgb, String flags) {
	}

	private BadgeFormat() {
	}

	/** Cuts a text into units: every character, but "{name}" is one unit. */
	public static List<String> tokenize(String text) {
		List<String> units = new ArrayList<>();
		int i = 0;
		while (i < text.length()) {
			if (text.startsWith("{name}", i)) {
				units.add("{name}");
				i += 6;
			} else {
				int length = Character.charCount(text.codePointAt(i));
				units.add(text.substring(i, i + length));
				i += length;
			}
		}
		return units;
	}

	/** Units with a color each (-1 = none) become the pieces of a format (same colors next to each other are joined). */
	public static List<Run> build(List<String> units, List<Integer> colors) {
		List<Run> runs = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			runs.add(new Run(units.get(i), colors.get(i), ""));
		}
		return check(normalize(runs));
	}

	/** The format as a Minecraft text component in JSON (what the JSON mode of the menu shows). */
	public static String toJson(List<Run> runs) {
		JsonArray extra = new JsonArray();
		for (Run run : runs) {
			JsonObject piece = new JsonObject();
			piece.addProperty("text", run.text());
			if (run.rgb() >= 0) {
				piece.addProperty("color", String.format(Locale.ROOT, "#%06X", run.rgb()));
			}
			if (run.flags().indexOf('b') >= 0) piece.addProperty("bold", true);
			if (run.flags().indexOf('i') >= 0) piece.addProperty("italic", true);
			if (run.flags().indexOf('u') >= 0) piece.addProperty("underlined", true);
			if (run.flags().indexOf('s') >= 0) piece.addProperty("strikethrough", true);
			extra.add(piece);
		}
		JsonObject root = new JsonObject();
		root.addProperty("text", "");
		root.add("extra", extra);
		return root.toString();
	}

	/** The simple version: left bracket, symbol, right bracket, a space and the name. */
	public static List<Run> simple(String left, String symbol, String right, int bracketRgb, int symbolRgb, int nameRgb, String nameText) {
		List<Run> runs = new ArrayList<>();
		runs.add(new Run(left, bracketRgb, ""));
		runs.add(new Run(symbol, symbolRgb, ""));
		runs.add(new Run(right, bracketRgb, ""));
		runs.add(new Run(" ", -1, ""));
		// empty = your real name, otherwise the text you wrote
		runs.add(new Run(nameText == null || nameText.isBlank() ? "{name}" : nameText, nameRgb, ""));
		return check(normalize(runs));
	}

	/** A Minecraft text component in JSON, for example {"text":"","extra":[{"text":"[","color":"#FF5555"}, ...]}. */
	public static List<Run> fromComponentJson(String json) {
		JsonElement element;
		try {
			element = JsonParser.parseString(json.trim());
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("That is not valid JSON");
		}
		trimColors(element);
		Optional<Component> parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result();
		if (parsed.isEmpty()) {
			throw new IllegalArgumentException("That is not a Minecraft text component");
		}
		List<Run> runs = new ArrayList<>();
		parsed.get().visit((style, text) -> {
			if (!text.isEmpty()) {
				TextColor color = style.getColor();
				StringBuilder flags = new StringBuilder();
				if (style.isBold()) flags.append('b');
				if (style.isItalic()) flags.append('i');
				if (style.isUnderlined()) flags.append('u');
				if (style.isStrikethrough()) flags.append('s');
				runs.add(new Run(text, color == null ? -1 : color.getValue(), flags.toString()));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return check(normalize(runs));
	}

	/** A color like "#FF5555" or "FF5555" (a space at the end is fine), or -1 if it is not one. */
	public static int parseColor(String text) {
		if (text == null) {
			return -1;
		}
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (!hex.matches("[0-9a-fA-F]{6}")) {
			return -1;
		}
		return Integer.parseInt(hex, 16);
	}

	/** Colors in JSON may have a space at the end ("#FFAA00 "): that is removed so the game accepts them. */
	private static void trimColors(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			for (String key : new ArrayList<>(object.keySet())) {
				JsonElement child = object.get(key);
				if (key.equals("color") && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
					object.addProperty(key, child.getAsString().trim());
				} else {
					trimColors(child);
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				trimColors(child);
			}
		}
	}

	/** Pieces with the same color and flags next to each other become one piece. */
	private static List<Run> normalize(List<Run> runs) {
		List<Run> out = new ArrayList<>();
		for (Run run : runs) {
			String text = clean(run.text());
			if (text.isEmpty()) {
				continue;
			}
			Run last = out.isEmpty() ? null : out.get(out.size() - 1);
			if (last != null && last.rgb() == run.rgb() && last.flags().equals(run.flags())) {
				out.set(out.size() - 1, new Run(last.text() + text, last.rgb(), last.flags()));
			} else {
				out.add(new Run(text, run.rgb(), run.flags()));
			}
		}
		return out;
	}

	/** No control characters or section signs. */
	private static String clean(String text) {
		StringBuilder sb = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (c >= 32 && c != 127 && c != '§') {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static List<Run> check(List<Run> runs) {
		if (runs.isEmpty()) {
			throw new IllegalArgumentException("There is no text in it");
		}
		if (runs.size() > MAX_RUNS) {
			throw new IllegalArgumentException("Too many different colors/styles (at most " + MAX_RUNS + " pieces)");
		}
		int chars = 0;
		for (Run run : runs) {
			chars += run.text().length();
		}
		if (chars > MAX_CHARS) {
			throw new IllegalArgumentException("Too long: " + chars + " characters (at most " + MAX_CHARS + ")");
		}
		return runs;
	}

	// ---- sending and receiving ----

	/** The compact text that is sent to the registry. */
	public static String toWire(List<Run> runs) {
		JsonArray array = new JsonArray();
		for (Run run : runs) {
			JsonArray piece = new JsonArray();
			piece.add(run.text());
			piece.add(run.rgb() < 0 ? "" : String.format(Locale.ROOT, "%06X", run.rgb()));
			piece.add(run.flags());
			array.add(piece);
		}
		String wire = array.toString();
		return wire.length() > MAX_WIRE ? "" : wire;
	}

	/** What another player sent: read carefully, null if it is not valid. */
	public static List<Run> fromWire(String wire) {
		try {
			if (wire == null || wire.isEmpty() || wire.length() > 700) {
				return null;
			}
			List<Run> runs = new ArrayList<>();
			for (JsonElement element : JsonParser.parseString(wire).getAsJsonArray()) {
				JsonArray piece = element.getAsJsonArray();
				String color = piece.get(1).getAsString();
				String flags = piece.get(2).getAsString().replaceAll("[^bius]", "");
				int rgb = color.isEmpty() ? -1 : parseColor(color);
				runs.add(new Run(piece.get(0).getAsString(), rgb, flags));
			}
			return check(normalize(runs));
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ---- showing ----

	/**
	 * The component for a player: exactly the format they chose, "{name}" replaced by their real name. The format
	 * may look like anything, also like the name of another player; nothing is added to it.
	 */
	public static MutableComponent render(List<Run> runs, String playerName) {
		MutableComponent result = Component.literal("");
		for (Run run : runs) {
			result.append(Component.literal(run.text().replace("{name}", playerName)).withStyle(styleOf(run)));
		}
		return result;
	}

	private static Style styleOf(Run run) {
		Style style = Style.EMPTY;
		if (run.rgb() >= 0) {
			style = style.withColor(TextColor.fromRgb(run.rgb()));
		}
		if (run.flags().indexOf('b') >= 0) style = style.withBold(true);
		if (run.flags().indexOf('i') >= 0) style = style.withItalic(true);
		if (run.flags().indexOf('u') >= 0) style = style.withUnderlined(true);
		if (run.flags().indexOf('s') >= 0) style = style.withStrikethrough(true);
		return style;
	}
}
