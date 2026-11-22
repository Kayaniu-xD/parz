package net.parz.badge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * The badge of the developer account, in the tab list and above the head: "[ዞ] Name" - brackets and name in red,
 * the symbol in gold. The name is the real Minecraft name of the player. It is always on (there is no setting)
 * and only changes how the name is drawn on your own screen.
 */
public final class PlayerBadge {
	private static final TextColor RED = TextColor.fromRgb(0xFF5555);
	private static final TextColor GOLD = TextColor.fromRgb(0xFFAA00);
	/** The symbol in the middle of the brackets (it is drawn as the H badge). */
	private static final String SYMBOL = "ዞ";
	/** The accounts that get the badge. */
	private static final String[] OWNERS = {"Pay2Loze"};

	private PlayerBadge() {
	}

	private static boolean isOwner(String playerName) {
		for (String owner : OWNERS) {
			if (owner.equalsIgnoreCase(playerName)) {
				return true;
			}
		}
		return false;
	}

	/** The name as "[ዞ] Name" if it is a badge account, otherwise unchanged. */
	public static Component apply(String playerName, Component original) {
		if (original == null || playerName == null || !isOwner(playerName)) {
			return original;
		}
		if (original.getString().contains(SYMBOL)) {
			return original; // already there
		}
		MutableComponent name = Component.literal("");
		name.append(Component.literal("[").withStyle(style -> style.withColor(RED)));
		name.append(Component.literal(SYMBOL).withStyle(style -> style.withColor(GOLD)));
		name.append(Component.literal("]").withStyle(style -> style.withColor(RED)));
		name.append(Component.literal(" "));
		name.append(Component.literal(playerName).withStyle(style -> style.withColor(RED)));
		return name;
	}
}
