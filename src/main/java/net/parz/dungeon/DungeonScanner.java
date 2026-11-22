package net.parz.dungeon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
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
 * Every cell is recognised on its own by the hash of the blocks in the column at its middle. Cells with the
 * same room name next to each other are one room - so a recognised room is never joined to a wrong neighbour.
 * Only cells that are not recognised are joined by looking at the roof between them.
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

		public String name() {
			return info != null ? info.name() : entrance ? "Entrance" : "";
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

	// what has been read from the world
	private final Integer[] cellRoof = new Integer[36];
	private final RoomDatabase.RoomInfo[] cellInfo = new RoomDatabase.RoomInfo[36];
	private final int[] cellCore = new int[36];
	private final int[] cellLegacyCore = new int[36];
	private final int[] doorlessPasses = new int[36];
	private final Map<Integer, Integer> slotRoof = new HashMap<>();
	private final Map<Integer, Door> doors = new HashMap<>();
	private List<int[]> remaining = allCoordinates();
	private int passes = 0;
	private final int[] attempts = new int[36];
	private int[] forcedEntrance = null;

	// what is built from it
	private List<Room> rooms = new ArrayList<>();
	private final Map<Integer, Room> byCell = new HashMap<>();

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

	/** Rooms (not cells) that have no name. */
	public int unidentified() {
		int count = 0;
		for (Room room : rooms) {
			if (room.info == null && !room.entrance) {
				count++;
			}
		}
		return count;
	}

	public int passes() {
		return passes;
	}

	public boolean hasWork() {
		return !remaining.isEmpty();
	}

	/** Hashes of a cell for the debug output: modern, legacy. */
	public int[] coresOf(int col, int row) {
		return new int[]{cellCore[row * 6 + col], cellLegacyCore[row * 6 + col]};
	}

	public void reset() {
		java.util.Arrays.fill(cellRoof, null);
		java.util.Arrays.fill(cellInfo, null);
		java.util.Arrays.fill(attempts, 0);
		java.util.Arrays.fill(doorlessPasses, 0);
		slotRoof.clear();
		doors.clear();
		remaining = allCoordinates();
		passes = 0;
		forcedEntrance = null;
		rooms = new ArrayList<>();
		byCell.clear();
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
				cellRoof[(c[1] / 2) * 6 + c[0] / 2] = roof;
			}
		}
		remaining = keep;

		// every cell is recognised on its own; cells that are not recognised yet are tried again each pass
		// (reading a room is expensive: a room that was not recognised after a few tries is only tried now and then,
		// and only a few rooms are read per pass, so no single frame has to do it all)
		int budget = 3;
		for (int idx = 0; idx < 36 && budget > 0; idx++) {
			if (cellRoof[idx] != null && cellInfo[idx] == null) {
				if (attempts[idx] >= 4 && passes % 12 != 0) {
					continue;
				}
				attempts[idx]++;
				budget--;
				identify(level, idx);
			}
		}
		rebuild();
	}

	// ---- recognising rooms ----

	private void identify(ClientLevel level, int idx) {
		int col = idx % 6;
		int row = idx / 6;
		int x = worldX(col * 2);
		int z = worldX(row * 2);
		int modern = modernCore(level, x, z);
		cellCore[idx] = modern;
		RoomDatabase.RoomInfo info = RoomDatabase.byModernCore(modern);
		if (info == null) {
			int legacy = legacyCore(level, x, z);
			cellLegacyCore[idx] = legacy;
			info = RoomDatabase.byLegacyCore(legacy);
		}
		cellInfo[idx] = info;
	}

	/** The hash of one column as Odin computes it (modern block names, y 140 down to 12). */
	public static int modernCore(ClientLevel level, int x, int z) {
		StringBuilder sb = new StringBuilder(2048);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean foundHighest = false;
		int bedrock = 0;
		for (int y = 140; y >= 12; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			Block block = state.getBlock();
			if (!foundHighest) {
				if (!state.isAir() && block != Blocks.GOLD_BLOCK) {
					foundHighest = true;
				} else {
					sb.append('0');
				}
			}
			if (foundHighest) {
				if (state.isAir() && bedrock >= 2 && y < 69) {
					for (int i = 0; i < y - 11; i++) {
						sb.append('0');
					}
					break;
				}
				if (block == Blocks.BEDROCK) {
					bedrock++;
				} else {
					bedrock = 0;
					if (block == Blocks.OAK_PLANKS || block == Blocks.TRAPPED_CHEST || block == Blocks.CHEST) {
						continue;
					}
				}
				sb.append(block);
			}
		}
		return sb.toString().hashCode();
	}

	/** The hash of one column as IllegalMap computes it (1.8 block ids, y 140 down to 12). */
	public static int legacyCore(ClientLevel level, int x, int z) {
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

	// ---- doors ----

	private void doorSlot(ClientLevel level, int gx, int gz, int wx, int wz, int roof) {
		slotRoof.put(key(gx, gz), roof);
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
		}
	}

	private static int key(int gx, int gz) {
		return gz * 11 + gx;
	}

	// ---- building the rooms ----

	public void markEntrance(int col, int row) {
		if (forcedEntrance == null) {
			forcedEntrance = new int[]{col, row};
		}
	}

	private void rebuild() {
		List<Room> result = new ArrayList<>();
		Map<Integer, Room> map = new HashMap<>();

		// 1. recognised cells: next to each other with the same name = one room
		for (int idx = 0; idx < 36; idx++) {
			if (cellRoof[idx] == null || cellInfo[idx] == null || map.containsKey(idx)) {
				continue;
			}
			Room room = new Room();
			room.info = cellInfo[idx];
			room.roof = cellRoof[idx];
			room.entrance = room.info.type().equals("entrance");
			List<Integer> queue = new ArrayList<>();
			queue.add(idx);
			map.put(idx, room);
			for (int i = 0; i < queue.size(); i++) {
				int current = queue.get(i);
				room.cells.add(new int[]{current % 6, current / 6});
				for (int next : neighbours(current)) {
					if (!map.containsKey(next) && cellRoof[next] != null && cellInfo[next] != null
							&& cellInfo[next].name().equals(room.info.name())) {
						map.put(next, room);
						queue.add(next);
					}
				}
			}
			result.add(room);
		}

		// 2. cells that are not recognised: joined by a roof that goes on across the gap
		for (int idx = 0; idx < 36; idx++) {
			if (cellRoof[idx] == null || cellInfo[idx] != null || map.containsKey(idx)) {
				continue;
			}
			Room room = new Room();
			room.roof = cellRoof[idx];
			List<Integer> queue = new ArrayList<>();
			queue.add(idx);
			map.put(idx, room);
			for (int i = 0; i < queue.size(); i++) {
				int current = queue.get(i);
				room.cells.add(new int[]{current % 6, current / 6});
				for (int next : neighbours(current)) {
					if (!map.containsKey(next) && cellRoof[next] != null && cellInfo[next] == null && roofContinues(current, next)) {
						map.put(next, room);
						queue.add(next);
					}
				}
			}
			result.add(room);
		}

		// 3. the start room, if the database did not know it
		boolean hasEntrance = false;
		for (Room room : result) {
			hasEntrance |= room.entrance;
		}
		if (!hasEntrance && forcedEntrance != null) {
			Room room = map.get(forcedEntrance[1] * 6 + forcedEntrance[0]);
			if (room != null) {
				room.entrance = true;
			}
		}

		// 4. leftovers outside of a smaller dungeon: not recognised and without any door
		List<Room> remove = new ArrayList<>();
		for (Room room : result) {
			boolean hasDoor = room.info != null || room.entrance || hasDoor(room);
			for (int[] cell : room.cells) {
				int idx = cell[1] * 6 + cell[0];
				doorlessPasses[idx] = hasDoor ? 0 : doorlessPasses[idx] + 1;
			}
			if (!hasDoor && doorlessPasses[room.cells.get(0)[1] * 6 + room.cells.get(0)[0]] >= PRUNE_PASSES) {
				remove.add(room);
			}
		}
		for (Room room : remove) {
			result.remove(room);
			for (int[] cell : room.cells) {
				map.remove(cell[1] * 6 + cell[0]);
			}
		}

		rooms = result;
		byCell.clear();
		byCell.putAll(map);
	}

	private static List<Integer> neighbours(int idx) {
		List<Integer> list = new ArrayList<>(4);
		int col = idx % 6;
		int row = idx / 6;
		if (col > 0) {
			list.add(idx - 1);
		}
		if (col < 5) {
			list.add(idx + 1);
		}
		if (row > 0) {
			list.add(idx - 6);
		}
		if (row < 5) {
			list.add(idx + 6);
		}
		return list;
	}

	/** Two cells that are not recognised are one room if the roof is as high in the gap as in both cells. */
	private boolean roofContinues(int a, int b) {
		int gx = (a % 6) + (b % 6);
		int gz = (a / 6) + (b / 6);
		Integer gap = slotRoof.get(key(gx, gz));
		return gap != null && !doors.containsKey(key(gx, gz))
				&& gap.equals(cellRoof[a]) && gap.equals(cellRoof[b]);
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
}
