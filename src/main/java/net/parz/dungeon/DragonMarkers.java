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
 * Master Mode 7, the dragon phase of the boss fight: a circle on the neck (the chin block of the statue) of each
 * of the five dragons. The circles are fixed in the world and always there, also before a dragon has spawned and
 * after the relics were placed. Drawn on the screen from your own camera. (Boxes and beams like in Devonian were
 * removed: drawing their long lines pixel by pixel on the screen cost far too many frames.)
 */
public final class DragonMarkers {
	/** A dragon: name, color and the chin block of its statue (its neck). */
	private record Dragon(String name, int color, BlockPos chin) {
	}

	private static final Dragon[] DRAGONS = {
			new Dragon("Power", 0xFFFF5555, new BlockPos(32, 18, 59)),
			new Dragon("Apex", 0xFF55FF55, new BlockPos(32, 19, 94)),
			new Dragon("Soul", 0xFFAA00AA, new BlockPos(56, 18, 120)),
			new Dragon("Ice", 0xFF55FFFF, new BlockPos(79, 19, 94)),
			new Dragon("Flame", 0xFFFFAA00, new BlockPos(80, 19, 56))
	};
	private static final double RING_RADIUS_BLOCKS = 1.5;
	private static final int OUTLINE_COLOR = 0xFF000000;
	/** The dragon phase is below this height. */
	private static final double PHASE_5_MAX_Y = 45;

	private static String lastState = "not checked yet";
	private static boolean latched = false;
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel = null;

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
		lines.add("dragon markers: " + lastState + (latched ? " [kept on]" : ""));
		return lines;
	}

	/** The boss room is at positive coordinates (the dungeon rooms are at negative ones); the dragons are low. */
	private static boolean inDragonPhase(Minecraft client) {
		return client.player != null && client.player.getX() > 0 && client.player.getZ() > 0
				&& client.player.getY() <= PHASE_5_MAX_Y;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!ParzConfigDefinition.DRAGON_CIRCLES.isEnabled()) {
			lastState = "off (setting)";
			return;
		}
		if (client.level == null || client.player == null) {
			latched = false;
			return;
		}
		if (client.level != lastLevel) {
			lastLevel = client.level; // a new world: start again
			latched = false;
		}
		// Once the dragon phase of M7 has been recognised the circles stay until you leave the boss room. The
		// sidebar and your height change during the fight, so they are only looked at to start.
		if (!latched) {
			if (!SkyBlockDetector.featuresActive() || !DungeonInfo.inCatacombs()) {
				lastState = "off (not in a dungeon)";
				return;
			}
			if (!DungeonInfo.floor().equals("M7")) {
				lastState = "off (only Master Mode 7, this is " + (DungeonInfo.floor().isEmpty() ? "an unknown floor" : DungeonInfo.floor()) + ")";
				return;
			}
			if (!inDragonPhase(client)) {
				lastState = "off (not in the dragon phase of the boss fight)";
				return;
			}
			latched = true;
		}
		if (client.player.getX() <= 0 || client.player.getZ() <= 0) {
			latched = false; // back in the dungeon rooms
			lastState = "off (left the boss room)";
			return;
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
		for (Dragon dragon : DRAGONS) {
			drawNeckRing(graphics, font, dragon);
		}
		lastState = "showing the 5 circles (fov " + Math.round(fov) + ")";
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
		int steps = (int) Math.max(24, Math.min(90, Math.round(radius * 1.6)));
		for (int i = 0; i < steps; i++) {
			double angle = Math.PI * 2 * i / steps;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(px - 2, py - 2, px + 2, py + 2, OUTLINE_COLOR);
			graphics.fill(px - 1, py - 1, px + 1, py + 1, dragon.color());
		}
		graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, OUTLINE_COLOR);
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
		graphics.text(font, dragon.name(), cx - font.width(dragon.name()) / 2, (int) Math.round(cy + radius) + 4, dragon.color(), true);
	}
}
