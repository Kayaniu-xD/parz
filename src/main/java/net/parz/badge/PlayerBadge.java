package net.parz.badge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.Optional;

/**
 * The [H] badge of the developer account, in the tab list and above the head. It is always on (there is no
 * setting for it) and only changes how the name is drawn on your own screen.
 */
public final class PlayerBadge {
	private static final String BADGE = "[H]";
	/** The account that gets the badge. */
	private static final String OWNER = "Pay2Loze";

	private PlayerBadge() {
	}

	/** The name with the badge in front of it if it is the badge account, otherwise unchanged. */
	public static Component apply(String playerName, Component original) {
		if (original == null || playerName == null || !playerName.equalsIgnoreCase(OWNER)) {
			return original;
		}
		if (original.getString().contains(BADGE)) {
			return original; // already there
		}
		// the badge has the color of the name (the last colored part of the name: the rank comes before it)
		TextColor[] color = new TextColor[1];
		original.visit((style, text) -> {
			if (!text.isBlank() && style.getColor() != null) {
				color[0] = style.getColor();
			}
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent badge = Component.literal(BADGE + " ");
		badge.withStyle(style -> color[0] != null ? style.withColor(color[0]) : style.withColor(ChatFormatting.RED));
		return badge.append(original);
	}
}
