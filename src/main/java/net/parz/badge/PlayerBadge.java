package net.parz.badge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The badge of every Parz user, in the tab list and above the head: "[ዞ] Name" - brackets and name in red, the
 * symbol in gold. The name is the real Minecraft name of the player. It is always on (there is no setting) and
 * only changes how the name is drawn on your own screen.
 */
public final class PlayerBadge {
	/** The symbol in the middle of the brackets (it is drawn as the H badge). */
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
	 * with made-up profile names, so the name is looked for in the TEXT. The badge goes in front of the name,
	 * everything else of the line (the level, the icons) stays as it is.
	 */
	public static Component applyInText(Component original) {
		if (original == null) {
			return null;
		}
		String text = original.getString();
		if (text.isEmpty() || text.contains(SYMBOL)) {
			return original;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		int start = -1;
		int end = -1;
		String name = null;
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
					if (start < 0 || index < start) {
						start = index;
						end = after;
						name = text.substring(index, after);
					}
					break;
				}
				from = index + 1;
			}
		}
		if (name == null) {
			return original;
		}
		// the line piece by piece (every piece has its own style): cut it at the name
		List<String> texts = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		original.visit((style, piece) -> {
			texts.add(piece);
			styles.add(style);
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent result = Component.literal("");
		int offset = 0;
		boolean inserted = false;
		for (int i = 0; i < texts.size(); i++) {
			String piece = texts.get(i);
			int pieceStart = offset;
			int pieceEnd = offset + piece.length();
			offset = pieceEnd;
			if (pieceEnd <= start || pieceStart >= end) {
				result.append(Component.literal(piece).withStyle(styles.get(i)));
				continue;
			}
			if (pieceStart < start) {
				result.append(Component.literal(piece.substring(0, start - pieceStart)).withStyle(styles.get(i)));
			}
			if (!inserted) {
				inserted = true;
				result.append(badgeAndName(name, true)); // (only the tab list uses this)
			}
			if (pieceEnd > end) {
				result.append(Component.literal(piece.substring(end - pieceStart)).withStyle(styles.get(i)));
			}
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
