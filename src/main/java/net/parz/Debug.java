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

	// ---- errors ----------------------------------------------------------------------

	private static final java.util.Map<String, long[]> ERRORS = new java.util.HashMap<>(); // signature -> {last shown, hidden count}
	private static final long ERROR_REPEAT_MS = 10_000;

	/**
	 * Shows an error in the chat (red): where it happened, the exception, and the first places in the code.
	 * The same error is shown at most every 10 seconds (with a count of how often it happened in between);
	 * the full stack trace goes to the game log.
	 */
	public static void error(String where, Throwable error) {
		if (!isOn()) {
			return;
		}
		StackTraceElement[] trace = error.getStackTrace();
		String top = trace.length > 0 ? trace[0].toString() : "?";
		String signature = where + "|" + error.getClass().getName() + "|" + top;
		long now = System.currentTimeMillis();
		long[] seen;
		synchronized (ERRORS) {
			seen = ERRORS.computeIfAbsent(signature, k -> new long[]{0, 0});
			if (seen[0] != 0 && now - seen[0] < ERROR_REPEAT_MS) {
				seen[1]++;
				return;
			}
		}
		long hidden = seen[1];
		seen[0] = now;
		seen[1] = 0;

		ParzClient.LOGGER.info("[debug] [error] {}", where, error);
		java.util.List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.literal("[Debug] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("[ERROR] " + where + ": ").withStyle(ChatFormatting.RED))
				.append(Component.literal(error.getClass().getSimpleName() + (error.getMessage() != null ? ": " + error.getMessage() : "")
						+ (hidden > 0 ? "  (" + hidden + " more times in the last seconds)" : "")).withStyle(ChatFormatting.GRAY)));
		int shown = 0;
		for (StackTraceElement element : trace) {
			// the Parz code first: that is where the problem is
			if (element.getClassName().startsWith("net.parz") && shown < 4) {
				lines.add(Component.literal("    at " + element.getClassName().substring(element.getClassName().lastIndexOf('.') + 1)
						+ "." + element.getMethodName() + ":" + element.getLineNumber()).withStyle(ChatFormatting.DARK_RED));
				shown++;
			}
		}
		if (shown == 0 && trace.length > 0) {
			lines.add(Component.literal("    at " + top).withStyle(ChatFormatting.DARK_RED));
		}
		Throwable cause = error.getCause();
		if (cause != null) {
			lines.add(Component.literal("    caused by " + cause.getClass().getSimpleName()
					+ (cause.getMessage() != null ? ": " + cause.getMessage() : "")).withStyle(ChatFormatting.DARK_RED));
		}
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				lines.forEach(client.player::sendSystemMessage);
			}
		});
	}

	/**
	 * Runs a piece of Parz code that the game calls (tick, chat message, HUD). With the debug mode on an exception
	 * is shown in the chat and the game goes on; with it off the exception is thrown as before.
	 */
	public static void guard(String where, Runnable code) {
		try {
			code.run();
		} catch (RuntimeException e) {
			if (!isOn()) {
				throw e;
			}
			error(where, e);
		}
	}

	public static net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick tick(String where,
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick tick) {
		return client -> guard(where, () -> tick.onEndTick(client));
	}

	public static ClientReceiveMessageEvents.Game message(String where, ClientReceiveMessageEvents.Game handler) {
		return (message, overlay) -> guard(where, () -> handler.onReceiveGameMessage(message, overlay));
	}

	public static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement hud(String where,
			net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement element) {
		return (graphics, delta) -> guard(where, () -> element.extractRenderState(graphics, delta));
	}

	/**
	 * A logger that also shows every warning and error of Parz in the chat while the debug mode is on, so a
	 * problem that is only written to the game log (a file that could not be read, a failed update) is seen at once.
	 */
	public static org.slf4j.Logger wrap(org.slf4j.Logger logger) {
		return (org.slf4j.Logger) java.lang.reflect.Proxy.newProxyInstance(Debug.class.getClassLoader(),
				new Class<?>[]{org.slf4j.Logger.class}, (proxy, method, args) -> {
					String name = method.getName();
					if ((name.equals("warn") || name.equals("error")) && args != null && args.length > 0) {
						try {
							int first = args[0] instanceof org.slf4j.Marker ? 1 : 0;
							if (first < args.length && args[first] instanceof String format) {
								Object[] rest = java.util.Arrays.copyOfRange(args, first + 1, args.length);
								if (rest.length == 1 && rest[0] instanceof Object[] array) {
									rest = array;
								}
								org.slf4j.helpers.FormattingTuple tuple = org.slf4j.helpers.MessageFormatter.arrayFormat(format, rest);
								String level = name.toUpperCase(java.util.Locale.ROOT);
								if (tuple.getThrowable() != null) {
									error(level + " " + tuple.getMessage(), tuple.getThrowable());
								} else {
									log(level.toLowerCase(java.util.Locale.ROOT), tuple::getMessage);
								}
							}
						} catch (RuntimeException ignored) {
							// the debug output must never break the logging itself
						}
					}
					try {
						return method.invoke(logger, args);
					} catch (java.lang.reflect.InvocationTargetException e) {
						throw e.getCause();
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
