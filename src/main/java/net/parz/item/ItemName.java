package net.parz.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A custom item name: a text in which every letter has its own color and formatting. The editor works with one
 * {@link Mark} per character, the saved file with {@link Run runs} of characters that look the same.
 */
public final class ItemName {
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int UNDERLINE = 4;
	public static final int STRIKETHROUGH = 8;
	public static final int OBFUSCATED = 16;

	/** No own color: the letter has the rarity color of the item. */
	public static final int NO_COLOR = -1;

	/** How one character looks. */
	public record Mark(int color, int flags) {
		public static final Mark PLAIN = new Mark(NO_COLOR, 0);

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		public Mark withColor(int rgb) {
			return new Mark(rgb, flags);
		}

		public Mark with(int flag, boolean on) {
			return new Mark(color, on ? flags | flag : flags & ~flag);
		}
	}

	/** A piece of text with one look. */
	public record Run(String text, Mark mark) {
	}

	private ItemName() {
	}

	/** Joins characters that look the same; marks that are missing count as plain. */
	public static List<Run> runs(String text, List<Mark> marks) {
		List<Run> runs = new ArrayList<>();
		int start = 0;
		for (int i = 1; i <= text.length(); i++) {
			if (i == text.length() || !markAt(marks, i).equals(markAt(marks, start))) {
				runs.add(new Run(text.substring(start, i), markAt(marks, start)));
				start = i;
			}
		}
		return runs;
	}

	private static Mark markAt(List<Mark> marks, int index) {
		return index < marks.size() ? marks.get(index) : Mark.PLAIN;
	}

	public static String text(List<Run> runs) {
		StringBuilder text = new StringBuilder();
		for (Run run : runs) {
			text.append(run.text());
		}
		return text.toString();
	}

	public static List<Mark> marks(List<Run> runs) {
		List<Mark> marks = new ArrayList<>();
		for (Run run : runs) {
			for (int i = 0; i < run.text().length(); i++) {
				marks.add(run.mark());
			}
		}
		return marks;
	}

	/**
	 * The marks after the text was edited: the unchanged beginning and end keep their look, new characters look like
	 * the one before them.
	 */
	public static List<Mark> edited(String before, List<Mark> marks, String after) {
		int max = Math.min(before.length(), after.length());
		int start = 0;
		while (start < max && before.charAt(start) == after.charAt(start)) {
			start++;
		}
		int end = 0;
		while (end < max - start && before.charAt(before.length() - 1 - end) == after.charAt(after.length() - 1 - end)) {
			end++;
		}
		Mark typed = start > 0 ? markAt(marks, start - 1) : markAt(marks, 0);
		List<Mark> result = new ArrayList<>(after.length());
		for (int i = 0; i < after.length(); i++) {
			if (i < start) {
				result.add(markAt(marks, i));
			} else if (i >= after.length() - end) {
				result.add(markAt(marks, before.length() - (after.length() - i)));
			} else {
				result.add(typed);
			}
		}
		return result;
	}

	/**
	 * The name as Minecraft text. Every format is set (also to "off"), so the italic Minecraft puts on renamed items
	 * does not get through; letters without a color keep the one of the surrounding text (the rarity color).
	 */
	public static MutableComponent component(List<Run> runs) {
		MutableComponent result = Component.literal("");
		for (Run run : runs) {
			Mark mark = run.mark();
			Style style = Style.EMPTY
					.withBold(mark.has(BOLD))
					.withItalic(mark.has(ITALIC))
					.withUnderlined(mark.has(UNDERLINE))
					.withStrikethrough(mark.has(STRIKETHROUGH))
					.withObfuscated(mark.has(OBFUSCATED));
			if (mark.color() != NO_COLOR) {
				style = style.withColor(mark.color());
			}
			result.append(Component.literal(run.text()).withStyle(style));
		}
		return result;
	}

	/** The runs of an existing name (also one that carries the old color codes in its text). */
	public static List<Run> fromComponent(Component name) {
		StringBuilder text = new StringBuilder();
		List<Mark> marks = new ArrayList<>();
		name.visit((style, piece) -> {
			Mark base = new Mark(style.getColor() == null ? NO_COLOR : style.getColor().getValue(),
					(style.isBold() ? BOLD : 0) | (style.isItalic() ? ITALIC : 0) | (style.isUnderlined() ? UNDERLINE : 0)
							| (style.isStrikethrough() ? STRIKETHROUGH : 0) | (style.isObfuscated() ? OBFUSCATED : 0));
			Mark mark = base;
			for (int i = 0; i < piece.length(); i++) {
				char c = piece.charAt(i);
				if (c == '§' && i + 1 < piece.length()) {
					mark = coded(mark, base, ChatFormatting.getByCode(piece.charAt(++i)));
					continue;
				}
				text.append(c);
				marks.add(mark);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return runs(text.toString(), marks);
	}

	private static Mark coded(Mark mark, Mark base, ChatFormatting code) {
		if (code == null) {
			return mark;
		}
		return switch (code) {
			case BOLD -> mark.with(BOLD, true);
			case ITALIC -> mark.with(ITALIC, true);
			case UNDERLINE -> mark.with(UNDERLINE, true);
			case STRIKETHROUGH -> mark.with(STRIKETHROUGH, true);
			case OBFUSCATED -> mark.with(OBFUSCATED, true);
			case RESET -> base;
			// a color code also ends the formatting before it
			default -> new Mark(TextColor.fromLegacyFormat(code).getValue(), 0);
		};
	}
}
