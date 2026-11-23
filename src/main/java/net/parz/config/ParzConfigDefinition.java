package net.parz.config;

import net.parz.config.ConfigModel.Category;
import net.parz.config.ConfigModel.Toggle;

import java.util.ArrayList;
import java.util.List;

/**
 * The categories and options shown in the /pz menu.
 */
public final class ParzConfigDefinition {
	public static final List<Category> CATEGORIES = new ArrayList<>();

	// ---- General ---------------------------------------------------------------
	public static final Category GENERAL = category("General", "General features.");

	public static final Toggle ONLY_SKYBLOCK = GENERAL.toggle(
			"general.only_skyblock",
			"Nur auf SkyBlock",
			"Alle Funktionen von Parz sind nur aktiv, wenn du auf Hypixel SkyBlock bist.",
			true);

	public static final Toggle REMOVE_BLINDNESS = GENERAL.toggle(
			"general.remove_blindness",
			"Remove Blindness",
			"Removes the visual blindness effect (black fog). The effect itself stays, so you still cannot sprint.",
			false);

	public static final Toggle MENU_BUTTON = GENERAL.toggle(
			"general.menu_button",
			"SkyBlock Menu Button",
			"Replaces the top left glass pane of the /skyblockmenu with a button that opens the Parz menu.",
			true);

	public static final Toggle PETS_MENU = GENERAL.toggle(
			"general.pets_menu",
			"Custom Pets Menu",
			"Shows the /pets menu in the Parz book design: pet list on the left, details and buttons on the right.",
			true);

	public static final Toggle NO_HURT_SHAKE = GENERAL.toggle(
			"general.no_hurt_shake",
			"Remove Damage Shake",
			"Removes the screen shaking (the camera tilt) when you take damage. The red flash stays, so you still see that you were hit.",
			false);

	public static final Toggle TITLE_VANILLA = GENERAL.toggle(
			"general.title_vanilla",
			"Vanilla Window Title",
			"Forces the title of the game window to the vanilla one (\"Minecraft 26.2\"), also when another mod or client renames the window. Only one of the two window title settings can be on.",
			false);

	public static final Toggle TITLE_FABRIC = GENERAL.toggle(
			"general.title_fabric",
			"Fabric Window Title",
			"Forces the title of the game window to the one of a modded game (\"Minecraft* 26.2\", the star marks mods like Fabric), also when another mod or client renames the window. On by default. Only one of the two window title settings can be on.",
			true);

	static {
		TITLE_VANILLA.exclusiveWith = TITLE_FABRIC;
		TITLE_FABRIC.exclusiveWith = TITLE_VANILLA;
	}

	// ---- Dashboard & HUD ---------------------------------------------------------
	public static final Category DASHBOARD = category("Dashboard", "SkyBlock dashboard (/pz dash) and HUD trackers (/pz hud).");

	public static final Toggle SKILL_XP_PER_HOUR = DASHBOARD.toggle(
			"dashboard.skill_xp_per_hour",
			"Skill XP per Hour",
			"Shows how much skill XP you gain per hour in the dashboard and in the HUD.",
			true);

	public static final Toggle HUD_TRACKERS = DASHBOARD.toggle(
			"dashboard.hud_trackers",
			"HUD Trackers",
			"Shows the skills and collections you pinned in /pz dash on your screen. Move them with /pz hud.",
			true);

	public static final Toggle LIVE_COLLECTIONS = DASHBOARD.toggle(
			"dashboard.live_collections",
			"Live Collections",
			"Raises your collections while you play: items you pick up and items that go into your sacks (read from the [Sacks] chat message). Enchanted items count as what they are made of.",
			true);

	public static final Toggle SUBTRACT_COLLECTIONS = DASHBOARD.toggle(
			"dashboard.subtract_collections",
			"Subtract Lost Items",
			"Lowers a collection again when items disappear from your inventory or sacks. Items sold to the Bazaar or an NPC and items used in menus are not subtracted.",
			true);

	// ---- Dungeons --------------------------------------------------------------------
	public static final Category DUNGEONS = category("Dungeons", "Dungeon tools for The Catacombs.");

	public static final Toggle DUNGEON_MAP = DUNGEONS.toggle(
			"dungeons.map",
			"Dungeon Map",
			"Shows every room of the dungeon on your screen from the start, found by reading the blocks that are already loaded in your game (nothing is sent to the server). The entrance is always at the bottom. Move and resize it with /pz hud.",
			true);

	public static final Toggle DUNGEON_MAP_NAMES = DUNGEONS.toggle(
			"dungeons.map_names",
			"Room Names",
			"Writes the name and the number of secrets on every room. Rooms that are not recognised show their type or a ?.",
			true);

	public static final Toggle DUNGEON_MAP_STATUS = DUNGEONS.toggle(
			"dungeons.map_status",
			"Room Status",
			"Rooms you have not entered are darker. A white check mark = cleared, green = completed, red cross = failed (read from the Magic Map if you carry it).",
			true);

	public static final Toggle DRAGON_CIRCLES = DUNGEONS.toggle(
			"dungeons.dragon_circles",
			"M7 Dragon Arrow Stack",
			"Only in the Master Mode 7 boss fight (dragon phase): while a dragon spawns, a cyan ring shows where to aim your bow for the arrow stack (the arrow drop is included). Same positions and calculation as the Wither Dragons feature of NoammAddons.",
			true);

	// ---- Crystal Hollows -----------------------------------------------------------------
	public static final Category HOLLOWS = category("Crystal Hollows", "Tools for the Crystal Hollows.");

	public static final Toggle NUCLEUS_SLOT = HOLLOWS.toggle(
			"hollows.nucleus_slot",
			"Nucleus Slot Machine",
			"When you place all crystals and get the loot bundle of the Crystal Nucleus, a slot machine spins on your screen and stops on the rarest item you got. With a Divan's Alloy all three reels show the alloy: jackpot. Try it with /pz nucleus.",
			true);

	// ---- Fishing -------------------------------------------------------------------------
	public static final Category FISHING = category("Fishing", "Tools for fishing.");

	public static final Toggle FISHING_PUZZLE = FISHING.toggle(
			"fishing.puzzle",
			"Loot Memory",
			"When Lord Jawbus, Thunder or Ragnarok is dead, a memory board shows all the loot it can drop. The cards of everything you did not get are turned around, your drops stay open. Try it with /pz fishing.",
			true);

	// ---- Update --------------------------------------------------------------------
	public static final Category UPDATE = updateCategory("Update", "Check for a new Parz version and install it from GitHub.");

	public static final Toggle UPDATE_CHECK = UPDATE.toggle(
			"update.check_on_start",
			"Check on Start",
			"Looks for a new Parz version on GitHub every time the game starts.",
			true);

	public static final Toggle UPDATE_CHAT = UPDATE.toggle(
			"update.chat_message",
			"Chat Message",
			"Shows a message in the chat (with a click to update) when a new version is available.",
			true);

	private ParzConfigDefinition() {
	}

	private static Category updateCategory(String name, String description) {
		Category category = category(name, description);
		category.updatePage = true;
		return category;
	}

	private static Category category(String name, String description) {
		Category category = new Category(name, description, null);
		CATEGORIES.add(category);
		return category;
	}
}
