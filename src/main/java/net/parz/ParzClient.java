package net.parz;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.Screen;
import net.parz.config.ParzConfig;
import net.parz.dashboard.CollectionTracker;
import net.parz.dashboard.DashboardData;
import net.parz.dashboard.DashboardTracker;
import net.parz.dungeon.DragonMarkers;
import net.parz.dungeon.DungeonMap;
import net.parz.gui.DashboardScreen;
import net.parz.gui.HudEditorScreen;
import net.parz.hud.HudTrackers;
import net.parz.gui.ParzConfigScreen;
import net.minecraft.client.Minecraft;
import net.parz.update.ModUpdater;
import net.parz.skyblock.HypixelResources;
import net.parz.skyblock.SkyBlockDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
		DashboardData.ensureAccount(Minecraft.getInstance().getUser().getName());
		// a different Minecraft account than last time: its old skills and collections are removed
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				DashboardData.ensureAccount(client.getUser().getName()));
		SkyBlockDetector.register();
		DashboardTracker.register();
		CollectionTracker.register();
		HudTrackers.register();
		DungeonMap.register();
		DragonMarkers.register();
		ModUpdater.checkOnStartup();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(ClientCommands.literal("pz")
					.executes(context -> {
						screenToOpen = ParzConfigScreen::new;
						return 1;
					})
					.then(ClientCommands.literal("hud").executes(context -> {
						screenToOpen = HudEditorScreen::new;
						return 1;
					}))
					.then(ClientCommands.literal("dungeon").executes(context -> {
							List<String> lines = new ArrayList<>(net.parz.dungeon.DungeonMap.debug(Minecraft.getInstance()));
							lines.addAll(net.parz.dungeon.DragonMarkers.debug(Minecraft.getInstance()));
							for (String line : lines) {
								context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] " + line));
							}
							return 1;
						}))
						.then(ClientCommands.literal("help").executes(context -> {
							sendHelp(context.getSource());
							return 1;
						}))
						.then(ClientCommands.literal("dash").executes(context -> {
							screenToOpen = DashboardScreen::new;
							return 1;
						}))
						.then(ClientCommands.literal("update").executes(context -> {
							ModUpdater.run(context.getSource());
							return 1;
						})));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ModUpdater.tick(client);
			if (screenToOpen != null) {
				Screen screen = screenToOpen.get();
				screenToOpen = null;
				client.gui.setScreen(screen);
			}
		});

		// everything (settings, skills, collections, HUD) is written when the game closes
		// or when you leave a server; the shutdown hook covers the cases the events miss
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveAll());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveAll());
		Runtime.getRuntime().addShutdownHook(new Thread(ParzClient::saveAll, "parz-save"));

		LOGGER.info("Parz loaded - use /pz, /pz hud, /pz update and /pz dash");
	}

	/** Every command with a short description; the command is clickable and runs when you click it. */
	private static final String[][] COMMANDS = {
			{"pz", "Opens the settings menu"},
			{"pz dash", "Opens the dashboard with your skills, collections and playtime"},
			{"pz hud", "Move and resize the HUD elements (trackers, dungeon map)"},
			{"pz dungeon", "Shows what the dungeon scanner sees right now (for bug reports)"},
			{"pz update", "Installs the newest Parz version from GitHub"},
			{"pz help", "Shows this list"}
	};

	private static void sendHelp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Commands (click one to run it):")
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		for (String[] entry : COMMANDS) {
			String command = entry[0];
			net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.literal("/" + command)
					.withStyle(style -> style
							.withColor(net.minecraft.ChatFormatting.AQUA)
							.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
							.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
									net.minecraft.network.chat.Component.literal("Click to run /" + command))))
					.append(net.minecraft.network.chat.Component.literal("  -  " + entry[1])
							.withStyle(net.minecraft.ChatFormatting.GRAY));
			source.sendFeedback(line);
		}
	}

	/** Writes the settings and the dashboard data (skills, collections, playtime, HUD) to disk. */
	public static synchronized void saveAll() {
		try {
			ParzConfig.save();
			DashboardData.save();
		} catch (Exception e) {
			LOGGER.error("Could not save Parz data", e);
		}
	}
}
