package net.parz.skyblock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.parz.config.ParzConfigDefinition;

import java.util.Locale;

/**
 * Detects whether you are currently playing Hypixel SkyBlock:
 * connected to a Hypixel server AND the sidebar scoreboard title contains "SKYBLOCK".
 */
public final class SkyBlockDetector {
	private static boolean onSkyBlock = false;
	private static int tickCounter = 0;

	private SkyBlockDetector() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++tickCounter % 20 == 0) {
				onSkyBlock = detect(client);
			}
		});
	}

	/** True if you are on Hypixel SkyBlock right now. */
	public static boolean isOnSkyBlock() {
		return onSkyBlock;
	}

	/**
	 * True if Parz features may run right now: either "Nur auf SkyBlock" is
	 * switched off, or you are on Hypixel SkyBlock.
	 */
	public static boolean featuresActive() {
		return !ParzConfigDefinition.ONLY_SKYBLOCK.isEnabled() || onSkyBlock;
	}

	private static boolean detect(Minecraft client) {
		if (client.level == null) {
			return false;
		}
		ServerData server = client.getCurrentServer();
		if (server == null || !server.ip.toLowerCase(Locale.ROOT).contains("hypixel")) {
			return false;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return false;
		}
		String title = sidebar.getDisplayName().getString().replaceAll("§.", "");
		return title.toUpperCase(Locale.ROOT).contains("SKYBLOCK");
	}
}
