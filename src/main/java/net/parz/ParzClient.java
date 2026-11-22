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
	public static final Logger LOGGER = Debug.wrap(LoggerFactory.getLogger(MOD_ID));

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
		Debug.register();
		net.parz.hollows.NucleusSlot.register();
		net.parz.fishing.FishingPuzzle.register();
		net.parz.badge.BadgeConfig.load();
		net.parz.badge.BadgePresets.load();
		net.parz.badge.ArmorColorConfig.load();
		net.parz.badge.ModUsers.register();
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
						.then(ClientCommands.literal("debug")
							.executes(context -> debugCommand(context.getSource(), null))
							.then(ClientCommands.literal("on").executes(context -> debugCommand(context.getSource(), true)))
							.then(ClientCommands.literal("off").executes(context -> debugCommand(context.getSource(), false))))
					.then(ClientCommands.literal("nucleus")
							.executes(context -> {
								net.parz.hollows.NucleusSlot.test(false);
								return 1;
							})
							.then(ClientCommands.literal("alloy").executes(context -> {
								net.parz.hollows.NucleusSlot.test(true);
								return 1;
							})))
					.then(ClientCommands.literal("fishing")
							.executes(context -> {
								net.parz.fishing.FishingPuzzle.test("");
								return 1;
							})
							.then(ClientCommands.literal("jawbus").executes(context -> {
								net.parz.fishing.FishingPuzzle.test("jawbus");
								return 1;
							}))
							.then(ClientCommands.literal("thunder").executes(context -> {
								net.parz.fishing.FishingPuzzle.test("thunder");
								return 1;
							}))
							.then(ClientCommands.literal("ragnarok").executes(context -> {
								net.parz.fishing.FishingPuzzle.test("ragnarok");
								return 1;
							})))
					.then(ClientCommands.literal("customname")
							.executes(context -> {
								screenToOpen = net.parz.gui.NameScreen::new;
								return 1;
							})
							.then(ClientCommands.literal("reset").executes(context -> {
								net.parz.badge.BadgeConfig.reset();
								context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Your name is back to the default.")
										.withStyle(net.minecraft.ChatFormatting.GREEN));
								return 1;
							}))
							.then(ClientCommands.literal("json").then(ClientCommands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
								return nameJson(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "text"));
							}))))
					.then(ClientCommands.literal("customarmor")
							.executes(context -> {
								context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
										"[Parz] Use /pz customarmor <#RRGGBB> or /pz customarmor reset. Only leather armor can show a color."));
								return 1;
							})
							.then(ClientCommands.literal("reset").executes(context -> {
								net.parz.badge.ArmorColorConfig.reset();
								context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Your armor color was reset.")
										.withStyle(net.minecraft.ChatFormatting.GREEN));
								return 1;
							}))
							.then(ClientCommands.argument("color", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
								return armorColor(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "color"));
							})))
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

		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("main tick", client -> {
			ModUpdater.tick(client);
			if (screenToOpen != null) {
				Screen screen = screenToOpen.get();
				screenToOpen = null;
				client.gui.setScreen(screen);
			}
		}));

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
			{"pz nucleus", "Shows the Nucleus slot machine as a test (add 'alloy' for the jackpot)"},
			{"pz fishing", "Shows the fishing loot memory as a test (add jawbus, thunder or ragnarok)"},
			{"pz customname", "Choose how your name looks to other Parz users (menu, or /pz customname json <json>)"},
			{"pz customarmor", "Choose a color other Parz users see on your leather armor (/pz customarmor #RRGGBB, reset to clear it)"},
			{"pz update", "Installs the newest Parz version from GitHub"},
			{"pz help", "Shows this list"}
	};

	/** /pz debug [on|off]: only for the developer account. */
	private static int debugCommand(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, Boolean on) {
		if (!Debug.allowed()) {
			source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] /pz debug is only for the developer.")
					.withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		if (on != null) {
			Debug.set(on);
		}
		source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Debug mode is " + (Debug.isOn() ? "ON" : "OFF")
				+ (on == null ? " (use /pz debug on or /pz debug off)" : ""))
				.withStyle(Debug.isOn() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY));
		return 1;
	}

	/** /pz customname json <text component>: your name from a JSON text. */
	private static int nameJson(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String text) {
		net.parz.badge.BadgeConfig.Data data = new net.parz.badge.BadgeConfig.Data();
		data.json = true;
		data.jsonText = text;
		try {
			net.parz.badge.BadgeConfig.apply(data);
		} catch (IllegalArgumentException e) {
			source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] " + e.getMessage())
					.withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Saved. Your name now looks like this: ")
				.withStyle(net.minecraft.ChatFormatting.GREEN)
				.append(net.parz.badge.BadgeFormat.render(net.parz.badge.BadgeConfig.runs(),
						net.minecraft.client.Minecraft.getInstance().getUser().getName())));
		return 1;
	}

	/** /pz customarmor <#RRGGBB>: the color other Parz users see on your leather armor. */
	private static int armorColor(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String text) {
		try {
			net.parz.badge.ArmorColorConfig.apply(text);
		} catch (IllegalArgumentException e) {
			source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] " + e.getMessage())
					.withStyle(net.minecraft.ChatFormatting.RED));
			return 0;
		}
		source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Saved. Other Parz users now see this color on your leather armor.")
				.withStyle(net.minecraft.ChatFormatting.GREEN));
		return 1;
	}

	private static void sendHelp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		source.sendFeedback(net.minecraft.network.chat.Component.literal("[Parz] Commands (click one to run it):")
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		java.util.List<String[]> shown = new java.util.ArrayList<>(java.util.Arrays.asList(COMMANDS));
		if (Debug.allowed()) {
			shown.add(new String[]{"pz debug", "Developer: shows everything Parz does in the chat (/pz debug on | off)"});
		}
		for (String[] entry : shown) {
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
			DashboardData.saveBlocking();
		} catch (Exception e) {
			LOGGER.error("Could not save Parz data", e);
		}
	}
}
