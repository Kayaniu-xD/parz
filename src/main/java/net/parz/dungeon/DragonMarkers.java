package net.parz.dungeon;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
 * Master Mode 7, the dragon phase of the boss fight: the arrow stack indicator, copied from the Wither Dragons
 * feature of NoammAddons. While a dragon is spawning (the flame particles of its statue) a cyan ring shows where to
 * aim your bow so the arrow lands on the stack position, including the drop of the arrow over the distance. The
 * ring is a circle that always faces you, is drawn through walls and gets bigger with the distance.
 */
public final class DragonMarkers {
	private enum State { DEAD, SPAWNING, ALIVE }

	/** A dragon: its name, where it spawns, the box it flies in, the area of its spawn particles and the stack position. */
	private static final class Dragon {
		final String name;
		final Vec3 spawn;
		final Vec3 stack;
		final double minX, minZ, maxX, maxZ;
		final double pMinX, pMaxX, pMinZ, pMaxZ;
		State state = State.DEAD;
		int timeToSpawn = 100;

		Dragon(String name, Vec3 spawn, Vec3 stack, double[] box, double[] particleRange) {
			this.name = name;
			this.spawn = spawn;
			this.stack = stack;
			this.minX = box[0];
			this.minZ = box[1];
			this.maxX = box[2];
			this.maxZ = box[3];
			this.pMinX = particleRange[0];
			this.pMaxX = particleRange[1];
			this.pMinZ = particleRange[2];
			this.pMaxZ = particleRange[3];
		}
	}

	// the same positions as NoammAddons (WitherDragonEnum and fixedStackPositions)
	private static final Dragon[] DRAGONS = {
			new Dragon("Power", new Vec3(27, 14, 59), new Vec3(28, 14, 58),
					new double[]{14.5, 45.5, 39.5, 70.5}, new double[]{24, 30, 56, 62}),
			new Dragon("Flame", new Vec3(85, 14, 56), new Vec3(85, 14, 56),
					new double[]{72, 47, 102, 77}, new double[]{82, 88, 53, 59}),
			new Dragon("Apex", new Vec3(27, 14, 94), new Vec3(27, 14, 90),
					new double[]{7, 80, 37, 110}, new double[]{23, 29, 91, 97}),
			new Dragon("Ice", new Vec3(84, 14, 94), new Vec3(84, 14, 97),
					new double[]{71.5, 82.5, 96.5, 107.5}, new double[]{82, 88, 91, 97}),
			new Dragon("Soul", new Vec3(56, 14, 125), new Vec3(56, 14, 125),
					new double[]{45.5, 113.5, 68.5, 136.5}, new double[]{53, 59, 122, 128})
	};
	private static final int INDICATOR_COLOR = 0xFF00FFFF;
	private static final double INDICATOR_SIZE = 2.0;
	private static final double INDICATOR_THICKNESS = 3.0;
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
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
				net.parz.Debug.tick("dragon timers", DragonMarkers::tick));
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
		for (Dragon dragon : DRAGONS) {
			lines.add(dragon.name + " dragon: " + dragon.state);
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
				resetStates();
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

		Vec3 eye = client.player.getEyePosition();
		int shown = 0;
		for (Dragon dragon : DRAGONS) {
			if (dragon.state == State.SPAWNING) {
				drawIndicator(graphics, dragon, eye);
				shown++;
			}
		}
		setState(shown == 0 ? "waiting for a dragon to spawn" : "showing " + shown + " arrow stack indicator(s)");
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

	// ---- the arrow stack indicator (copied from NoammAddons) ----

	/**
	 * Where to aim: the target moved up by the drop of the arrow on its way (a bow arrow flies at speed 3, loses 1%
	 * speed per tick and drops 0.05 per tick). Null if the target is out of reach.
	 */
	private static Vec3 calculateLead(Vec3 target, Vec3 eye) {
		double distToTargetSq = target.distanceToSqr(eye);
		double currentArrowDist = 0;
		double currentSpeed = 3.0;
		double currentYVel = 0;
		double drop = 0;
		for (int i = 0; i < 160; i++) {
			currentArrowDist += currentSpeed;
			currentSpeed *= 0.99;
			drop += currentYVel;
			currentYVel -= 0.05;
			currentYVel *= 0.99;
			if (currentArrowDist * currentArrowDist >= distToTargetSq) {
				return target.subtract(0, drop, 0);
			}
		}
		return null;
	}

	private static void drawIndicator(GuiGraphicsExtractor graphics, Dragon dragon, Vec3 eye) {
		Vec3 target = dragon.stack.add(0.5, 3.5, 0.5);
		Vec3 lead = calculateLead(target, eye);
		if (lead == null) {
			return;
		}
		double distance = eye.distanceTo(target);
		double radiusBlocks = INDICATOR_SIZE * Math.max(0.5, Math.sqrt(distance / 50.0));
		double[] centre = project(lead.x, lead.y, lead.z);
		if (centre == null) {
			return;
		}
		// the ring always faces you: its size is how far a point radiusBlocks above the middle is on the screen
		double[] above = project(lead.x + up.x() * radiusBlocks, lead.y + up.y() * radiusBlocks, lead.z + up.z() * radiusBlocks);
		double radius = above == null ? 12 : Math.hypot(above[0] - centre[0], above[1] - centre[1]);
		radius = Math.max(4, Math.min(300, radius));
		// the line is 2 * 3/40 blocks thick like in NoammAddons
		int thick = (int) Math.max(2, Math.round(radius * (2 * INDICATOR_THICKNESS / 40.0) / radiusBlocks));
		int half = thick / 2;
		int steps = (int) Math.max(48, Math.min(360, Math.round(Math.PI * 2 * radius / Math.max(1, thick * 0.7))));
		int cx = (int) Math.round(centre[0]);
		int cy = (int) Math.round(centre[1]);
		for (int i = 0; i < steps; i++) {
			double angle = Math.PI * 2 * i / steps;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(px - half, py - half, px - half + thick, py - half + thick, INDICATOR_COLOR);
		}
	}

	// ---- dragon states (the same checks as NoammAddons' DragonCheck) ----

	private static void resetStates() {
		for (Dragon dragon : DRAGONS) {
			dragon.state = State.DEAD;
			dragon.timeToSpawn = 100;
		}
	}

	/** The flame particles of a dragon statue (only these exact ones): the dragon starts to spawn. */
	public static void onParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) {
		if (!Minecraft.getInstance().isSameThread() || !ParzConfigDefinition.DRAGON_CIRCLES.isEnabled()) {
			return;
		}
		// the packet has other method names in Minecraft 26.2 (getX) and 26.3 (x, a record), so it is read by name
		Object particle = read(packet, "getParticle", "particle");
		if (!(particle instanceof net.minecraft.core.particles.ParticleOptions options)
				|| options.getType() != net.minecraft.core.particles.ParticleTypes.FLAME) return;
		double x = number(packet, "getX", "x");
		double y = number(packet, "getY", "y");
		double z = number(packet, "getZ", "z");
		if (x % 1 != 0.0 || z % 1 != 0.0) return;
		if (number(packet, "getCount", "count") != 20 || y != 19.0) return;
		if (number(packet, "getMaxSpeed", "xMaxSpeed") != 0 || number(packet, "yMaxSpeed", "getMaxSpeed") != 0
				|| number(packet, "zMaxSpeed", "getMaxSpeed") != 0) return;
		if (!Boolean.TRUE.equals(read(packet, "isOverrideLimiter", "overrideLimiter"))) return;
		if (number(packet, "getXDist", "xDist") != 2 || number(packet, "getYDist", "yDist") != 3
				|| number(packet, "getZDist", "zDist") != 2) return;
		for (Dragon dragon : DRAGONS) {
			if (dragon.state != State.SPAWNING) {
				if (x < dragon.pMinX || x > dragon.pMaxX || z < dragon.pMinZ || z > dragon.pMaxZ) {
					continue;
				}
				dragon.state = State.SPAWNING;
				dragon.timeToSpawn = 100;
				net.parz.Debug.log("dragons", () -> dragon.name + " dragon is spawning");
			}
		}
	}

	/** Calls the first method of the object that exists under one of the names. */
	private static Object read(Object target, String... names) {
		for (String name : names) {
			try {
				return target.getClass().getMethod(name).invoke(target);
			} catch (ReflectiveOperationException ignored) {
				// try the next name
			}
		}
		throw new IllegalStateException("no method " + String.join("/", names) + " in " + target.getClass().getName());
	}

	private static double number(Object target, String... names) {
		return ((Number) read(target, names)).doubleValue();
	}

	/** The dragon itself appears: it is alive, the indicator ends. */
	public static void onEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
		if (!Minecraft.getInstance().isSameThread() || packet.getType() != net.minecraft.world.entity.EntityTypes.ENDER_DRAGON) {
			return;
		}
		for (Dragon dragon : DRAGONS) {
			if (dragon.state == State.SPAWNING && packet.getX() >= dragon.minX && packet.getX() <= dragon.maxX
					&& packet.getZ() >= dragon.minZ && packet.getZ() <= dragon.maxZ) {
				dragon.state = State.ALIVE;
				dragon.timeToSpawn = 100;
				net.parz.Debug.log("dragons", () -> dragon.name + " dragon is alive");
				return;
			}
		}
	}

	/** Counts the spawn timer down (once per tick, like the server); a dragon that never appears is dropped. */
	private static void tick(Minecraft client) {
		for (Dragon dragon : DRAGONS) {
			if (dragon.state == State.SPAWNING) {
				dragon.timeToSpawn--;
				if (dragon.timeToSpawn <= -20) {
					dragon.state = State.DEAD;
					dragon.timeToSpawn = 100;
				}
			}
		}
	}
}
