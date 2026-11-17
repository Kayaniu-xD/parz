package net.parz.dungeon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds every room of the dungeon by reading the blocks of the world that the server has already sent to your
 * client (nothing is sent to the server, nothing hidden is read). A Catacombs dungeon always lies at X/Z
 * -200..-10: a 6x6 grid of rooms, each 31 blocks wide, with a 1 block gap between two rooms that holds the
 * door (or a wall).
 *
 * Room cells are at even numbers of the 11x11 "door grid" (0, 2, ... 10), door slots at odd numbers.
 * A room is recognised by the hash of the blocks in the column at the middle of one of its cells.
 */
public final class DungeonScanner {
	public static final int WORLD_MIN = -200;
	public static final int ROOM_SIZE = 31;
	public static final int STRIDE = ROOM_SIZE + 1;
	public static final int HALF_ROOM = ROOM_SIZE / 2;
	public static final int HALF_STRIDE = STRIDE / 2;
	private static final int PRUNE_PASSES = 5;

	public static final int DOOR_NORMAL = 0;
	public static final int DOOR_WITHER = 1;
	public static final int DOOR_BLOOD = 2;
	public static final int DOOR_ENTRANCE = 3;

	public static final class Room {
		public final List<int[]> cells = new ArrayList<>(); // col, row (0..5)
		public int roof;
		public RoomDatabase.RoomInfo info;
		public boolean entrance; // the start room (from the database, or where you spawned)
		int doorlessPasses = 0;

		public String name() {
			return entrance ? "Entrance" : info != null ? info.name() : "";
		}

		/** normal, puzzle, trap, yellow, blood, fairy, rare, entrance or unknown. */
		public String type() {
			return entrance ? "entrance" : info != null ? info.type() : "unknown";
		}

		public int secrets() {
			return info != null ? info.secrets() : -1;
		}

		public boolean contains(int col, int row) {
			for (int[] cell : cells) {
				if (cell[0] == col && cell[1] == row) {
					return true;
				}
			}
			return false;
		}
	}

	/** A door between two room cells. gx, gz are door grid coordinates (one of them is odd). */
	public record Door(int gx, int gz, int type) {
		public int[] cellA() {
			return gx % 2 == 1 ? new int[]{(gx - 1) / 2, gz / 2} : new int[]{gx / 2, (gz - 1) / 2};
		}

		public int[] cellB() {
			return gx % 2 == 1 ? new int[]{(gx + 1) / 2, gz / 2} : new int[]{gx / 2, (gz + 1) / 2};
		}
	}

	private final List<Room> rooms = new ArrayList<>();
	private final Map<Integer, Room> byCell = new HashMap<>();
	private final Map<Integer, Door> doors = new HashMap<>();
	private List<int[]> remaining = allCoordinates();
	private int passes = 0;
	private int unidentified = 0;
	private int lastUnknownCore = 0;

	private static List<int[]> allCoordinates() {
		List<int[]> list = new ArrayList<>();
		for (int z = 0; z < 11; z++) {
			for (int x = 0; x < 11; x++) {
				if (x % 2 == 1 && z % 2 == 1) {
					continue; // neither a room nor a door
				}
				list.add(new int[]{x, z});
			}
		}
		return list;
	}

	public List<Room> rooms() {
		return rooms;
	}

	public List<Door> doors() {
		return new ArrayList<>(doors.values());
	}

	public Room roomAt(int col, int row) {
		return byCell.get(row * 6 + col);
	}

	public int unidentified() {
		return unidentified;
	}

	public int lastUnknownCore() {
		return lastUnknownCore;
	}

	public int passes() {
		return passes;
	}

	public boolean hasWork() {
		return !remaining.isEmpty();
	}

	public void reset() {
		rooms.clear();
		byCell.clear();
		doors.clear();
		remaining = allCoordinates();
		passes = 0;
		unidentified = 0;
	}

	public static int worldX(int gridCoordinate) {
		return WORLD_MIN + HALF_ROOM + gridCoordinate * HALF_STRIDE;
	}

	/** World position -> room cell (col, row), or null outside the dungeon. */
	public static int[] cellOf(double x, double z) {
		int col = (int) Math.floor((x - WORLD_MIN + 0.5) / STRIDE);
		int row = (int) Math.floor((z - WORLD_MIN + 0.5) / STRIDE);
		return col < 0 || row < 0 || col > 5 || row > 5 ? null : new int[]{col, row};
	}

	/** One scan over every cell whose chunk is loaded. Cells that gave nothing are tried again next time. */
	public void scan(ClientLevel level) {
		passes++;
		List<int[]> keep = new ArrayList<>();
		for (int[] c : remaining) {
			int wx = worldX(c[0]);
			int wz = worldX(c[1]);
			if (!level.hasChunk(wx >> 4, wz >> 4)) {
				keep.add(c);
				continue;
			}
			Integer roof = roofHeight(level, wx, wz);
			if (roof == null) {
				keep.add(c); // void, or the blocks have not arrived yet
				continue;
			}
			if (c[0] % 2 == 1 || c[1] % 2 == 1) {
				doorSlot(level, c[0], c[1], wx, wz, roof);
			} else {
				roomCell(level, c[0] / 2, c[1] / 2, wx, wz, roof);
			}
		}
		remaining = keep;

		unidentified = 0;
		for (Room room : rooms) {
			if (room.info == null && !room.entrance) {
				identify(level, room);
			}
			if (room.info == null && !room.entrance) {
				unidentified++;
			}
		}
		prune();
	}

	// ---- rooms ----

	private void roomCell(ClientLevel level, int col, int row, int wx, int wz, int roof) {
		Room room = byCell.get(row * 6 + col);
		if (room == null) {
			room = new Room();
			room.roof = roof;
			room.cells.add(new int[]{col, row});
			byCell.put(row * 6 + col, room);
			rooms.add(room);
			identify(level, room);
		}
		if (room.entrance || (room.info != null && room.info.type().equals("entrance"))) {
			return; // the start room is never part of a bigger room
		}
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			int nx = col + d[0];
			int nz = row + d[1];
			if (nx < 0 || nz < 0 || nx > 5 || nz > 5) {
				continue;
			}
			int gapX = wx + d[0] * HALF_STRIDE;
			int gapZ = wz + d[1] * HALF_STRIDE;
			// the roof goes on across the gap: one big room
			boolean solid = !isAir(level, gapX, roof, gapZ);
			boolean open = isAir(level, gapX, roof + 1, gapZ);
			if (!solid || !open) {
				continue;
			}
			Room other = byCell.get(nz * 6 + nx);
			if (other == room) {
				continue;
			}
			if (other != null) {
				if (other.entrance || (other.info != null && other.info.type().equals("entrance"))) {
					continue;
				}
				merge(room, other);
			} else {
				room.cells.add(new int[]{nx, nz});
				byCell.put(nz * 6 + nx, room);
			}
		}
	}

	private void merge(Room into, Room from) {
		for (int[] cell : from.cells) {
			into.cells.add(cell);
			byCell.put(cell[1] * 6 + cell[0], into);
		}
		rooms.remove(from);
		if (into.info == null) {
			into.info = from.info;
		}
	}

	private void identify(ClientLevel level, Room room) {
		for (int[] cell : room.cells) {
			int core = core(level, worldX(cell[0] * 2), worldX(cell[1] * 2));
			RoomDatabase.RoomInfo info = RoomDatabase.byCore(core);
			if (info != null) {
				room.info = info;
				room.entrance = info.type().equals("entrance");
				return;
			}
			lastUnknownCore = core;
		}
	}

	/** The hash of the blocks of one column, as the room data expects it (1.8 block ids, y 140 down to 12). */
	public static int core(ClientLevel level, int x, int z) {
		StringBuilder sb = new StringBuilder(260);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 140; y >= 12; y--) {
			int id = LegacyBlocks.of(level.getBlockState(pos.set(x, y, z)));
			if (id == 101 || id == 54) {
				sb.append('0'); // iron bars and chests count as air
			} else {
				sb.append(id);
			}
		}
		return sb.toString().hashCode();
	}

	/** Marks the room that contains this cell as the start room (used when the database does not know it). */
	public void markEntrance(int col, int row) {
		Room room = byCell.get(row * 6 + col);
		if (room != null && rooms.stream().noneMatch(r -> r.entrance)) {
			room.entrance = true;
		}
	}

	// ---- doors ----

	private void doorSlot(ClientLevel level, int gx, int gz, int wx, int wz, int roof) {
		int below = LegacyBlocks.of(level.getBlockState(new BlockPos(wx, 69, wz)));
		if (below == 97) {
			doors.put(key(gx, gz), new Door(gx, gz, DOOR_ENTRANCE));
			return;
		}
		if (roof < 85) { // a doorway is low, a wall between two rooms is high
			int type = DOOR_NORMAL;
			if (below == 173) {
				type = DOOR_WITHER;
			} else if (below == 159) {
				type = DOOR_BLOOD;
			}
			doors.put(key(gx, gz), new Door(gx, gz, type));
			return;
		}
		// no door: the two rooms next to the slot are one room if their roofs line up
		Room a = gx % 2 == 1 ? byCell.get((gz / 2) * 6 + (gx - 1) / 2) : byCell.get(((gz - 1) / 2) * 6 + gx / 2);
		Room b = gx % 2 == 1 ? byCell.get((gz / 2) * 6 + (gx + 1) / 2) : byCell.get(((gz + 1) / 2) * 6 + gx / 2);
		if (a != null && b != null && a != b && a.roof == b.roof && a.roof == roof && !a.entrance && !b.entrance) {
			merge(a, b);
		}
	}

	private static int key(int gx, int gz) {
		return gz * 11 + gx;
	}

	/** Rooms that are not identified and have no door at all are leftovers outside of a smaller dungeon. */
	private void prune() {
		List<Room> remove = new ArrayList<>();
		for (Room room : rooms) {
			if (room.info != null || room.entrance || hasDoor(room)) {
				room.doorlessPasses = 0;
			} else if (++room.doorlessPasses >= PRUNE_PASSES) {
				remove.add(room);
			}
		}
		for (Room room : remove) {
			for (int[] cell : room.cells) {
				byCell.remove(cell[1] * 6 + cell[0]);
			}
			rooms.remove(room);
		}
	}

	private boolean hasDoor(Room room) {
		for (Door door : doors.values()) {
			int[] a = door.cellA();
			int[] b = door.cellB();
			if (room.contains(a[0], a[1]) || room.contains(b[0], b[1])) {
				return true;
			}
		}
		return false;
	}

	// ---- blocks ----

	/** The highest block of a column (gold blocks on a roof are ignored), or null if the column is empty. */
	private static Integer roofHeight(ClientLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 175; y > 0; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (state.isAir() || state.getBlock() == Blocks.GOLD_BLOCK) {
				continue;
			}
			return y;
		}
		return null;
	}

	private static boolean isAir(ClientLevel level, int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z)).isAir();
	}
}
