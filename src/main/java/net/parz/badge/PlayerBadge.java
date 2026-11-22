package net.parz.badge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.parz.Debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The name of every Parz user as that player chose it (default "[ዞ] Name"), in the tab list, the scoreboard, the
 * chat and above the head. It is always on (there is no setting) and only changes how the name is drawn on
 * your own screen.
 */
public final class PlayerBadge {
	/** The symbol of the default look (it is drawn as the H badge). */
	private static final String SYMBOL = "ዞ";

	private PlayerBadge() {
	}

	/** The name in the format its player chose (default: "[ዞ] Name"). */
	private static MutableComponent badgeAndName(String playerName, boolean legacyColors) {
		return BadgeFormat.render(ModUsers.formatOf(playerName), playerName, legacyColors);
	}

	/** The name as "[ዞ] Name" if it is a badge account (by its profile name), otherwise unchanged. */
	public static Component apply(String playerName, Component original) {
		return apply(playerName, original, false);
	}

	/** legacyColors: only the 16 Minecraft colors (for the tab list, see BadgeFormat.render). */
	public static Component apply(String playerName, Component original, boolean legacyColors) {
		if (original == null || playerName == null || !ModUsers.isUser(playerName)) {
			return original;
		}
		if (original.getString().contains(SYMBOL)) {
			return original; // already there
		}
		return badgeAndName(playerName, legacyColors);
	}

	/**
	 * For lists whose entries are not the real profiles: the SkyBlock tab list shows "[550] Pay2Loze" in entries
	 * with made-up profile names, so the name is looked for in the TEXT. The badge goes in the place of the name,
	 * everything else of the line (the level, the icons) stays as it is.
	 */
	public static Component applyInText(Component original) {
		return replaceNames(original, 1, true);
	}

	/** A chat message: every name of a Parz user in it is replaced (the message itself is not touched for other mods). */
	public static Component applyInChat(Component original) {
		if (original == null) {
			return null;
		}
		String text = original.getString();
		if (text.startsWith("[Parz]") || Debug.isOwnMessage(text)) {
			return original;
		}
		return replaceNames(original, 8, false);
	}

	private record Match(int start, int end, String name) {
	}

	/** Replaces up to maxMatches names of Parz users in the text by their chosen format. */
	private static Component replaceNames(Component original, int maxMatches, boolean legacyColors) {
		if (original == null) {
			return null;
		}
		String text = original.getString();
		if (text.isEmpty() || text.contains(SYMBOL)) {
			return original;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		List<Match> found = new ArrayList<>();
		for (String candidate : ModUsers.names()) {
			int from = 0;
			while (true) {
				int index = lower.indexOf(candidate, from);
				if (index < 0) {
					break;
				}
				int after = index + candidate.length();
				boolean wordStart = index == 0 || !isNameChar(lower.charAt(index - 1));
				boolean wordEnd = after >= lower.length() || !isNameChar(lower.charAt(after));
				if (wordStart && wordEnd) {
					found.add(new Match(index, after, text.substring(index, after)));
				}
				from = index + 1;
			}
		}
		if (found.isEmpty()) {
			return original;
		}
		found.sort(Comparator.comparingInt(Match::start));
		List<Match> matches = new ArrayList<>();
		int lastEnd = -1;
		for (Match match : found) {
			if (match.start() >= lastEnd && matches.size() < maxMatches) {
				matches.add(match);
				lastEnd = match.end();
			}
		}

		// the line piece by piece (every piece has its own style): cut it at the names
		List<String> texts = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		original.visit((style, piece) -> {
			texts.add(piece);
			styles.add(style);
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent result = Component.literal("");
		int offset = 0;
		int mi = 0;
		boolean inserted = false;
		for (int i = 0; i < texts.size(); i++) {
			String piece = texts.get(i);
			Style style = styles.get(i);
			int p = 0;
			while (p < piece.length()) {
				int global = offset + p;
				if (mi >= matches.size()) {
					result.append(Component.literal(piece.substring(p)).withStyle(style));
					p = piece.length();
					continue;
				}
				Match match = matches.get(mi);
				if (global < match.start()) {
					int take = Math.min(piece.length() - p, match.start() - global);
					result.append(Component.literal(piece.substring(p, p + take)).withStyle(style));
					p += take;
				} else {
					if (!inserted) {
						inserted = true;
						result.append(badgeAndName(match.name(), legacyColors));
					}
					int skip = Math.min(piece.length() - p, match.end() - global);
					p += skip;
					if (offset + p >= match.end()) {
						mi++;
						inserted = false;
					}
				}
			}
			offset += piece.length();
		}
		return result;
	}

	/** Names that were looked at: the result per text object (teams give the same object again and again). */
	private static final java.util.Map<Component, Object[]> TEAM_CACHE = new java.util.IdentityHashMap<>();

	/** applyInText for the prefix and suffix of scoreboard teams, remembered per text (it is asked for in every frame). */
	public static synchronized Component applyToTeamText(Component original) {
		if (original == null) {
			return null;
		}
		int version = ModUsers.version();
		Object[] cached = TEAM_CACHE.get(original);
		if (cached != null && (int) cached[0] == version) {
			return (Component) cached[1];
		}
		if (TEAM_CACHE.size() > 600) {
			TEAM_CACHE.clear();
		}
		Component result = applyInText(original);
		TEAM_CACHE.put(original, new Object[]{version, result});
		return result;
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}
