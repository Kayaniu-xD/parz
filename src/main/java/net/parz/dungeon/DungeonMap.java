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
import net.parz.config.ParzConfigDefinition;
import net.parz.gui.HudEditorScreen;
import net.parz.hud.HudTrackers;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dungeon map HUD. Hypixel gives you a "Magic Map" in the last hotbar slot while you are in a dungeon. The map is a
 * normal 128x128 map: every room is a square of 16 pixels (18 on the small floors) and between two rooms there
 * is a gap of 4 pixels that holds the door, or the room color if both rooms are one big room.
 *
 * Every room, door and its color is read straight from the map pixels. Everything that was seen during the run is
 * kept, so the map stays complete even if a part of the real map is hidden or redrawn.
 */
public final class DungeonMap {
	public static final int MAP_PIXELS = 128;
	public static final int WIDTH = MAP_PIXELS;
	public static final int HEIGHT = MAP_PIXELS + 12; // 12 = title strip

	private static final int HOTBAR_LAST = 8;
	private static final int ENTRANCE = 30;      // map color of the green start room
	private static final int DOOR_GAP = 4;
	private static final int MAX_CELLS = 6;
	private static final int MISSING_TICKS = 100; // map gone for 5 s = the run is over

	// run state
	private static boolean inDungeon = false;
	private static int missingTicks = 0;
	private static int mapId = Integer.MIN_VALUE;
	private static final byte[] seen = new byte[MAP_PIXELS * MAP_PIXELS]; // all colors ever seen in this run
	private static boolean calibrated = false;
	private static int size = 16;
	private static int pitch = 16 + DOOR_GAP;
	private static int offX = 0;
	private static int offY = 0;
	private static int roomCount = 0;
	private static final List<float[]> markers = new ArrayList<>(); // x, y, 1 = you / 0 = others

	private DungeonMap() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				if (inDungeon || calibrated) {
					reset();
				}
				return;
			}
			if (client.player.tickCount % 5 == 0) {
				try {
					scan(client);
				} catch (RuntimeException e) {
					// a broken map frame must never crash the game
				}
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dungeon_map"),
				(graphics, deltaTracker) -> renderHud(graphics));
	}

	/** True while you are in a dungeon run (the Magic Map is in your last hotbar slot). */
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
		roomCount = 0;
		java.util.Arrays.fill(seen, (byte) 0);
		markers.clear();
	}

	// ======================================================================================
	// Scanning
	// ======================================================================================

	private static void scan(Minecraft client) {
		if (!SkyBlockDetector.featuresActive()) {
			if (inDungeon) {
				reset();
			}
			return;
		}
		ItemStack stack = client.player.getInventory().getItem(HOTBAR_LAST);
		MapId id = stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
		boolean magicMap = id != null && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("magic map");
		if (!magicMap) {
			if (inDungeon && ++missingTicks * 5 >= MISSING_TICKS) {
				reset();
			}
			return;
		}
		MapItemSavedData data = client.level.getMapData(id);
		if (data == null) {
			return;
		}
		if (id.id() != mapId) {
			reset(); // a new map = a new run
			mapId = id.id();
		}
		inDungeon = true;
		missingTicks = 0;

		// remember every pixel that is drawn right now
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
		roomCount = countRooms();

		markers.clear();
		for (MapDecoration decoration : data.getDecorations()) {
			boolean you = decoration.type().is(MapDecorationTypes.PLAYER);
			if (you || decoration.type().is(MapDecorationTypes.FRAME)) {
				markers.add(new float[]{(decoration.x() + 128) / 2f, (decoration.y() + 128) / 2f, you ? 1f : 0f});
			}
		}
	}

	/** Finds the green start room: its top left corner and its edge length give the grid of all rooms. */
	private static void calibrate() {
		for (int y = 0; y < MAP_PIXELS; y++) {
			for (int x = 0; x < MAP_PIXELS; x++) {
				if ((seen[y * MAP_PIXELS + x] & 0xFF) != ENTRANCE) {
					continue;
				}
				int run = 0;
				while (x + run < MAP_PIXELS && (seen[y * MAP_PIXELS + x + run] & 0xFF) == ENTRANCE) {
					run++;
				}
				if (run < 12) {
					x += run; // a thin green line (a door), not the start room
					continue;
				}
				size = run >= 18 ? 18 : 16;
				pitch = size + DOOR_GAP;
				offX = x % pitch;
				offY = y % pitch;
				calibrated = true;
				return;
			}
		}
	}

	private static int cells(int offset) {
		return Math.max(0, Math.min(MAX_CELLS, (MAP_PIXELS - offset - size) / pitch + 1));
	}

	private static int pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= MAP_PIXELS || y >= MAP_PIXELS) {
			return 0;
		}
		return seen[y * MAP_PIXELS + x] & 0xFF;
	}

	private static int countRooms() {
		int count = 0;
		for (int row = 0; row < cells(offY); row++) {
			for (int col = 0; col < cells(offX); col++) {
				if (pixel(offX + col * pitch + 1, offY + row * pitch + 1) != 0) {
					count++;
				}
			}
		}
		return count;
	}

	// ======================================================================================
	// Drawing
	// ======================================================================================

	private static void renderHud(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled() || !inDungeon || client.gui.screen() instanceof HudEditorScreen) {
			return;
		}
		int[] pos = HudTrackers.position(HudTrackers.MAP_KEY, 0,
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		HudTrackers.draw(graphics, client.font, HudTrackers.MAP_KEY, pos[0], pos[1]);
	}

	/** Draws the map box (x, y = top left corner) in its normal size. preview = sample rooms when there is no run. */
	public static void drawUnscaled(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean preview) {
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xA0101014);
		graphics.outline(x, y, WIDTH, HEIGHT, 0x80555560);
		int mapY = y + 12;

		boolean sample = preview && !calibrated;
		String title = "Dungeon Map";
		graphics.text(font, title, x + 4, y + 2, 0xFFE0E0E0, true);
		String right = sample ? "preview" : roomCount + (roomCount == 1 ? " room" : " rooms");
		graphics.text(font, right, x + WIDTH - 4 - font.width(right), y + 2, 0xFFFFAA00, true);

		if (sample) {
			drawSample(graphics, x, mapY);
			return;
		}
		if (!calibrated) {
			graphics.text(font, "Scanning...", x + 4, mapY + 4, 0xFFAAAAAA, true);
			return;
		}

		int cols = cells(offX);
		int rows = cells(offY);
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int px = offX + col * pitch;
				int py = offY + row * pitch;
				int roomColor = argb(pixel(px + 1, py + 1));
				if (roomColor == 0) {
					continue;
				}
				graphics.fill(x + px, mapY + py, x + px + size, mapY + py + size, roomColor);

				// the gap to the right and below: as wide as the pixels in it (a whole side = one big room, less = a door)
				if (col + 1 < cols) {
					drawGap(graphics, x, mapY, px + size + DOOR_GAP / 2, py, true);
				}
				if (row + 1 < rows) {
					drawGap(graphics, x, mapY, px, py + size + DOOR_GAP / 2, false);
				}
				// the middle of a 2x2 room
				int middle = pixel(px + size + DOOR_GAP / 2, py + size + DOOR_GAP / 2);
				if (middle != 0) {
					graphics.fill(x + px + size, mapY + py + size, x + px + pitch, mapY + py + pitch, argb(middle));
				}
			}
		}

		for (float[] marker : markers) {
			int mx = x + Math.round(marker[0]);
			int my = mapY + Math.round(marker[1]);
			graphics.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
			graphics.fill(mx - 2, my - 2, mx + 2, my + 2, marker[2] > 0 ? 0xFFFFFFFF : 0xFF55FF55);
		}
	}

	/**
	 * Draws what is in the 4 pixel gap between two rooms. (lineX, lineY) is a pixel in the middle of the gap, the
	 * other coordinate is the start of the room; the gap is measured along the room edge.
	 */
	private static void drawGap(GuiGraphicsExtractor graphics, int x, int mapY, int lineX, int lineY, boolean horizontal) {
		int first = -1;
		int last = -1;
		for (int i = 0; i < size; i++) {
			int px = horizontal ? lineX : lineX + i;
			int py = horizontal ? lineY + i : lineY;
			if (pixel(px, py) != 0) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first < 0) {
			return;
		}
		int mid = (first + last) / 2;
		int color = argb(horizontal ? pixel(lineX, lineY + mid) : pixel(lineX + mid, lineY));
		if (horizontal) {
			graphics.fill(x + lineX - DOOR_GAP / 2, mapY + lineY + first, x + lineX + DOOR_GAP / 2, mapY + lineY + last + 1, color);
		} else {
			graphics.fill(x + lineX + first, mapY + lineY - DOOR_GAP / 2, x + lineX + last + 1, mapY + lineY + DOOR_GAP / 2, color);
		}
	}

	/** A few sample rooms so you can place the map with /parz hud before a run. */
	private static void drawSample(GuiGraphicsExtractor graphics, int x, int mapY) {
		int[][] layout = {{0, 1, 2, 3}, {0, 1, 1, 0}, {0, 2, 3, 2}};
		int[] colors = {0, 0xFF007C00, 0xFF7A4A27, 0xFFB02E2E};
		int s = 16;
		int p = s + DOOR_GAP;
		for (int row = 0; row < layout.length; row++) {
			for (int col = 0; col < layout[row].length; col++) {
				int c = colors[Math.min(layout[row][col], 3)];
				if (layout[row][col] == 0) {
					continue;
				}
				int px = x + 8 + col * p;
				int py = mapY + 8 + row * p;
				graphics.fill(px, py, px + s, py + s, c);
			}
		}
	}

	private static int argb(int packedId) {
		return packedId == 0 ? 0 : MapColor.getColorFromPackedId(packedId);
	}
}
