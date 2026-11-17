package net.parz.dungeon;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Master Mode 7 (and Floor 7), the dragon phase of the boss fight: draws a circle on the screen at each of the
 * five places where the dragons come out (Power, Flame, Apex, Ice, Soul). The places are fixed, so the circles
 * are there before a dragon spawns. Only your own camera and position are used for this.
 */
public final class DragonMarkers {
	/** A dragon place: the middle of the spot, name and color. */
	private record Spot(String name, double x, double y, double z, int color) {
	}

	private static final Spot[] SPOTS = {
			new Spot("Power", 27.5, 19, 59.5, 0xFFFF5555),
			new Spot("Flame", 85.5, 19, 56.5, 0xFFFFAA00),
			new Spot("Apex", 27.5, 19, 94.5, 0xFF55FF55),
			new Spot("Ice", 84.5, 19, 94.5, 0xFF55FFFF),
			new Spot("Soul", 56.5, 19, 125.5, 0xFFC25BFF)
	};
	private static final double RING_RADIUS_BLOCKS = 3.0;
	private static final int OUTLINE_COLOR = 0xFF000000;
	/** The dragon phase is below this height. */
	private static final double PHASE_5_MAX_Y = 45;

	private static String lastState = "not checked yet";

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
		lines.add("dragon circles setting: " + ParzConfigDefinition.DRAGON_CIRCLES.isEnabled());
		if (client.player != null) {
			lines.add(String.format("your position: %.0f %.0f %.0f (dragon phase: %s)", client.player.getX(), client.player.getY(),
					client.player.getZ(), inDragonPhase(client)));
		}
		lines.add("dragon circles: " + lastState);
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
		if (!SkyBlockDetector.featuresActive() || !DungeonInfo.inCatacombs()) {
			lastState = "off (not in a dungeon)";
			return;
		}
		if (!DungeonInfo.floor().isEmpty() && !DungeonInfo.isFloor7()) {
			lastState = "off (floor " + DungeonInfo.floor() + ")";
			return;
		}
		if (client.level == null || client.player == null || !inDragonPhase(client)) {
			lastState = "off (not in the dragon phase of the boss fight)";
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cameraPos = camera.position();
		Quaternionf rotation = camera.rotation();
		Vector3f forward = new Vector3f(0, 0, 1).rotate(rotation);
		Vector3f left = new Vector3f(1, 0, 0).rotate(rotation);
		Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);

		float fov = camera.getFov();
		if (fov <= 0.01f) {
			fov = client.options.fov().get();
		} else if (fov < 3.2f) {
			fov = (float) Math.toDegrees(fov); // radians
		}
		double tan = Math.tan(Math.toRadians(fov) / 2.0);
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		double aspect = screenW / (double) screenH;

		int shown = 0;
		Font font = client.font;
		for (Spot spot : SPOTS) {
			double dx = spot.x() - cameraPos.x;
			double dy = spot.y() - cameraPos.y;
			double dz = spot.z() - cameraPos.z;
			double depth = dx * forward.x + dy * forward.y + dz * forward.z;
			if (depth < 0.5) {
				continue; // behind you
			}
			double side = dx * left.x + dy * left.y + dz * left.z;
			double height = dx * up.x + dy * up.y + dz * up.z;
			double screenX = (0.5 - side / depth / (tan * aspect) * 0.5) * screenW;
			double screenY = (0.5 - height / depth / tan * 0.5) * screenH;
			double radius = RING_RADIUS_BLOCKS / depth / tan * 0.5 * screenH;
			radius = Math.max(7, Math.min(120, radius));
			drawRing(graphics, (int) Math.round(screenX), (int) Math.round(screenY), (float) radius, spot.color());
			String label = spot.name();
			graphics.text(font, label, (int) Math.round(screenX) - font.width(label) / 2,
					(int) Math.round(screenY + radius) + 4, spot.color(), true);
			shown++;
		}
		lastState = "showing " + shown + " of " + SPOTS.length + " circles (fov " + Math.round(fov) + ")";
	}

	private static void drawRing(GuiGraphicsExtractor graphics, int cx, int cy, float radius, int color) {
		int steps = Math.max(40, Math.round(radius * 3f));
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
			graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
		}
		graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, OUTLINE_COLOR);
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
	}
}
