package net.parz.dungeon;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Master Mode 7, the dragon phase of the boss fight. Like the dragon boxes of Devonian (GPL-3.0, behaviour only,
 * no code taken): for every dragon whose statue is still standing it shows
 * - a wire frame box around the area of the dragon, in the color of the dragon,
 * - a beam where the dragon's waypoint is,
 * - a circle on the statue's neck (the chin block), the spot where the dragon's neck is.
 * When a dragon is killed its statue disappears and everything of that dragon disappears with it. All of it is
 * fixed in the world, it is there before the dragons spawn. Drawn on the screen from your own camera.
 */
public final class DragonMarkers {
	/** A dragon: name, color, the chin block of its statue, its area, and the waypoint of its beam. */
	private record Dragon(String name, int color, BlockPos chin, double[] box, BlockPos waypoint) {
	}

	private static final Dragon[] DRAGONS = {
			new Dragon("Power", 0xFFFF5555, new BlockPos(32, 18, 59),
					new double[]{14.5, 13, 45.5, 37.5, 28, 70.5}, new BlockPos(27, 0, 56)),
			new Dragon("Apex", 0xFF55FF55, new BlockPos(32, 19, 94),
					new double[]{7, 8, 80, 37, 28, 110}, new BlockPos(27, 0, 92)),
			new Dragon("Soul", 0xFFAA00AA, new BlockPos(56, 18, 120),
					new double[]{45.5, 13, 113.5, 68.5, 23, 136.5}, new BlockPos(56, 0, 124)),
			new Dragon("Ice", 0xFF55FFFF, new BlockPos(79, 19, 94),
					new double[]{71.5, 16, 82.5, 96.5, 26, 107.5}, new BlockPos(82, 0, 96)),
			new Dragon("Flame", 0xFFFFAA00, new BlockPos(80, 19, 56),
					new double[]{72, 8, 47, 102, 28, 76}, new BlockPos(82, 0, 56))
	};
	private static final double RING_RADIUS_BLOCKS = 1.5;
	private static final double BEAM_TOP = 17;
	private static final double NEAR = 0.1;
	private static final int OUTLINE_COLOR = 0xFF000000;
	/** The dragon phase is below this height. */
	private static final double PHASE_5_MAX_Y = 45;

	private static final boolean[] dead = new boolean[DRAGONS.length];
	private static String lastState = "not checked yet";

	// the camera of the frame that is drawn
	private static Vec3 cameraPos;
	private static Vector3fc forward;
	private static Vector3fc left;
	private static Vector3fc up;
	private static double tan;
	private static double aspect;
	private static int screenW;
	private static int screenH;

	private DragonMarkers() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dragon_markers"),
				DragonMarkers::render);
	}

	/** Lines for /parz dungeon. */
	public static List<String> debug(Minecraft client) {
		List<String> lines = new ArrayList<>();
		lines.add("dragon markers setting: " + ParzConfigDefinition.DRAGON_CIRCLES.isEnabled());
		if (client.player != null) {
			lines.add(String.format("your position: %.0f %.0f %.0f (dragon phase: %s)", client.player.getX(), client.player.getY(),
					client.player.getZ(), inDragonPhase(client)));
		}
		StringBuilder alive = new StringBuilder();
		for (int i = 0; i < DRAGONS.length; i++) {
			alive.append(DRAGONS[i].name()).append(dead[i] ? " (statue gone)  " : "  ");
		}
		lines.add("dragons: " + alive.toString().trim());
		lines.add("dragon markers: " + lastState);
		return lines;
	}

	/** The boss room is at positive coordinates (the dungeon rooms are at negative ones); the dragons are low. */
	private static boolean inDragonPhase(Minecraft client) {
		return client.player != null && client.player.getX() > 0 && client.player.getZ() > 0
				&& client.player.getY() <= PHASE_5_MAX_Y;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!DungeonInfo.inCatacombs()) {
			java.util.Arrays.fill(dead, false); // a new run: all statues are standing again
		}
		if (!ParzConfigDefinition.DRAGON_CIRCLES.isEnabled()) {
			lastState = "off (setting)";
			return;
		}
		if (!SkyBlockDetector.featuresActive() || !DungeonInfo.inCatacombs()) {
			lastState = "off (not in a dungeon)";
			return;
		}
		if (!DungeonInfo.floor().equals("M7")) {
			lastState = "off (only Master Mode 7, this is " + (DungeonInfo.floor().isEmpty() ? "an unknown floor" : DungeonInfo.floor()) + ")";
			return;
		}
		if (client.level == null || client.player == null || !inDragonPhase(client)) {
			lastState = "off (not in the dragon phase of the boss fight)";
			return;
		}

		// a dragon is dead when the chin block of its statue is gone
		for (int i = 0; i < DRAGONS.length; i++) {
			BlockPos chin = DRAGONS[i].chin();
			if (!dead[i] && client.level.hasChunk(chin.getX() >> 4, chin.getZ() >> 4)
					&& client.level.getBlockState(chin).isAir()) {
				dead[i] = true;
			}
		}

		Camera camera = client.gameRenderer.mainCamera();
		cameraPos = camera.position();
		// the directions the camera looks / goes left / goes up, already in world coordinates
		forward = camera.forwardVector();
		left = camera.leftVector();
		up = camera.upVector();
		float fov = camera.getFov();
		if (fov <= 0.01f) {
			fov = client.options.fov().get();
		} else if (fov < 3.2f) {
			fov = (float) Math.toDegrees(fov); // radians
		}
		tan = Math.tan(Math.toRadians(fov) / 2.0);
		screenW = client.getWindow().getGuiScaledWidth();
		screenH = client.getWindow().getGuiScaledHeight();
		aspect = screenW / (double) screenH;

		Font font = client.font;
		int shown = 0;
		for (int i = 0; i < DRAGONS.length; i++) {
			if (dead[i]) {
				continue;
			}
			Dragon dragon = DRAGONS[i];
			drawBox(graphics, dragon.box(), dragon.color());
			drawLine(graphics, dragon.waypoint().getX() + 0.5, dragon.waypoint().getY(), dragon.waypoint().getZ() + 0.5,
					dragon.waypoint().getX() + 0.5, BEAM_TOP, dragon.waypoint().getZ() + 0.5, dragon.color());
			drawNeckRing(graphics, font, dragon);
			shown++;
		}
		lastState = "showing " + shown + " of " + DRAGONS.length + " dragons (fov " + Math.round(fov) + ")";
	}

	// ---- projection ----

	/** A point of the world in camera space: {side (left), height (up), depth (forward)}. */
	private static double[] toCamera(double x, double y, double z) {
		double dx = x - cameraPos.x;
		double dy = y - cameraPos.y;
		double dz = z - cameraPos.z;
		return new double[]{
				dx * left.x() + dy * left.y() + dz * left.z(),
				dx * up.x() + dy * up.y() + dz * up.z(),
				dx * forward.x() + dy * forward.y() + dz * forward.z()
		};
	}

	private static double screenX(double[] c) {
		return (0.5 - c[0] / c[2] / (tan * aspect) * 0.5) * screenW;
	}

	private static double screenY(double[] c) {
		return (0.5 - c[1] / c[2] / tan * 0.5) * screenH;
	}

	// ---- drawing ----

	private static void drawBox(GuiGraphicsExtractor graphics, double[] b, int color) {
		double[][] corner = new double[8][];
		for (int i = 0; i < 8; i++) {
			corner[i] = new double[]{(i & 1) == 0 ? b[0] : b[3], (i & 2) == 0 ? b[1] : b[4], (i & 4) == 0 ? b[2] : b[5]};
		}
		// 12 edges: corners that differ in exactly one of the three bits
		for (int i = 0; i < 8; i++) {
			for (int bit = 1; bit <= 4; bit <<= 1) {
				int j = i | bit;
				if (j != i) {
					drawLine(graphics, corner[i][0], corner[i][1], corner[i][2], corner[j][0], corner[j][1], corner[j][2], color);
				}
			}
		}
	}

	/** A line between two points of the world. The part behind the camera is cut off, the rest is clipped to the screen. */
	private static void drawLine(GuiGraphicsExtractor graphics, double ax, double ay, double az, double bx, double by, double bz, int color) {
		double[] a = toCamera(ax, ay, az);
		double[] b = toCamera(bx, by, bz);
		if (a[2] < NEAR && b[2] < NEAR) {
			return;
		}
		if (a[2] < NEAR) {
			a = lerp(a, b, (NEAR - a[2]) / (b[2] - a[2]));
		} else if (b[2] < NEAR) {
			b = lerp(b, a, (NEAR - b[2]) / (a[2] - b[2]));
		}
		double x0 = screenX(a);
		double y0 = screenY(a);
		double x1 = screenX(b);
		double y1 = screenY(b);

		// clip to the screen (Liang-Barsky) so that a long line costs no more than the part you see
		double t0 = 0;
		double t1 = 1;
		double dx = x1 - x0;
		double dy = y1 - y0;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x0, screenW - x0, y0, screenH - y0};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) {
					return;
				}
			} else {
				double t = q[i] / p[i];
				if (p[i] < 0) {
					t0 = Math.max(t0, t);
				} else {
					t1 = Math.min(t1, t);
				}
			}
		}
		if (t0 > t1) {
			return;
		}
		double sx = x0 + dx * t0;
		double sy = y0 + dy * t0;
		double ex = x0 + dx * t1;
		double ey = y0 + dy * t1;
		int steps = (int) Math.min(900, Math.max(1, Math.max(Math.abs(ex - sx), Math.abs(ey - sy)) / 2));
		for (int i = 0; i <= steps; i++) {
			int px = (int) Math.round(sx + (ex - sx) * i / steps);
			int py = (int) Math.round(sy + (ey - sy) * i / steps);
			graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
		}
	}

	private static double[] lerp(double[] from, double[] to, double t) {
		return new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t};
	}

	/** A circle around the neck (the chin block) of the dragon statue. */
	private static void drawNeckRing(GuiGraphicsExtractor graphics, Font font, Dragon dragon) {
		double[] c = toCamera(dragon.chin().getX() + 0.5, dragon.chin().getY() + 0.5, dragon.chin().getZ() + 0.5);
		if (c[2] < 0.5) {
			return;
		}
		int cx = (int) Math.round(screenX(c));
		int cy = (int) Math.round(screenY(c));
		double radius = RING_RADIUS_BLOCKS / c[2] / tan * 0.5 * screenH;
		radius = Math.max(7, Math.min(120, radius));
		int steps = Math.max(40, (int) Math.round(radius * 3));
		for (int i = 0; i < steps; i++) {
			double angle = Math.PI * 2 * i / steps;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(px - 2, py - 2, px + 2, py + 2, OUTLINE_COLOR);
		}
		for (int i = 0; i < steps; i++) {
			double angle = Math.PI * 2 * i / steps;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(px - 1, py - 1, px + 1, py + 1, dragon.color());
		}
		graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, OUTLINE_COLOR);
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
		graphics.text(font, dragon.name(), cx - font.width(dragon.name()) / 2, (int) Math.round(cy + radius) + 4, dragon.color(), true);
	}
}
