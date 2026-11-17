package net.parz.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facts about the current dungeon run that are written on the sidebar and in the tab list: the floor, the score,
 * secrets, crypts and deaths. Read once per second.
 */
public final class DungeonInfo {
	private static final Pattern FLOOR = Pattern.compile("\\((E|[FM]\\d)\\)");
	private static final Pattern CLEARED = Pattern.compile("Cleared:\\s*(\\d+)%\\s*(?:\\((\\d+)\\))?");
	private static final Pattern SECRETS = Pattern.compile("Secrets Found:\\s*([\\d.,]+)(%?)");
	private static final Pattern CRYPTS = Pattern.compile("Crypts:\\s*(\\d+)");
	private static final Pattern DEATHS = Pattern.compile("Deaths:\\s*(\\d+)");

	private static boolean inCatacombs = false;
	private static String floor = "";
	private static int cleared = -1;
	private static int score = -1;
	private static int secrets = -1;
	private static double secretsPercent = -1;
	private static int crypts = -1;
	private static int deaths = -1;

	private DungeonInfo() {
	}

	public static boolean inCatacombs() {
		return inCatacombs;
	}

	/** "F7", "M7", "E" or "" if unknown. */
	public static String floor() {
		return floor;
	}

	/** True on floor 7 or master mode 7. */
	public static boolean isFloor7() {
		return floor.equals("F7") || floor.equals("M7");
	}

	public static int score() {
		return score;
	}

	public static int secrets() {
		return secrets;
	}

	/** Total secrets of the dungeon, worked out from "found" and its percentage; -1 if not known yet. */
	public static int secretsTotal() {
		if (secrets > 0 && secretsPercent > 0) {
			return (int) Math.round(secrets * 100.0 / secretsPercent);
		}
		return -1;
	}

	public static int crypts() {
		return crypts;
	}

	public static int deaths() {
		return deaths;
	}

	public static void reset() {
		inCatacombs = false;
		floor = "";
		cleared = score = secrets = crypts = deaths = -1;
		secretsPercent = -1;
	}

	public static void update(Minecraft client) {
		if (client.level == null) {
			reset();
			return;
		}
		boolean catacombs = false;
		String foundFloor = "";
		int foundScore = -1;
		for (String line : sidebar(client)) {
			if (line.contains("Catacombs")) {
				catacombs = true;
				Matcher m = FLOOR.matcher(line);
				if (m.find()) {
					foundFloor = m.group(1);
				}
			}
			Matcher m = CLEARED.matcher(line);
			if (m.find()) {
				cleared = Integer.parseInt(m.group(1));
				if (m.group(2) != null) {
					foundScore = Integer.parseInt(m.group(2));
				}
			}
		}
		inCatacombs = catacombs;
		if (!catacombs) {
			reset();
			return;
		}
		if (!foundFloor.isEmpty()) {
			floor = foundFloor;
		}
		score = foundScore;

		int foundSecrets = -1;
		double percent = -1;
		for (String line : tabList(client)) {
			Matcher m = SECRETS.matcher(line);
			if (m.find()) {
				try {
					double value = Double.parseDouble(m.group(1).replace(",", ""));
					if (m.group(2).isEmpty()) {
						foundSecrets = (int) value;
					} else {
						percent = value;
					}
				} catch (NumberFormatException ignored) {
					// not a number, skip the line
				}
			}
			m = CRYPTS.matcher(line);
			if (m.find()) {
				crypts = Integer.parseInt(m.group(1));
			}
			m = DEATHS.matcher(line);
			if (m.find()) {
				deaths = Integer.parseInt(m.group(1));
			}
		}
		if (foundSecrets >= 0) {
			secrets = foundSecrets;
		}
		if (percent >= 0) {
			secretsPercent = percent;
		}
	}

	/** The text lines of the sidebar (colors removed). */
	public static List<String> sidebar(Minecraft client) {
		List<String> lines = new ArrayList<>();
		if (client.level == null) {
			return lines;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return lines;
		}
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			String owner = entry.owner();
			if (owner.startsWith("#")) {
				continue; // hidden entry
			}
			PlayerTeam team = scoreboard.getPlayersTeam(owner);
			Component line = PlayerTeam.formatNameForTeam(team, Component.literal(owner));
			lines.add(strip(line.getString()));
		}
		return lines;
	}

	private static List<String> tabList(Minecraft client) {
		List<String> lines = new ArrayList<>();
		if (client.getConnection() == null) {
			return lines;
		}
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name != null) {
				lines.add(strip(name.getString()));
			}
		}
		return lines;
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "").trim();
	}

	public static String describe() {
		return "catacombs=" + inCatacombs + " floor=" + (floor.isEmpty() ? "?" : floor) + " score=" + score
				+ " secrets=" + secrets + "/" + secretsTotal() + " crypts=" + crypts + " deaths=" + deaths
				+ " cleared=" + cleared + "%";
	}
}
