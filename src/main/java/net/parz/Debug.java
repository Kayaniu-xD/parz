package net.parz;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The /pz debug mode for the developer. While it is on, everything Parz does is written into the chat
 * (and into the game log): menus that are read, chat lines that are recognised, collection changes, dungeon
 * scans and room states, dragon circle states, update steps. Raw chat lines go to the game log only.
 * It is off after every start and only exists for the account of the developer.
 */
public final class Debug {
	/** The Minecraft name that may use /pz debug. */
	private static final String DEVELOPER = "Pay2Loze";
	private static final Pattern FORMATTING = Pattern.compile("§.");

	private static volatile boolean enabled = false;
	private static String lastLine = "";
	private static long lastLineMs = 0;

	private Debug() {
	}

	/** True if the current Minecraft account may use /pz debug. */
	public static boolean allowed() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.getUser() != null && DEVELOPER.equalsIgnoreCase(client.getUser().getName());
	}

	public static boolean isOn() {
		return enabled && allowed();
	}

	public static void set(boolean on) {
		enabled = on;
		lastLine = "";
	}

	/** Shows a line in the chat and writes it to the game log, if the debug mode is on. */
	public static void log(String category, Supplier<String> message) {
		if (!isOn()) {
			return;
		}
		String line = "[" + category + "] " + message.get();
		long now = System.currentTimeMillis();
		if (line.equals(lastLine) && now - lastLineMs < 1000) {
			return; // the same line again and again is shown once
		}
		lastLine = line;
		lastLineMs = now;
		ParzClient.LOGGER.info("[debug] {}", line);
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("[Debug] ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal("[" + category + "] ").withStyle(ChatFormatting.YELLOW))
						.append(Component.literal(line.substring(category.length() + 3)).withStyle(ChatFormatting.GRAY)));
			}
		});
	}

	/** Only writes to the game log (for things that would flood the chat, like every chat line). */
	public static void file(String category, Supplier<String> message) {
		if (isOn()) {
			ParzClient.LOGGER.info("[debug] [{}] {}", category, message.get());
		}
	}

	/** Events that are not tied to one feature. */
	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				log("world", () -> "joined a world/server: " + (client.getCurrentServer() != null ? client.getCurrentServer().ip : "singleplayer")));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> log("world", () -> "disconnected"));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				log("screen", () -> "opened " + screen.getClass().getSimpleName() + " \""
						+ FORMATTING.matcher(screen.getTitle().getString()).replaceAll("") + "\""));
		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				file(overlay ? "actionbar" : "chat", () -> FORMATTING.matcher(message.getString()).replaceAll("")));
	}
}
