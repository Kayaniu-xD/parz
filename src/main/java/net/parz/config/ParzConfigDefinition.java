package net.parz.config;

import net.parz.config.ConfigModel.Category;
import net.parz.config.ConfigModel.Toggle;

import java.util.ArrayList;
import java.util.List;

/**
 * The categories and options shown in the /parz menu.
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

	// ---- Dashboard & HUD ---------------------------------------------------------
	public static final Category DASHBOARD = category("Dashboard", "SkyBlock dashboard (/parz-dash) and HUD trackers (/parz hud).");

	public static final Toggle SKILL_XP_PER_HOUR = DASHBOARD.toggle(
			"dashboard.skill_xp_per_hour",
			"Skill XP per Hour",
			"Shows how much skill XP you gain per hour in the dashboard and in the HUD.",
			true);

	public static final Toggle HUD_TRACKERS = DASHBOARD.toggle(
			"dashboard.hud_trackers",
			"HUD Trackers",
			"Shows the skills and collections you pinned in /parz-dash on your screen. Move them with /parz hud.",
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

	private ParzConfigDefinition() {
	}

	private static Category category(String name, String description) {
		Category category = new Category(name, description, null);
		CATEGORIES.add(category);
		return category;
	}
}
