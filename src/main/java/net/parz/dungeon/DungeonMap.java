package net.parz.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Dungeon map HUD. The rooms come from scanning the world that is already loaded on your client (see
 * DungeonScanner), so every room is shown right from the start, with its name and secret count. The map is
 * turned so that the entrance is always at the bottom.
 *
 * Which rooms you have entered comes from your own position. If you carry the Magic Map, its check marks
 * (read only) tell if a room is cleared, completed or failed.
 */
public final class DungeonMap {
	// ---- the Magic Map item (only used for the check marks) ----
	private static final int MAP_PIXELS = 128;
	private static final int HOTBAR_LAST = 8;
	private static final int DOOR_GAP = 4;
	private static final int MAX_CELLS = 6;
	private static final int C_ENTRANCE = 30;
	private static final int C_BLOOD = 18;
	private static final int C_UNEXPLORED = 85;
	private static final int C_UNKNOWN = 119;
	private static final int C_CHECK_WHITE = 34;
	private static final int C_CHECK_GREEN = 30;
	private static final int C_CROSS = 18;

	// ---- the drawing (gui pixels at size 1.0) ----
	private static final int ROOM = 24;
	private static final int GAP = 6;
	private static final int PITCH = ROOM + GAP;
	private static final int DOOR_W = 10;
	private static final int ROOM_RADIUS = 5;
	private static final int PAD = 8;
	private static final int FOOTER = 26;
	private static final int MIN_WIDTH = 150;

	private static final int PANEL = 0xF0161618;
	private static final int PANEL_EDGE = 0xFF3A3A42;
	private static final int TEXT_NAME = 0xFFA9C4FF;
	private static final int TEXT_DIM = 0xFF8E8E96;
	private static final int DIM_UNEXPLORED = 100; // percent brightness of a room you have not entered

	public enum State {
		UNEXPLORED, ENTERED, CLEARED, COMPLETE, FAILED
	}

	/** A room as it is drawn (cells already turned so that the entrance is at the bottom). */
	private static final class View {
		final List<int[]> cells = new ArrayList<>();     // drawn col, row
		final List<int[]> worldCells = new ArrayList<>(); // col, row in the dungeon
		String name = "";
		String type = "unknown";
		int secrets = -1;
		State state = State.UNEXPLORED;
	}

	private record DoorView(int c1, int r1, int c2, int r2, int type) {
	}

	// ---- run state ----
	private static final DungeonScanner scanner = new DungeonScanner();
	private static boolean wasInCatacombs = false;
	private static final boolean[] visited = new boolean[36];
	private static int[] startCell = null;
	private static List<View> views = new ArrayList<>();
	private static List<DoorView> doorViews = new ArrayList<>();
	private static int viewCols = 5;
	private static int viewRows = 5;
	private static int rotation = 0;    // 0 = as is, 1 = turned 90 degrees clockwise, 2 = 180, 3 = counter clockwise
	private static int minCol = 0;
	private static int minRow = 0;
	private static int boxCols = 1;
	private static int boxRows = 1;
	private static boolean mapAligned = false;
	private static boolean loggedError = false;
	private static String lastProblem = "none";

	// Magic Map data
	private static boolean mapFound = false;
	private static int mapId = Integer.MIN_VALUE;
	private static final byte[] seen = new byte[MAP_PIXELS * MAP_PIXELS];
	private static boolean calibrated = false;
	private static int size = 16;
	private static int pitch = size + DOOR_GAP;
	private static int offX = 0;
	private static int offY = 0;
	private static int anchorCol = 0;
	private static int anchorRow = 0;
	private static int mapCols = MAX_CELLS;
	private static int mapRows = MAX_CELLS;

	private DungeonMap() {
	}

	public static void register() {
		RoomDatabase.load();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				tick(client);
			} catch (RuntimeException e) {
				lastProblem = e.toString();
				if (!loggedError) {
					loggedError = true;
					ParzClient.LOGGER.warn("Dungeon map error", e);
				}
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dungeon_map"),
				net.parz.Debug.hud("dungeon map", (graphics, deltaTracker) -> renderHud(graphics)));
	}

	/** True while you are in a Catacombs run. */
	public static boolean inDungeon() {
		return DungeonInfo.inCatacombs();
	}

	public static boolean enabled() {
		return ParzConfigDefinition.DUNGEON_MAP.isEnabled() && SkyBlockDetector.featuresActive();
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			DungeonInfo.reset();
			if (wasInCatacombs) {
				resetRun();
			}
			wasInCatacombs = false;
			return;
		}
		int tick = client.player.tickCount;
		if (tick % 20 == 0 && SkyBlockDetector.featuresActive()) {
			DungeonInfo.update(client);
		}
		boolean inCatacombs = DungeonInfo.inCatacombs() && SkyBlockDetector.featuresActive();
		if (inCatacombs != wasInCatacombs) {
			net.parz.Debug.log("dungeon", () -> inCatacombs ? "in a dungeon: " + DungeonInfo.describe() : "left the dungeon, map reset");
			resetRun(); // a new run starts, or the old one is over
			wasInCatacombs = inCatacombs;
		}
		if (!inCatacombs || !ParzConfigDefinition.DUNGEON_MAP.isEnabled()) {
			return;
		}

		if (tick % 5 == 0) {
			trackPlayer(client);
			if (ParzConfigDefinition.DUNGEON_MAP_STATUS.isEnabled()) {
				scanMagicMap(client);
			}
		}
		int every = scanner.passes() < 30 ? 10 : 40;
		if (tick % every == 0) {
			scanner.scan(client.level);
			if (startCell != null) {
				scanner.markEntrance(startCell[0], startCell[1]);
			}
			buildViews();
			String summary = "scan: " + scanner.rooms().size() + " rooms, " + scanner.unidentified() + " without a name, "
					+ scanner.doors().size() + " doors";
			if (!summary.equals(lastScanSummary)) {
				lastScanSummary = summary;
				net.parz.Debug.log("dungeon", () -> summary);
			}
		} else if (tick % 5 == 0 && !views.isEmpty()) {
			updateStates();
		}
	}

	private static String lastScanSummary = "";

	private static void resetRun() {
		lastScanSummary = "";
		scanner.reset();
		java.util.Arrays.fill(visited, false);
		startCell = null;
		views = new ArrayList<>();
		doorViews = new ArrayList<>();
		viewCols = 5;
		viewRows = 5;
		mapFound = false;
		calibrated = false;
		mapAligned = false;
		mapId = Integer.MIN_VALUE;
		java.util.Arrays.fill(seen, (byte) 0);
	}

	/** Remembers which rooms you have been in. The first room you stand in is the start room. */
	private static void trackPlayer(Minecraft client) {
		int[] cell = DungeonScanner.cellOf(client.player.getX(), client.player.getZ());
		if (cell == null) {
			return;
		}
		visited[cell[1] * 6 + cell[0]] = true;
		DungeonScanner.Room room = scanner.roomAt(cell[0], cell[1]);
		if (room != null) {
			for (int[] c : room.cells) {
				visited[c[1] * 6 + c[0]] = true;
			}
		}
		if (startCell == null) {
			startCell = cell;
			net.parz.Debug.log("dungeon", () -> "start room cell: " + cell[0] + "," + cell[1]);
		}
	}

	/** Lines for /pz dungeon: what the scan sees right now. */
	public static List<String> debug(Minecraft client) {
		List<String> out = new ArrayList<>();
		out.add("features active: " + SkyBlockDetector.featuresActive() + " (on SkyBlock: " + SkyBlockDetector.isOnSkyBlock() + ")");
		out.add("dungeon map setting: " + ParzConfigDefinition.DUNGEON_MAP.isEnabled());
		out.add(DungeonInfo.describe());
		out.add("room hashes in database: " + RoomDatabase.size());
		out.add("scan passes: " + scanner.passes() + ", rooms found: " + scanner.rooms().size()
				+ ", without a name: " + scanner.unidentified() + ", doors: " + scanner.doors().size());
		out.add("shown as " + viewCols + "x" + viewRows + ", turned by " + rotation * 90 + " degrees, start cell: "
				+ (startCell == null ? "?" : startCell[0] + "," + startCell[1]));
		int shown = 0;
		for (DungeonScanner.Room room : scanner.rooms()) {
			if (room.info == null && !room.entrance && shown++ < 6) {
				int[] cell = room.cells.get(0);
				int[] cores = scanner.coresOf(cell[0], cell[1]);
				out.add("no name for the room at " + cell[0] + "," + cell[1] + " (" + room.cells.size() + " cells), cores "
						+ cores[0] + " / " + cores[1]);
			}
		}
		out.add("magic map for check marks: " + (mapFound ? "found" : "none") + ", aligned: " + mapAligned);
		out.add("last problem: " + lastProblem);
		return out;
	}

	// ======================================================================================
	// Building what is drawn
	// ======================================================================================

	private static void buildViews() {
		List<DungeonScanner.Room> rooms = scanner.rooms();
		if (rooms.isEmpty()) {
			views = new ArrayList<>();
			doorViews = new ArrayList<>();
			return;
		}
		int minC = 99;
		int minR = 99;
		int maxC = -1;
		int maxR = -1;
		for (DungeonScanner.Room room : rooms) {
			for (int[] cell : room.cells) {
				minC = Math.min(minC, cell[0]);
				minR = Math.min(minR, cell[1]);
				maxC = Math.max(maxC, cell[0]);
				maxR = Math.max(maxR, cell[1]);
			}
		}
		minCol = minC;
		minRow = minR;
		boxCols = maxC - minC + 1;
		boxRows = maxR - minR + 1;

		// turn the map so that the start room is at the bottom edge
		rotation = 0;
		for (DungeonScanner.Room room : rooms) {
			if (!room.entrance) {
				continue;
			}
			int[] e = room.cells.get(0);
			if (e[1] == maxR) {
				rotation = 0;
			} else if (e[1] == minR) {
				rotation = 2;
			} else if (e[0] == minC) {
				rotation = 3;
			} else if (e[0] == maxC) {
				rotation = 1;
			}
		}
		viewCols = rotation % 2 == 0 ? boxCols : boxRows;
		viewRows = rotation % 2 == 0 ? boxRows : boxCols;

		List<View> result = new ArrayList<>();
		for (DungeonScanner.Room room : rooms) {
			View view = new View();
			view.name = room.name();
			view.type = room.type();
			view.secrets = room.secrets();
			for (int[] cell : room.cells) {
				view.worldCells.add(cell);
				view.cells.add(turn(cell[0], cell[1]));
			}
			result.add(view);
		}
		List<DoorView> doorResult = new ArrayList<>();
		for (DungeonScanner.Door door : scanner.doors()) {
			int[] a = door.cellA();
			int[] b = door.cellB();
			if (scanner.roomAt(a[0], a[1]) == null || scanner.roomAt(b[0], b[1]) == null) {
				continue;
			}
			int[] ta = turn(a[0], a[1]);
			int[] tb = turn(b[0], b[1]);
			doorResult.add(new DoorView(ta[0], ta[1], tb[0], tb[1], door.type()));
		}
		views = result;
		doorViews = doorResult;
		updateStates();
	}

	/** A room cell of the dungeon -> the cell where it is drawn. */
	private static int[] turn(int col, int row) {
		int c = col - minCol;
		int r = row - minRow;
		return switch (rotation) {
			case 1 -> new int[]{boxRows - 1 - r, c};
			case 2 -> new int[]{boxCols - 1 - c, boxRows - 1 - r};
			case 3 -> new int[]{r, boxCols - 1 - c};
			default -> new int[]{c, r};
		};
	}

	/** A position in cell units (a room is 1.0 wide) -> the same for the drawn map. */
	private static float[] turn(float u, float v) {
		float c = u - minCol;
		float r = v - minRow;
		return switch (rotation) {
			case 1 -> new float[]{boxRows - r, c};
			case 2 -> new float[]{boxCols - c, boxRows - r};
			case 3 -> new float[]{r, boxCols - c};
			default -> new float[]{c, r};
		};
	}

	private static void updateStates() {
		// the Magic Map is lined up with the dungeon by the start room
		int[] entranceWorld = null;
		for (DungeonScanner.Room room : scanner.rooms()) {
			if (room.entrance) {
				entranceWorld = room.cells.get(0);
			}
		}
		int checked = 0;
		int hits = 0;
		State[] mapStates = new State[views.size()];
		if (calibrated && entranceWorld != null) {
			for (int i = 0; i < views.size(); i++) {
				mapStates[i] = mapStateOf(views.get(i), entranceWorld);
				checked++;
				if (mapStates[i] != null) {
					hits++;
				}
			}
		}
		mapAligned = checked >= 3 && hits * 10 >= checked * 7;

		for (int i = 0; i < views.size(); i++) {
			View view = views.get(i);
			boolean entered = false;
			for (int[] cell : view.worldCells) {
				entered |= visited[cell[1] * 6 + cell[0]];
			}
			State before = view.state;
			State fromMap = mapAligned ? mapStates[i] : null;
			if (fromMap != null && fromMap != State.UNEXPLORED) {
				view.state = fromMap;
			} else if (entered) {
				view.state = State.ENTERED;
			} else {
				view.state = State.UNEXPLORED;
			}
			if (view.type.equals("entrance")) {
				view.state = entered || fromMap != null ? State.COMPLETE : State.ENTERED;
			}
			if (view.state != before) {
				final State after = view.state;
				net.parz.Debug.log("rooms", () -> (view.name.isEmpty() ? view.type : view.name) + ": " + before + " -> " + after);
			}
		}
	}

	private static State mapStateOf(View view, int[] entranceWorld) {
		State result = null;
		for (int[] cell : view.worldCells) {
			int mapCol = cell[0] - entranceWorld[0] + anchorCol;
			int mapRow = cell[1] - entranceWorld[1] + anchorRow;
			if (mapCol < 0 || mapRow < 0 || mapCol >= mapCols || mapRow >= mapRows) {
				return null;
			}
			int px = offX + mapCol * pitch;
			int py = offY + mapRow * pitch;
			int color = pixel(px + 1, py + 1);
			if (color == 0) {
				return null;
			}
			if (color == C_UNEXPLORED || color == C_UNKNOWN) {
				return State.UNEXPLORED;
			}
			int middle = pixel(px + size / 2, py + size / 2);
			if (middle == C_CHECK_WHITE) {
				return State.CLEARED;
			}
			if (middle == C_CHECK_GREEN && color != C_ENTRANCE) {
				return State.COMPLETE;
			}
			if (middle == C_CROSS && color != C_BLOOD) {
				result = State.FAILED;
			} else if (result == null) {
				result = State.ENTERED;
			}
		}
		return result;
	}

	// ======================================================================================
	// The Magic Map item (check marks only)
	// ======================================================================================

	private static void scanMagicMap(Minecraft client) {
		ItemStack stack = ItemStack.EMPTY;
		for (int slot = HOTBAR_LAST; slot >= 0; slot--) {
			ItemStack candidate = client.player.getInventory().getItem(slot);
			if (candidate.is(Items.FILLED_MAP) && candidate.get(DataComponents.MAP_ID) != null
					&& (slot == HOTBAR_LAST || candidate.getHoverName().getString().toLowerCase(Locale.ROOT).contains("magic map"))) {
				stack = candidate;
				break;
			}
		}
		MapId id = stack.isEmpty() ? null : stack.get(DataComponents.MAP_ID);
		mapFound = id != null;
		if (id == null) {
			return;
		}
		MapItemSavedData data = client.level.getMapData(id);
		if (data == null) {
			return;
		}
		if (id.id() != mapId) {
			mapId = id.id();
			calibrated = false;
			java.util.Arrays.fill(seen, (byte) 0);
		}
		byte[] colors = data.colors;
		int length = Math.min(colors.length, seen.length);
		for (int i = 0; i < length; i++) {
			if (colors[i] != 0) {
				seen[i] = colors[i];
			}
		}
		if (!calibrated) {
			calibrate();
		}
	}

	/** Finds the green start room on the map: its corner and edge give the grid of all rooms. */
	private static void calibrate() {
		for (int y = 0; y < MAP_PIXELS; y++) {
			for (int x = 0; x < MAP_PIXELS; x++) {
				if (pixel(x, y) != C_ENTRANCE || pixel(x - 1, y) == C_ENTRANCE || pixel(x, y - 1) == C_ENTRANCE) {
					continue;
				}
				int run = 0;
				while (pixel(x + run, y) == C_ENTRANCE) {
					run++;
				}
				int height = 0;
				while (pixel(x, y + height) == C_ENTRANCE) {
					height++;
				}
				if (run < 14 || height < 14) {
					continue;
				}
				size = run >= 18 && height >= 18 ? 18 : 16;
				pitch = size + DOOR_GAP;
				offX = x % pitch;
				offY = y % pitch;
				anchorCol = x / pitch;
				anchorRow = y / pitch;
				mapCols = Math.max(1, Math.min(MAX_CELLS, (MAP_PIXELS - offX - size) / pitch + 1));
				mapRows = Math.max(1, Math.min(MAX_CELLS, (MAP_PIXELS - offY - size) / pitch + 1));
				calibrated = true;
				return;
			}
		}
	}

	private static int pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= MAP_PIXELS || y >= MAP_PIXELS) {
			return 0;
		}
		return seen[y * MAP_PIXELS + x] & 0xFF;
	}

	// ======================================================================================
	// Drawing
	// ======================================================================================

	/** Size of the map box at scale 1.0. */
	public static int width() {
		return Math.max(MIN_WIDTH, viewCols * PITCH - GAP + 2 * PAD);
	}

	public static int height() {
		return viewRows * PITCH - GAP + 2 * PAD + FOOTER;
	}

	private static void renderHud(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled() || !DungeonInfo.inCatacombs() || client.gui.screen() instanceof HudEditorScreen) {
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
		roundRect(graphics, x, y, x + width, y + height, PANEL_EDGE, 8);
		roundRect(graphics, x + 1, y + 1, x + width - 1, y + height - 1, PANEL, 7);

		int mapW = viewCols * PITCH - GAP;
		int originX = x + (width - mapW) / 2;
		int originY = y + PAD;

		if (views.isEmpty()) {
			if (preview) {
				drawSample(graphics, font, originX, originY);
			} else {
				String message = scanner.passes() == 0 ? "Scanning the dungeon..." : "Looking for rooms...";
				graphics.text(font, message, x + (width - font.width(message)) / 2, originY + 20, TEXT_DIM, true);
			}
		} else {
			drawRooms(graphics, font, originX, originY);
		}
		drawFooter(graphics, font, x, y + height - FOOTER, width);
	}

	private static void drawRooms(GuiGraphicsExtractor graphics, Font font, int originX, int originY) {
		boolean names = ParzConfigDefinition.DUNGEON_MAP_NAMES.isEnabled();
		boolean status = ParzConfigDefinition.DUNGEON_MAP_STATUS.isEnabled();

		for (View view : views) {
			int fill = fillColor(view);
			for (int[] cell : view.cells) {
				boolean left = hasCell(view, cell[0] - 1, cell[1]);
				boolean right = hasCell(view, cell[0] + 1, cell[1]);
				boolean up = hasCell(view, cell[0], cell[1] - 1);
				boolean down = hasCell(view, cell[0], cell[1] + 1);
				int rx = originX + cell[0] * PITCH;
				int ry = originY + cell[1] * PITCH;
				// only the outer corners of a room are round
				smoothRect(graphics, rx, ry, rx + ROOM, ry + ROOM, fill, ROOM_RADIUS,
						!left && !up, !right && !up, !left && !down, !right && !down);
			}
			// cells of one big room are joined without a seam
			for (int[] a : view.cells) {
				if (hasCell(view, a[0] + 1, a[1])) {
					graphics.fill(originX + a[0] * PITCH + ROOM, originY + a[1] * PITCH,
							originX + (a[0] + 1) * PITCH, originY + a[1] * PITCH + ROOM, fill);
				}
				if (hasCell(view, a[0], a[1] + 1)) {
					graphics.fill(originX + a[0] * PITCH, originY + a[1] * PITCH + ROOM,
							originX + a[0] * PITCH + ROOM, originY + (a[1] + 1) * PITCH, fill);
				}
				// the middle of a block of 2x2 cells (also when one of them is missing, like in an L)
				int inBlock = 0;
				for (int dc = 0; dc <= 1; dc++) {
					for (int dr = 0; dr <= 1; dr++) {
						if (hasCell(view, a[0] + dc, a[1] + dr)) {
							inBlock++;
						}
					}
				}
				if (inBlock >= 3 && hasCell(view, a[0], a[1])) {
					graphics.fill(originX + a[0] * PITCH + ROOM, originY + a[1] * PITCH + ROOM,
							originX + a[0] * PITCH + PITCH, originY + a[1] * PITCH + PITCH, fill);
				}
			}
		}

		// doors
		for (DoorView door : doorViews) {
			int color = switch (door.type()) {
				case DungeonScanner.DOOR_WITHER -> 0xFF101010;
				case DungeonScanner.DOOR_BLOOD -> 0xFFC02828;
				case DungeonScanner.DOOR_ENTRANCE -> 0xFF1F8A1F;
				default -> 0xFF4A2B0E;
			};
			int c = Math.min(door.c1(), door.c2());
			int r = Math.min(door.r1(), door.r2());
			if (door.r1() == door.r2()) {
				int rx = originX + c * PITCH + ROOM;
				int ry = originY + r * PITCH + (ROOM - DOOR_W) / 2;
				smoothRect(graphics, rx - 1, ry, rx + GAP + 1, ry + DOOR_W, color, 2, true, true, true, true);
			} else {
				int rx = originX + c * PITCH + (ROOM - DOOR_W) / 2;
				int ry = originY + r * PITCH + ROOM;
				smoothRect(graphics, rx, ry - 1, rx + DOOR_W, ry + GAP + 1, color, 2, true, true, true, true);
			}
		}

		// names, secrets and state
		for (View view : views) {
			drawLabel(graphics, font, view, originX, originY, names);
			if (status) {
				int[] corner = view.cells.get(0);
				for (int[] cell : view.cells) {
					if (cell[1] < corner[1] || (cell[1] == corner[1] && cell[0] > corner[0])) {
						corner = cell;
					}
				}
				int ix = originX + corner[0] * PITCH + ROOM - 6;
				int iy = originY + corner[1] * PITCH + 5;
				switch (view.state) {
					case CLEARED -> drawCheck(graphics, ix, iy, 0xFFFFFFFF);
					case COMPLETE -> drawCheck(graphics, ix, iy, 0xFF3DDC4F);
					case FAILED -> drawCross(graphics, ix, iy, 0xFFFF4040);
					default -> {
					}
				}
			}
		}

		// players
		Minecraft client = Minecraft.getInstance();
		if (client.level != null && client.player != null) {
			for (Player player : client.level.players()) {
				if (player.getUUID().version() != 4) {
					continue; // not a real player (an NPC)
				}
				float u = (float) ((player.getX() - DungeonScanner.WORLD_MIN) / DungeonScanner.STRIDE);
				float v = (float) ((player.getZ() - DungeonScanner.WORLD_MIN) / DungeonScanner.STRIDE);
				if (u < 0 || v < 0 || u > 6 || v > 6) {
					continue;
				}
				float[] p = turn(u, v);
				int mx = originX + Math.round(p[0] * PITCH - GAP / 2f);
				int my = originY + Math.round(p[1] * PITCH - GAP / 2f);
				boolean you = player == client.player;
				graphics.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
				graphics.fill(mx - 2, my - 2, mx + 2, my + 2, you ? 0xFFFFFFFF : 0xFF55FF55);
			}
		}
	}

	private static void drawLabel(GuiGraphicsExtractor graphics, Font font, View view, int originX, int originY, boolean names) {
		if (!names) {
			return;
		}
		List<String> lines = new ArrayList<>();
		if (!view.name.isEmpty()) {
			for (String word : view.name.split(" ")) {
				lines.add(word);
			}
			if (view.secrets >= 0) {
				lines.add(String.valueOf(view.secrets));
			}
		} else if (!view.type.equals("unknown") && !view.type.equals("normal")) {
			lines.add(Character.toUpperCase(view.type.charAt(0)) + view.type.substring(1));
		} else if (view.type.equals("unknown")) {
			lines.add("?");
		}
		if (lines.isEmpty()) {
			return;
		}
		int minC = Integer.MAX_VALUE;
		int minR = Integer.MAX_VALUE;
		int maxC = -1;
		int maxR = -1;
		for (int[] cell : view.cells) {
			minC = Math.min(minC, cell[0]);
			minR = Math.min(minR, cell[1]);
			maxC = Math.max(maxC, cell[0]);
			maxR = Math.max(maxR, cell[1]);
		}
		int boxW = (maxC - minC) * PITCH + ROOM;
		int boxH = (maxR - minR) * PITCH + ROOM;
		float centerX = originX + minC * PITCH + boxW / 2f;
		float centerY = originY + minR * PITCH + boxH / 2f;
		if (view.cells.size() > 1 && view.cells.size() != (maxC - minC + 1) * (maxR - minR + 1)) {
			// a bent room: the name goes to the cell that is closest to the middle
			int[] best = view.cells.get(0);
			float bestDistance = Float.MAX_VALUE;
			for (int[] cell : view.cells) {
				float dx = originX + cell[0] * PITCH + ROOM / 2f - centerX;
				float dy = originY + cell[1] * PITCH + ROOM / 2f - centerY;
				float d = dx * dx + dy * dy;
				if (d < bestDistance) {
					bestDistance = d;
					best = cell;
				}
			}
			centerX = originX + best[0] * PITCH + ROOM / 2f;
			centerY = originY + best[1] * PITCH + ROOM / 2f;
			boxW = ROOM;
			boxH = ROOM;
		}
		int widest = 1;
		for (String line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		float scale = Math.min(1.0f, Math.min((boxW - 4f) / widest, (boxH - 2f) / (lines.size() * font.lineHeight)));
		scale = Math.max(0.35f, scale);
		float total = lines.size() * font.lineHeight * scale;
		float y = centerY - total / 2f;
		for (int i = 0; i < lines.size(); i++) {
			boolean secretLine = view.secrets >= 0 && !view.name.isEmpty() && i == lines.size() - 1;
			drawSmall(graphics, font, lines.get(i), Math.round(centerX), Math.round(y), secretLine ? 0xFFFFFFFF : TEXT_NAME, scale);
			y += font.lineHeight * scale;
		}
	}

	private static int fillColor(View view) {
		int color = switch (view.type) {
			case "entrance" -> 0xFF1F8A1F;
			case "puzzle" -> 0xFF7A1F91;
			case "fairy" -> 0xFFA21FB5;
			case "trap" -> 0xFF9C5A1E;
			case "blood" -> 0xFF9C1F1F;
			case "yellow" -> 0xFFB59A1B;
			case "rare" -> 0xFF3F6E8C;
			case "normal" -> 0xFF4E2D12;
			default -> 0xFF50463C;
		};
		return view.state == State.UNEXPLORED ? dim(color, DIM_UNEXPLORED / 100f * 0.6f) : color;
	}

	private static void drawFooter(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		graphics.fill(x + PAD, y, x + width - PAD, y + 1, 0x40FFFFFF);
		String[] captions = {"SCORE", "SECRETS", "CRYPTS", "DEATHS"};
		int total = DungeonInfo.secretsTotal();
		if (total <= 0 && !scanner.rooms().isEmpty() && scanner.unidentified() == 0) {
			total = 0;
			for (DungeonScanner.Room room : scanner.rooms()) {
				total += Math.max(0, room.secrets());
			}
		}
		String secrets = DungeonInfo.secrets() < 0 ? (total > 0 ? "0/" + total : "-")
				: total > 0 ? DungeonInfo.secrets() + "/" + total : String.valueOf(DungeonInfo.secrets());
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

	/** A few sample rooms so you can place the map with /pz hud before a run. */
	private static void drawSample(GuiGraphicsExtractor graphics, Font font, int originX, int originY) {
		String[][] layout = {{"Puzzle", "Atlas", "Atlas", "Fairy"}, {"Trap", "Hall", "", "Blood"}, {"", "Entrance", "", ""}};
		int[] colors = {0xFF7A1F91, 0xFF4E2D12, 0xFF4E2D12, 0xFFA21FB5};
		for (int row = 0; row < layout.length; row++) {
			for (int col = 0; col < layout[row].length; col++) {
				String label = layout[row][col];
				if (label.isEmpty()) {
					continue;
				}
				int fill = switch (label) {
					case "Entrance" -> 0xFF1F8A1F;
					case "Trap" -> 0xFF9C5A1E;
					case "Blood" -> 0xFF9C1F1F;
					case "Puzzle" -> 0xFF7A1F91;
					case "Fairy" -> 0xFFA21FB5;
					default -> 0xFF4E2D12;
				};
				int rx = originX + col * PITCH;
				int ry = originY + row * PITCH;
				roundRect(graphics, rx, ry, rx + ROOM, ry + ROOM, fill, ROOM_RADIUS);
				drawSmall(graphics, font, label, rx + ROOM / 2, ry + ROOM / 2 - 3, TEXT_NAME, 0.5f);
			}
		}
		drawSmall(graphics, font, "Move me with /pz hud", originX + (viewCols * PITCH - GAP) / 2, originY + 3 * PITCH + 2, TEXT_DIM, 0.6f);
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
		int[][] points = {{-3, 0}, {-2, 1}, {-1, 2}, {0, 1}, {1, 0}, {2, -1}, {3, -2}, {4, -3}};
		for (int[] p : points) {
			graphics.fill(cx + p[0] - 1, cy + p[1] - 1, cx + p[0] + 2, cy + p[1] + 2, 0xB0000000);
		}
		for (int[] p : points) {
			graphics.fill(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, color);
			graphics.fill(cx + p[0], cy + p[1] - 1, cx + p[0] + 1, cy + p[1], color);
		}
	}

	private static void drawCross(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		for (int i = -2; i <= 2; i++) {
			graphics.fill(cx + i - 1, cy + i - 1, cx + i + 2, cy + i + 2, 0xB0000000);
			graphics.fill(cx + i - 1, cy - i - 1, cx + i + 2, cy - i + 2, 0xB0000000);
		}
		for (int i = -2; i <= 2; i++) {
			graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
			graphics.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
		}
	}

	/** A rectangle with round corners (all four). */
	private static void roundRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color, int radius) {
		smoothRect(graphics, x1, y1, x2, y2, color, radius, true, true, true, true);
	}

	/** A rectangle with smooth round corners; each corner can be switched off to stay square. */
	private static void smoothRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color, int radius,
								   boolean topLeft, boolean topRight, boolean bottomLeft, boolean bottomRight) {
		int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
		if (r <= 0) {
			graphics.fill(x1, y1, x2, y2, color);
			return;
		}
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy)); // a quarter circle
			graphics.fill(x1 + (topLeft ? inset : 0), y1 + i, x2 - (topRight ? inset : 0), y1 + i + 1, color);
			graphics.fill(x1 + (bottomLeft ? inset : 0), y2 - i - 1, x2 - (bottomRight ? inset : 0), y2 - i, color);
		}
		graphics.fill(x1, y1 + r, x2, y2 - r, color);
	}

	private static boolean hasCell(View view, int col, int row) {
		for (int[] cell : view.cells) {
			if (cell[0] == col && cell[1] == row) {
				return true;
			}
		}
		return false;
	}

	private static int dim(int argb, float factor) {
		int r = Math.round(((argb >> 16) & 0xFF) * factor);
		int g = Math.round(((argb >> 8) & 0xFF) * factor);
		int b = Math.round((argb & 0xFF) * factor);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
