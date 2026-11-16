package net.parz.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.parz.config.ParzConfigDefinition;
import net.parz.dashboard.DashboardData;
import net.parz.dashboard.DashboardTracker;
import net.parz.gui.HudEditorScreen;
import net.parz.skyblock.SkyBlockDetector;

import java.util.List;
import java.util.Locale;

/**
 * Draws the trackers you pinned in /parz-dash on the HUD.
 */
public final class HudTrackers {
	public static final int WIDTH = 150;
	public static final int HEIGHT = 41;
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;
	public static final float SCALE_STEP = 0.1f;

	private HudTrackers() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("parz", "trackers"),
				(graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (!ParzConfigDefinition.HUD_TRACKERS.isEnabled() || !SkyBlockDetector.featuresActive()) {
			return;
		}
		if (client.gui.screen() instanceof HudEditorScreen) {
			return; // the editor draws them itself
		}
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		List<String> pinned = DashboardData.get().hudPinned;
		for (int i = 0; i < pinned.size(); i++) {
			String key = pinned.get(i);
			int[] pos = position(key, i, screenW, screenH);
			draw(graphics, client.font, key, pos[0], pos[1]);
		}
	}

	/** Size of a tracker (1.0 = normal). */
	public static float scale(String key) {
		Float scale = DashboardData.get().hudScales.get(key);
		return scale == null ? 1f : Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
	}

	/** Makes a tracker bigger (steps > 0) or smaller (steps < 0). */
	public static void changeScale(String key, int steps) {
		float scale = Math.round((scale(key) + steps * SCALE_STEP) * 10f) / 10f;
		DashboardData.get().hudScales.put(key, Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)));
	}

	public static int scaledWidth(String key) {
		return Math.round(WIDTH * scale(key));
	}

	public static int scaledHeight(String key) {
		return Math.round(HEIGHT * scale(key));
	}

	/** Position of a tracker (default: stacked in the top-left corner), clamped to the screen. */
	public static int[] position(String key, int index, int screenW, int screenH) {
		int[] pos = DashboardData.get().hudPositions.get(key);
		int defaultY = 4;
		List<String> pinned = DashboardData.get().hudPinned;
		for (int i = 0; i < index && i < pinned.size(); i++) {
			defaultY += scaledHeight(pinned.get(i)) + 3;
		}
		int x = pos != null ? pos[0] : 4;
		int y = pos != null ? pos[1] : defaultY;
		x = Math.max(0, Math.min(screenW - scaledWidth(key), x));
		y = Math.max(0, Math.min(screenH - scaledHeight(key), y));
		return new int[]{x, y};
	}

	/** Draws one tracker box (x, y = top left corner) in its size. */
	public static void draw(GuiGraphicsExtractor graphics, Font font, String key, int x, int y) {
		float scale = scale(key);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		drawUnscaled(graphics, font, key, 0, 0);
		graphics.pose().popMatrix();
	}

	private static void drawUnscaled(GuiGraphicsExtractor graphics, Font font, String key, int x, int y) {
		DashboardData data = DashboardData.get();
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xA0101014);
		graphics.outline(x, y, WIDTH, HEIGHT, 0x80555560);

		DashboardData.Skill skill = data.skillByKey(key);
		DashboardData.Collection col = data.collectionByKey(key);
		if (skill == null && col == null) {
			graphics.text(font, "Unknown tracker", x + 4, y + 4, 0xFFAAAAAA, true);
			return;
		}

		String name;
		String right;
		float progress;
		String bottom;
		String extra;
		int barColor;
		if (skill != null) {
			graphics.item(DashboardData.iconFor(skill), x + 3, y + 3);
			name = skill.name;
			int maxLevel = skill.maxLevel();
			right = skill.maxed ? "MAX" : skill.level >= 0 ? "Lvl " + skill.level + (maxLevel > 0 ? "/" + maxLevel : "") : "";
			progress = skill.progress();
			barColor = 0xFFB060FF;
			StringBuilder sb = new StringBuilder();
			if (skill.maxed) {
				sb.append("Maxed");
			} else if (skill.percent >= 0) {
				sb.append(String.format(Locale.ROOT, "%.1f%%", skill.percent));
			} else {
				sb.append("No data yet");
			}
			if (ParzConfigDefinition.SKILL_XP_PER_HOUR.isEnabled()) {
				sb.append("  ").append(shortNumber(DashboardTracker.xpPerHour(skill.name))).append(" XP/h");
			}
			bottom = sb.toString();
			double toMax = skill.xpToMax();
			extra = skill.maxed || toMax == 0 ? "Maxed!" : toMax > 0 ? "To max: " + shortNumber(toMax) + " XP" : "";
		} else {
			graphics.item(DashboardData.iconFor(col), x + 3, y + 3);
			name = col.name;
			int maxTier = col.maxTier();
			right = "MAXED".equals(col.status) ? "MAX" : col.tierNumber > 0
					? col.tierNumber + (maxTier > 0 ? "/" + maxTier : "") : "";
			progress = col.progress();
			barColor = 0xFFFFAA00;
			if ("LOCKED".equals(col.status)) {
				bottom = "Locked";
			} else if ("UNKNOWN".equals(col.status)) {
				bottom = "Open /collections";
			} else if (col.needed > 0) {
				bottom = shortNumber(col.current) + " / " + shortNumber(col.needed)
						+ String.format(Locale.ROOT, "  (%.1f%%)", col.percent);
			} else {
				bottom = shortNumber(col.total) + " collected";
			}
			double toMax = col.neededToMax();
			extra = "MAXED".equals(col.status) ? "Maxed!" : toMax >= 0
					? "To max: " + shortNumber(toMax) + String.format(Locale.ROOT, " (%.0f%%)", col.maxProgress() * 100) : "";
		}

		graphics.text(font, name, x + 22, y + 3, 0xFFFFFFFF, true);
		if (!right.isEmpty()) {
			graphics.text(font, right, x + WIDTH - 4 - font.width(right), y + 3, 0xFFFFAA00, true);
		}
		int barX = x + 22;
		int barW = WIDTH - 26;
		graphics.fill(barX, y + 13, barX + barW, y + 17, 0xFF2A2A2A);
		int filled = Math.round(barW * Math.max(0f, Math.min(1f, progress)));
		if (filled > 0) {
			graphics.fill(barX, y + 13, barX + filled, y + 17, barColor);
		}
		graphics.text(font, bottom, x + 22, y + 20, 0xFFBBBBBB, true);
		if (!extra.isEmpty()) {
			graphics.text(font, extra, x + 22, y + 30, 0xFFFFD27F, true);
		}
	}

	public static String shortNumber(double value) {
		if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000);
		if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
		if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000);
		return String.format(Locale.ROOT, "%,.0f", value);
	}
}
