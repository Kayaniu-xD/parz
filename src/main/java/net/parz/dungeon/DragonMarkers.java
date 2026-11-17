package net.parz.dungeon;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Master Mode 7 (and Floor 7): draws a circle on the screen at the neck of every living dragon, so you can see
 * where to hit it.
 */
public final class DragonMarkers {
	private static final float RING_RADIUS_BLOCKS = 1.6f;
	private static final int RING_COLOR = 0xFFFF4FD8;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int NECK_INDEX = 1; // getSubEntities(): head, neck, body, tail1-3, wing1-2

	private DragonMarkers() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dragon_markers"),
				DragonMarkers::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ParzConfigDefinition.DRAGON_CIRCLES.isEnabled() || !SkyBlockDetector.featuresActive() || !DungeonMap.inDungeon()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 cameraPos = camera.position();
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();

		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof EnderDragon dragon) || !dragon.isAlive()) {
				continue;
			}
			EnderDragonPart[] parts = dragon.getSubEntities();
			if (parts.length <= NECK_INDEX) {
				continue;
			}
			Vec3 neck = parts[NECK_INDEX].getPosition(partial).subtract(cameraPos);
			Vector4f clip = matrix.transform(new Vector4f((float) neck.x, (float) neck.y, (float) neck.z, 1f));
			if (clip.w <= 0.05f) {
				continue; // behind you
			}
			float centerX = (clip.x / clip.w * 0.5f + 0.5f) * screenW;
			float centerY = (0.5f - clip.y / clip.w * 0.5f) * screenH;
			float radius = RING_RADIUS_BLOCKS * matrix.m11() * (screenH / 2f) / clip.w;
			radius = Math.max(6f, Math.min(90f, radius));
			drawRing(graphics, Math.round(centerX), Math.round(centerY), radius);
		}
	}

	private static void drawRing(GuiGraphicsExtractor graphics, int cx, int cy, float radius) {
		int steps = Math.max(32, Math.round(radius * 3f));
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
			graphics.fill(px - 1, py - 1, px + 1, py + 1, RING_COLOR);
		}
		graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, OUTLINE_COLOR);
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
	}
}
