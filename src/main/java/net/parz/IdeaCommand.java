package net.parz;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * /pz idea "the idea": opens a new GitHub issue in the Parz repository with the idea already written into it.
 * The player only has to press "Submit new issue" (a GitHub account is needed). No password or token is stored
 * in the mod, and nothing about the player (name, UUID, server) is put into the issue.
 */
public final class IdeaCommand {
	private static final String ISSUES = "https://github.com/Kayaniu-xD/parz/issues/new";
	private static final int MAX_LENGTH = 1500;

	private IdeaCommand() {
	}

	public static int run(String idea) {
		Minecraft client = Minecraft.getInstance();
		String text = idea.strip();
		if (text.isEmpty()) {
			message(client, Component.literal("[Parz] Write your idea after the command: /pz idea <your idea>").withStyle(ChatFormatting.RED));
			return 0;
		}
		if (text.length() > MAX_LENGTH) {
			text = text.substring(0, MAX_LENGTH);
		}
		String title = "Idea: " + (text.length() > 60 ? text.substring(0, 57).strip() + "..." : text);
		String version = FabricLoader.getInstance().getModContainer("parz")
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String body = text + "\n\n---\nSent from the game with /pz idea (Parz " + version + ", Minecraft "
				+ SharedConstants.getCurrentVersion().name() + ")";
		URI uri = URI.create(ISSUES + "?title=" + encode(title) + "&body=" + encode(body) + "&labels=idea");

		try {
			Util.getPlatform().openUri(uri);
			message(client, Component.literal("[Parz] Your idea is open in your browser. Press \"Submit new issue\" on GitHub to send it. ")
					.withStyle(ChatFormatting.GREEN)
					.append(link(uri, "[Open again]")));
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not open the browser for /pz idea", e);
			message(client, Component.literal("[Parz] Could not open the browser. ").withStyle(ChatFormatting.RED).append(link(uri, "[Click here]")));
		}
		return 1;
	}

	private static Component link(URI uri, String label) {
		return Component.literal(label).withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(uri)));
	}

	private static void message(Minecraft client, Component component) {
		if (client.player != null) {
			client.player.sendSystemMessage(component);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
