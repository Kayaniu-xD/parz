package net.parz.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.parz.ParzClient;
import net.parz.config.ParzConfigDefinition;
import net.parz.gui.HudEditorScreen;
import net.parz.hud.HudTrackers;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dungeon map HUD. Hypixel gives you a "Magic Map" in the last hotbar slot while you are in a dungeon. It is a
 * normal 128x128 map: every room is a square of 16 pixels (18 on small floors), between two rooms is a gap of
 * 4 pixels that holds a door, or the room color if both rooms are one big room.
 *
 * Everything is read from those pixels: which rooms exist, their color (= type), whether they were entered
 * (unexplored rooms are grey/black) and their check mark in the middle (white = cleared, green = completed,
 * red cross = failed). Everything seen during the run is kept.
 */
public final class DungeonMap {
	// ---- the map item ----
	private static final int MAP_PIXELS = 128;
	private static final int HOTBAR_LAST = 8;
	private static final int DOOR_GAP = 4;
	private static final int MAX_CELLS = 6;
	private static final int MISSING_TICKS = 100; // map gone for 5 s = the run is over

	// raw map colors Hypixel uses
	private static final int C_ENTRANCE = 30;
	private static final int C_BLOOD = 18;
	private static final int C_PUZZLE = 66;
	private static final int C_TRAP = 62;
	private static final int C_FAIRY = 82;
	private static final int C_YELLOW = 74;
	private static final int C_UNEXPLORED = 85;
	private static final int C_UNKNOWN = 119;
	private static final int C_CHECK_WHITE = 34;
	private static final int C_CHECK_GREEN = 30;
	private static final int C_CROSS = 18;

	// ---- the drawing (gui pixels at size 1.0) ----
	private static final int ROOM = 24;     // edge of a room
	private static final int GAP = 6;       // between two rooms
	private static final int PITCH = ROOM + GAP;
	private static final int DOOR_W = 12;
	private static final int PAD = 8;
	private static final int FOOTER = 26;
	private static final int MIN_WIDTH = 150;

	private static final int PANEL = 0xF0161618;
	private static final int PANEL_EDGE = 0xFF3A3A42;
	private static final int TEXT_NAME = 0xFFA9C4FF;
	private static final int TEXT_DIM = 0xFF8E8E96;

	public enum State {
		UNEXPLORED, ENTERED, CLEARED, COMPLETE, FAILED
	}

	private static final class Room {
		final List<int[]> cells = new ArrayList<>(); // col, row
		int color; // raw map color of the room
		State state = State.ENTERED;
		String label = "";
	}

	/** What is in the gap between two rooms. */
	private record Link(int col, int row, boolean horizontal, boolean joined, int argb, int first, int last) {
	}

	// ---- run state ----
	private static boolean inDungeon = false;
	private static int missingTicks = 0;
	private static int mapId = Integer.MIN_VALUE;
	private static final byte[] seen = new byte[MAP_PIXELS * MAP_PIXELS]; // all colors ever seen in this run
	private static boolean calibrated = false;
	private static int size = 16;
	private static int pitch = size + DOOR_GAP;
	private static int offX = 0;
	private static int offY = 0;
	private static int cols = MAX_CELLS;
	private static int rows = MAX_CELLS;
	private static List<Room> roomList = new ArrayList<>();
	private static List<Link> linkList = new ArrayList<>();
	private static final List<float[]> markers = new ArrayList<>(); // map pixel x, y, 1 = you / 0 = others
	private static boolean loggedError = false;
	private static String lastProblem = "not started";

	private DungeonMap() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				if (inDungeon || calibrated) {
					reset();
				}
				DungeonInfo.reset();
				return;
			}
			int tick = client.player.tickCount;
			if (tick % 20 == 0 && SkyBlockDetector.featuresActive()) {
				try {
					DungeonInfo.update(client);
				} catch (RuntimeException e) {
					logOnce(e);
				}
			}
			if (tick % 5 == 0) {
				try {
					scan(client);
				} catch (RuntimeException e) {
					logOnce(e);
				}
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dungeon_map"),
				(graphics, deltaTracker) -> renderHud(graphics));
	}

	private static void logOnce(RuntimeException e) {
		lastProblem = e.toString();
		if (!loggedError) {
			loggedError = true;
			ParzClient.LOGGER.warn("Dungeon map error", e);
		}
	}

	/** True while you are in a dungeon run (the Magic Map is in your hotbar). */
	public static boolean inDungeon() {
		return inDungeon;
	}

	public static boolean enabled() {
		return ParzConfigDefinition.DUNGEON_MAP.isEnabled() && SkyBlockDetector.featuresActive();
	}

	private static void reset() {
		inDungeon = false;
		calibrated = false;
		missingTicks = 0;
		mapId = Integer.MIN_VALUE;
		java.util.Arrays.fill(seen, (byte) 0);
		roomList = new ArrayList<>();
		linkList = new ArrayList<>();
		markers.clear();
	}

	/** Lines for /parz dungeon: what the map scan sees right now. */
	public static List<String> debug(Minecraft client) {
		List<String> out = new ArrayList<>();
		out.add("features active: " + SkyBlockDetector.featuresActive() + " (on SkyBlock: " + SkyBlockDetector.isOnSkyBlock() + ")");
		out.add("dungeon map setting: " + ParzConfigDefinition.DUNGEON_MAP.isEnabled());
		out.add(DungeonInfo.describe());
		if (client.player != null) {
			for (int slot = 0; slot <= HOTBAR_LAST; slot++) {
				ItemStack stack = client.player.getInventory().getItem(slot);
				if (stack.is(Items.FILLED_MAP)) {
					MapId id = stack.get(DataComponents.MAP_ID);
					MapItemSavedData data = id != null && client.level != null ? client.level.getMapData(id) : null;
					out.add("slot " + (slot + 1) + ": map '" + stack.getHoverName().getString() + "' id=" + (id != null ? id.id() : "none")
							+ " data=" + (data != null));
				}
			}
		}
		out.add("map found: " + inDungeon + ", calibrated: " + calibrated + ", grid " + cols + "x" + rows
				+ ", rooms: " + roomList.size() + ", room size: " + size);
		out.add("last problem: " + lastProblem);
		return out;
	}

	// ======================================================================================
	// Scanning
	// ======================================================================================

	/** The Magic Map: a filled map in the last hotbar slot, or any hotbar slot if it is called "Magic Map". */
	private static ItemStack findMap(Minecraft client) {
		for (int i = 0; i <= HOTBAR_LAST; i++) {
			int slot = i == 0 ? HOTBAR_LAST : i - 1; // last slot first
			ItemStack stack = client.player.getInventory().getItem(slot);
			if (stack.is(Items.FILLED_MAP) && stack.get(DataComponents.MAP_ID) != null) {
				boolean named = stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("magic map");
				if (named || (slot == HOTBAR_LAST && DungeonInfo.inCatacombs())) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private static void scan(Minecraft client) {
		if (!SkyBlockDetector.featuresActive()) {
			if (inDungeon) {
				reset();
			}
			lastProblem = "Parz is switched off outside SkyBlock";
			return;
		}
		ItemStack stack = findMap(client);
		MapId id = stack.isEmpty() ? null : stack.get(DataComponents.MAP_ID);
		if (id == null) {
			lastProblem = "no Magic Map in the hotbar";
			if (inDungeon && ++missingTicks * 5 >= MISSING_TICKS) {
				reset();
			}
			return;
		}
		MapItemSavedData data = client.level.getMapData(id);
		if (data == null) {
			lastProblem = "map data not received yet";
			return;
		}
		if (id.id() != mapId) {
			reset(); // a new map = a new run
			mapId = id.id();
		}
		inDungeon = true;
		missingTicks = 0;
		lastProblem = "none";

		// remember every pixel that is drawn right now
		byte[] colors = data.colors;
		int length = Math.min(colors.length, seen.length);
		for (int i = 0; i < length; i++) {
			if (colors[i] != 0) {
				seen[i] = colors[i];
			}
		}
		if (!calibrated && !calibrate()) {
			lastProblem = "map has no start room yet";
		}
		if (calibrated) {
			build();
		}

		markers.clear();
		for (MapDecoration decoration : data.getDecorations()) {
			boolean you = decoration.type().is(MapDecorationTypes.PLAYER);
			if (you || decoration.type().is(MapDecorationTypes.FRAME)) {
				markers.add(new float[]{(decoration.x() + 128) / 2f, (decoration.y() + 128) / 2f, you ? 1f : 0f});
			}
		}
	}

	/** Finds the green start room (or any big square): its top left corner and edge give the grid of all rooms. */
	private static boolean calibrate() {
		for (int pass = 0; pass < 2; pass++) {
			for (int y = 0; y < MAP_PIXELS; y++) {
				for (int x = 0; x < MAP_PIXELS; x++) {
					int color = pixel(x, y);
					if (color == 0 || (pass == 0 && color != C_ENTRANCE)) {
						continue;
					}
					if (pixel(x - 1, y) == color || pixel(x, y - 1) == color) {
						continue; // not a top left corner
					}
					int run = 0;
					while (pixel(x + run, y) == color) {
						run++;
					}
					int height = 0;
					while (pixel(x, y + height) == color) {
						height++;
					}
					if (run < 14 || height < 14) {
						continue; // a door or a thin line
					}
					size = run >= 18 && height >= 18 ? 18 : 16;
					pitch = size + DOOR_GAP;
					offX = x % pitch;
					offY = y % pitch;
					cols = cells(offX);
					rows = cells(offY);
					calibrated = true;
					return true;
				}
			}
		}
		return false;
	}

	private static int cells(int offset) {
		return Math.max(1, Math.min(MAX_CELLS, (MAP_PIXELS - offset - size) / pitch + 1));
	}

	private static int pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= MAP_PIXELS || y >= MAP_PIXELS) {
			return 0;
		}
		return seen[y * MAP_PIXELS + x] & 0xFF;
	}

	private static int cellX(int col) {
		return offX + col * pitch;
	}

	private static int cellY(int row) {
		return offY + row * pitch;
	}

	/** Reads rooms, doors, states from the pixels. */
	private static void build() {
		int[][] color = new int[rows][cols];
		int[][] room = new int[rows][cols]; // index into rooms or -1
		List<Link> links = new ArrayList<>();
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				color[row][col] = pixel(cellX(col) + 1, cellY(row) + 1);
				room[row][col] = -1;
			}
		}
		// union find over the cells joined by a full width gap
		int[] parent = new int[rows * cols];
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				if (color[row][col] == 0) {
					continue;
				}
				if (col + 1 < cols && color[row][col + 1] != 0) {
					Link link = measureGap(col, row, true);
					if (link != null) {
						links.add(link);
						if (link.joined()) {
							union(parent, row * cols + col, row * cols + col + 1);
						}
					}
				}
				if (row + 1 < rows && color[row + 1][col] != 0) {
					Link link = measureGap(col, row, false);
					if (link != null) {
						links.add(link);
						if (link.joined()) {
							union(parent, row * cols + col, (row + 1) * cols + col);
						}
					}
				}
			}
		}

		List<Room> result = new ArrayList<>();
		int[] indexOfRoot = new int[parent.length];
		java.util.Arrays.fill(indexOfRoot, -1);
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				if (color[row][col] == 0) {
					continue;
				}
				int root = find(parent, row * cols + col);
				if (indexOfRoot[root] < 0) {
					indexOfRoot[root] = result.size();
					Room created = new Room();
					created.color = color[row][col];
					result.add(created);
				}
				result.get(indexOfRoot[root]).cells.add(new int[]{col, row});
			}
		}
		for (Room r : result) {
			r.state = stateOf(r);
			r.label = labelOf(r);
		}
		roomList = result;
		linkList = links;
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		parent[find(parent, a)] = find(parent, b);
	}

	/** Measures the 4 pixel gap to the right of (col,row) or below it; null if nothing is drawn in it. */
	private static Link measureGap(int col, int row, boolean horizontal) {
		int lineX = horizontal ? cellX(col) + size + DOOR_GAP / 2 : cellX(col);
		int lineY = horizontal ? cellY(row) : cellY(row) + size + DOOR_GAP / 2;
		int first = -1;
		int last = -1;
		for (int i = 0; i < size; i++) {
			int c = horizontal ? pixel(lineX, lineY + i) : pixel(lineX + i, lineY);
			if (c != 0) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first < 0) {
			return null;
		}
		int mid = (first + last) / 2;
		int c = horizontal ? pixel(lineX, lineY + mid) : pixel(lineX + mid, lineY);
		boolean joined = last - first + 1 >= size - 2;
		return new Link(col, row, horizontal, joined, argb(c), first, last);
	}

	private static State stateOf(Room room) {
		if (room.color == C_UNEXPLORED || room.color == C_UNKNOWN) {
			return State.UNEXPLORED;
		}
		State state = State.ENTERED;
		for (int[] cell : room.cells) {
			int middle = pixel(cellX(cell[0]) + size / 2, cellY(cell[1]) + size / 2);
			if (middle == C_CHECK_WHITE) {
				return State.CLEARED;
			}
			if (middle == C_CHECK_GREEN && room.color != C_ENTRANCE) {
				return State.COMPLETE;
			}
			if (middle == C_CROSS && room.color != C_BLOOD) {
				state = State.FAILED;
			}
		}
		if (room.color == C_ENTRANCE) {
			return State.COMPLETE;
		}
		return state;
	}

	private static String labelOf(Room room) {
		return switch (room.color) {
			case C_ENTRANCE -> "Entrance";
			case C_BLOOD -> "Blood";
			case C_PUZZLE -> "Puzzle";
			case C_TRAP -> "Trap";
			case C_FAIRY -> "Fairy";
			case C_YELLOW -> "Mini Boss";
			default -> "";
		};
	}

	// ======================================================================================
	// Drawing
	// ======================================================================================

	/** Size of the map box at scale 1.0. */
	public static int width() {
		return Math.max(MIN_WIDTH, cols * PITCH - GAP + 2 * PAD);
	}

	public static int height() {
		return rows * PITCH - GAP + 2 * PAD + FOOTER;
	}

	private static void renderHud(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled() || !(inDungeon || DungeonInfo.inCatacombs()) || client.gui.screen() instanceof HudEditorScreen) {
			return;
		}
		int[] pos = HudTrackers.position(HudTrackers.MAP_KEY, 0,
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		HudTrackers.draw(graphics, client.font, HudTrackers.MAP_KEY, pos[0], pos[1]);
	}

	/** Draws the map box (x, y = top left corner) in its normal size. preview = sample rooms when there is no run. */
	public static void drawUnscaled(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean preview) {
		int width = width();
		int height = height();
		roundRect(graphics, x, y, x + width, y + height, PANEL_EDGE, 4);
		roundRect(graphics, x + 1, y + 1, x + width - 1, y + height - 1, PANEL, 3);

		int mapW = cols * PITCH - GAP;
		int originX = x + (width - mapW) / 2;
		int originY = y + PAD;

		boolean sample = preview && !calibrated;
		if (sample) {
			drawSample(graphics, font, originX, originY);
		} else if (!calibrated) {
			String message = inDungeon ? "Scanning the map..." : "No Magic Map in your hotbar";
			graphics.text(font, message, x + (width - font.width(message)) / 2, originY + 20, TEXT_DIM, true);
		} else {
			drawRooms(graphics, font, originX, originY);
		}
		drawFooter(graphics, font, x, y + height - FOOTER, width);
	}

	private static void drawRooms(GuiGraphicsExtractor graphics, Font font, int originX, int originY) {
		boolean names = ParzConfigDefinition.DUNGEON_MAP_NAMES.isEnabled();
		boolean status = ParzConfigDefinition.DUNGEON_MAP_STATUS.isEnabled();

		// rooms (a big room = several cells that are joined)
		for (Room room : roomList) {
			int fill = room.state == State.UNEXPLORED ? dim(argb(room.color), 0.55f) : argb(room.color);
			for (int[] cell : room.cells) {
				int rx = originX + cell[0] * PITCH;
				int ry = originY + cell[1] * PITCH;
				roundRect(graphics, rx, ry, rx + ROOM, ry + ROOM, fill, 3);
				if (status && room.state == State.ENTERED) {
					graphics.outline(rx, ry, ROOM, ROOM, 0x90FFFFFF);
				}
			}
		}
		// what is between the rooms: one big room or a door
		for (Link link : linkList) {
			int rx = originX + link.col() * PITCH;
			int ry = originY + link.row() * PITCH;
			if (link.joined()) {
				if (link.horizontal()) {
					graphics.fill(rx + ROOM, ry + 1, rx + PITCH, ry + ROOM - 1, link.argb());
				} else {
					graphics.fill(rx + 1, ry + ROOM, rx + ROOM - 1, ry + PITCH, link.argb());
				}
			} else if (link.horizontal()) {
				graphics.fill(rx + ROOM, ry + (ROOM - DOOR_W) / 2, rx + PITCH, ry + (ROOM + DOOR_W) / 2, link.argb());
			} else {
				graphics.fill(rx + (ROOM - DOOR_W) / 2, ry + ROOM, rx + (ROOM + DOOR_W) / 2, ry + PITCH, link.argb());
			}
		}
		// the middle of a 2x2 room
		for (Room room : roomList) {
			if (room.cells.size() == 4) {
				int minCol = Integer.MAX_VALUE;
				int minRow = Integer.MAX_VALUE;
				for (int[] cell : room.cells) {
					minCol = Math.min(minCol, cell[0]);
					minRow = Math.min(minRow, cell[1]);
				}
				int rx = originX + minCol * PITCH + ROOM;
				int ry = originY + minRow * PITCH + ROOM;
				graphics.fill(rx, ry, rx + GAP, ry + GAP, argb(room.color));
			}
		}

		// names and state icons
		for (Room room : roomList) {
			float[] center = labelCenter(room);
			int cx = originX + Math.round(center[0] * PITCH + ROOM / 2f);
			int cy = originY + Math.round(center[1] * PITCH + ROOM / 2f);
			boolean hasName = names && !room.label.isEmpty();
			if (hasName) {
				drawSmall(graphics, font, room.label, cx, cy - (status ? 7 : 3), TEXT_NAME, 0.55f);
			}
			if (status) {
				int iconY = cy + (hasName ? 4 : 0);
				switch (room.state) {
					case UNEXPLORED -> drawSmall(graphics, font, "?", cx, iconY - 3, 0xFFB0B0B8, 1.0f);
					case CLEARED -> drawCheck(graphics, cx, iconY, 0xFFFFFFFF);
					case COMPLETE -> drawCheck(graphics, cx, iconY, 0xFF3DDC4F);
					case FAILED -> drawCross(graphics, cx, iconY, 0xFFFF4040);
					default -> {
					}
				}
			}
		}

		// players
		for (float[] marker : markers) {
			float cellsX = (marker[0] - offX) / pitch;
			float cellsY = (marker[1] - offY) / pitch;
			int mx = originX + Math.round(cellsX * PITCH);
			int my = originY + Math.round(cellsY * PITCH);
			graphics.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
			graphics.fill(mx - 2, my - 2, mx + 2, my + 2, marker[2] > 0 ? 0xFFFFFFFF : 0xFF55FF55);
		}
	}

	/** Where the name of a room goes, in cell coordinates (a big room: its middle; a bent one: its nearest cell). */
	private static float[] labelCenter(Room room) {
		float avgCol = 0;
		float avgRow = 0;
		for (int[] cell : room.cells) {
			avgCol += cell[0];
			avgRow += cell[1];
		}
		avgCol /= room.cells.size();
		avgRow /= room.cells.size();
		int roundCol = Math.round(avgCol);
		int roundRow = Math.round(avgRow);
		for (int[] cell : room.cells) {
			if (cell[0] == roundCol && cell[1] == roundRow) {
				return new float[]{avgCol, avgRow};
			}
		}
		int[] best = room.cells.getFirst();
		float bestDistance = Float.MAX_VALUE;
		for (int[] cell : room.cells) {
			float d = (cell[0] - avgCol) * (cell[0] - avgCol) + (cell[1] - avgRow) * (cell[1] - avgRow);
			if (d < bestDistance) {
				bestDistance = d;
				best = cell;
			}
		}
		return new float[]{best[0], best[1]};
	}

	private static void drawFooter(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		graphics.fill(x + PAD, y, x + width - PAD, y + 1, 0x40FFFFFF);
		String[] captions = {"SCORE", "SECRETS", "CRYPTS", "DEATHS"};
		String secrets = DungeonInfo.secrets() < 0 ? "-"
				: DungeonInfo.secretsTotal() > 0 ? DungeonInfo.secrets() + "/" + DungeonInfo.secretsTotal()
				: String.valueOf(DungeonInfo.secrets());
		String[] values = {
				DungeonInfo.score() < 0 ? "-" : String.valueOf(DungeonInfo.score()),
				secrets,
				DungeonInfo.crypts() < 0 ? "-" : String.valueOf(DungeonInfo.crypts()),
				DungeonInfo.deaths() < 0 ? "-" : String.valueOf(DungeonInfo.deaths())
		};
		int[] colors = {0xFFB794F6, 0xFFFFFFFF, 0xFFFFFFFF, DungeonInfo.deaths() > 0 ? 0xFFFF6B6B : 0xFFFFFFFF};
		int columnW = (width - 2 * PAD) / 4;
		for (int i = 0; i < 4; i++) {
			int cx = x + PAD + columnW * i + columnW / 2;
			drawSmall(graphics, font, captions[i], cx, y + 4, TEXT_DIM, 0.55f);
			drawSmall(graphics, font, values[i], cx, y + 13, colors[i], 0.9f);
		}
	}

	/** A few sample rooms so you can place the map with /parz hud before a run. */
	private static void drawSample(GuiGraphicsExtractor graphics, Font font, int originX, int originY) {
		int[][] layout = {{3, 1, 5, 0}, {2, 2, 0, 4}, {0, 6, 2, 7}};
		int[] raw = {0, C_ENTRANCE, 63, C_PUZZLE, C_FAIRY, C_TRAP, C_UNEXPLORED, C_BLOOD};
		for (int row = 0; row < layout.length; row++) {
			for (int col = 0; col < layout[row].length; col++) {
				int type = layout[row][col];
				if (type == 0) {
					continue;
				}
				int fill = argb(raw[type]);
				int rx = originX + col * PITCH;
				int ry = originY + row * PITCH;
				roundRect(graphics, rx, ry, rx + ROOM, ry + ROOM, type == 6 ? dim(fill, 0.55f) : fill, 3);
				String label = switch (raw[type]) {
					case C_ENTRANCE -> "Entrance";
					case C_PUZZLE -> "Puzzle";
					case C_FAIRY -> "Fairy";
					case C_TRAP -> "Trap";
					case C_BLOOD -> "Blood";
					default -> "";
				};
				if (!label.isEmpty()) {
					drawSmall(graphics, font, label, rx + ROOM / 2, ry + ROOM / 2 - 7, TEXT_NAME, 0.55f);
				}
				if (type == 2) {
					drawCheck(graphics, rx + ROOM / 2, ry + ROOM / 2, 0xFFFFFFFF);
				} else if (type == 6) {
					drawSmall(graphics, font, "?", rx + ROOM / 2, ry + ROOM / 2 - 3, 0xFFB0B0B8, 1.0f);
				} else if (type == 1) {
					drawCheck(graphics, rx + ROOM / 2, ry + ROOM / 2 + 4, 0xFF3DDC4F);
				}
			}
		}
		drawSmall(graphics, font, "Move me with /parz hud", originX + (cols * PITCH - GAP) / 2, originY + 3 * PITCH + 2, TEXT_DIM, 0.6f);
	}

	// ---- small drawing helpers ----

	/** Text centered on (cx, y) at a smaller size. */
	private static void drawSmall(GuiGraphicsExtractor graphics, Font font, String text, int cx, int y, int color, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx - font.width(text) * scale / 2f, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
	}

	private static void drawCheck(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		int[][] points = {{-4, 0}, {-3, 1}, {-2, 2}, {-1, 3}, {0, 2}, {1, 1}, {2, 0}, {3, -1}, {4, -2}, {5, -3}};
		for (int[] p : points) {
			graphics.fill(cx + p[0] - 1, cy + p[1] - 1, cx + p[0] + 2, cy + p[1] + 2, 0xB0000000);
		}
		for (int[] p : points) {
			graphics.fill(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, color);
			graphics.fill(cx + p[0], cy + p[1] - 1, cx + p[0] + 1, cy + p[1], color);
		}
	}

	private static void drawCross(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		for (int i = -3; i <= 3; i++) {
			graphics.fill(cx + i - 1, cy + i - 1, cx + i + 2, cy + i + 2, 0xB0000000);
			graphics.fill(cx + i - 1, cy - i - 1, cx + i + 2, cy - i + 2, 0xB0000000);
		}
		for (int i = -3; i <= 3; i++) {
			graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
			graphics.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
		}
	}

	/** A rectangle with cut corners. */
	private static void roundRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color, int radius) {
		int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
		graphics.fill(x1, y1 + r, x2, y2 - r, color);
		for (int i = 0; i < r; i++) {
			int inset = Math.max(1, r - 1 - i);
			graphics.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
			graphics.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
	}

	private static int dim(int argb, float factor) {
		int r = Math.round(((argb >> 16) & 0xFF) * factor);
		int g = Math.round(((argb >> 8) & 0xFF) * factor);
		int b = Math.round((argb & 0xFF) * factor);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int argb(int packedId) {
		return packedId == 0 ? 0 : MapColor.getColorFromPackedId(packedId);
	}
}
