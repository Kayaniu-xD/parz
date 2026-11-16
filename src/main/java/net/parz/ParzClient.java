package net.parz;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.parz.config.ParzConfig;
import net.parz.dashboard.DashboardData;
import net.parz.dashboard.DashboardTracker;
import net.parz.gui.DashboardScreen;
import net.parz.gui.HudEditorScreen;
import net.parz.hud.HudTrackers;
import net.parz.gui.ParzConfigScreen;
import net.parz.skyblock.HypixelResources;
import net.parz.skyblock.SkyBlockDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class ParzClient implements ClientModInitializer {
	public static final String MOD_ID = "parz";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * The chat screen closes itself right after a command was sent, so screens are
	 * opened on the next client tick instead of directly inside the command.
	 */
	private static Supplier<Screen> screenToOpen = null;

	@Override
	public void onInitializeClient() {
		ParzConfig.load();
		HypixelResources.load();
		DashboardData.load();
		SkyBlockDetector.register();
		DashboardTracker.register();
		HudTrackers.register();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(ClientCommands.literal("parz")
					.executes(context -> {
						screenToOpen = ParzConfigScreen::new;
						return 1;
					})
					.then(ClientCommands.literal("hud").executes(context -> {
						screenToOpen = HudEditorScreen::new;
						return 1;
					})));
			dispatcher.register(ClientCommands.literal("parz-dash").executes(context -> {
				screenToOpen = DashboardScreen::new;
				return 1;
			}));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (screenToOpen != null) {
				Screen screen = screenToOpen.get();
				screenToOpen = null;
				client.gui.setScreen(screen);
			}
		});

		LOGGER.info("Parz loaded - use /parz, /parz hud and /parz-dash");
	}
}
