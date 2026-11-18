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
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

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
	private static long outOfDungeonSince = 0;
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel = null;

	// the camera of the frame that is drawn
	private static Vec3 cameraPos;
	private static Vector3fc up;
	/** The matrix the game itself draws the world with (the way you look and the real field of view). */
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static int screenW;
	private static int screenH;

	private DragonMarkers() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "dragon_markers"),
				net.parz.Debug.hud("dragon circles", DragonMarkers::render));
	}

	/** Lines for /pz dungeon. */
	public static List<String> debug(Minecraft client) {
		List<String> lines = new ArrayList<>();
		lines.add("dragon markers setting: " + ParzConfigDefinition.DRAGON_CIRCLES.isEnabled());
		if (client.player != null) {
			lines.add(String.format("your position: %.0f %.0f %.0f (dragon phase: %s)", client.player.getX(), client.player.getY(),
					client.player.getZ(), inDragonPhase(client)));
		}
		if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			lines.add("you look at the block " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
		}
		lines.add("dragon markers: " + lastState + (latched ? " [kept on]" : ""));
		return lines;
	}

	private static void setState(String state) {
		if (!state.equals(lastState)) {
			net.parz.Debug.log("dragons", () -> state);
		}
		lastState = state;
	}

	/** Ends the circles and says why in the chat, so a wrong end can be found. */
	private static void drop(Minecraft client, String reason) {
		latched = false;
		outOfDungeonSince = 0;
		setState("off (" + reason + ")");
		if (client.player != null) {
			client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Parz] Dragon circles off: " + reason));
		}
	}

	/** The boss room is at positive coordinates (the dungeon rooms are at negative ones); the dragons are low. */
	private static boolean inDragonPhase(Minecraft client) {
		return client.player != null && client.player.getX() > 0 && client.player.getZ() > 0
				&& client.player.getY() <= PHASE_5_MAX_Y;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!ParzConfigDefinition.DRAGON_CIRCLES.isEnabled()) {
			setState("off (setting)");
			return;
		}
		if (client.level == null || client.player == null) {
			latched = false;
			return;
		}
		if (client.level != lastLevel) {
			// a new world object: only a different place ends the circles, not a reload inside the boss fight
			lastLevel = client.level;
			if (latched && !DungeonInfo.inCatacombs()) {
				drop(client, "the world changed and you are not in a dungeon");
			}
		}
		// Once the dragon phase of M7 has been recognised the circles stay. The sidebar and your height change
		// during the fight (and when the Wither King spawns), so they are only looked at to start.
		if (!latched) {
			if (!SkyBlockDetector.featuresActive() || !DungeonInfo.inCatacombs()) {
				setState("off (not in a dungeon)");
				return;
			}
			if (!DungeonInfo.floor().equals("M7")) {
				setState("off (only Master Mode 7, this is " + (DungeonInfo.floor().isEmpty() ? "an unknown floor" : DungeonInfo.floor()) + ")");
				return;
			}
			if (!inDragonPhase(client)) {
				setState("off (not in the dragon phase of the boss fight)");
				return;
			}
			latched = true;
			net.parz.Debug.log("dragons", () -> "dragon phase of M7 recognised, the circles stay on");
			outOfDungeonSince = 0;
		}
		// they only end when you really are somewhere else: out of the dungeon for 30 seconds, or back in the rooms
		if (DungeonInfo.inCatacombs()) {
			outOfDungeonSince = 0;
		} else if (outOfDungeonSince == 0) {
			outOfDungeonSince = System.currentTimeMillis();
		} else if (System.currentTimeMillis() - outOfDungeonSince > 30_000) {
			drop(client, "you have not been in a dungeon for 30 seconds");
			return;
		}
		if (client.player.getX() < -5 && client.player.getZ() < -5) {
			drop(client, "you are back in the dungeon rooms");
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();
		cameraPos = camera.position();
		up = camera.upVector();
		// the projection of the game itself: also right for points far from the middle of the screen, and with
		// every field of view change (sprinting, speed, the FOV setting)
		camera.getViewRotationProjectionMatrix(VIEW_PROJECTION);
		screenW = client.getWindow().getGuiScaledWidth();
		screenH = client.getWindow().getGuiScaledHeight();

		Font font = client.font;
		for (Dragon dragon : DRAGONS) {
			drawNeckRing(graphics, font, dragon);
		}
		setState("showing the 5 circles");
	}

	// ---- projection ----

	/** A point of the world on the screen: {x, y, distance}, or null if it is behind you. */
	private static double[] project(double x, double y, double z) {
		CLIP.set((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z), 1f);
		VIEW_PROJECTION.transform(CLIP);
		if (CLIP.w <= 0.05f) {
			return null;
		}
		double ndcX = CLIP.x / CLIP.w;
		double ndcY = CLIP.y / CLIP.w;
		double distance = Math.sqrt((x - cameraPos.x) * (x - cameraPos.x) + (y - cameraPos.y) * (y - cameraPos.y)
				+ (z - cameraPos.z) * (z - cameraPos.z));
		return new double[]{(ndcX * 0.5 + 0.5) * screenW, (0.5 - ndcY * 0.5) * screenH, distance};
	}

	// ---- drawing ----

	/** A circle around the neck (the chin block) of the dragon statue. */
	private static void drawNeckRing(GuiGraphicsExtractor graphics, Font font, Dragon dragon) {
		double x = dragon.chin().getX() + 0.5;
		double y = dragon.chin().getY() + 0.5;
		double z = dragon.chin().getZ() + 0.5;
		double[] centre = project(x, y, z);
		if (centre == null) {
			return;
		}
		// the size of the ring: how far a point RING_RADIUS_BLOCKS above the middle is on the screen
		double[] above = project(x + up.x() * RING_RADIUS_BLOCKS, y + up.y() * RING_RADIUS_BLOCKS, z + up.z() * RING_RADIUS_BLOCKS);
		int cx = (int) Math.round(centre[0]);
		int cy = (int) Math.round(centre[1]);
		double radius = above == null ? 7 : Math.hypot(above[0] - centre[0], above[1] - centre[1]);
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
