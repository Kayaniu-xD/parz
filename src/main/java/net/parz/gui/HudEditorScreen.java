package net.parz.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.parz.dashboard.DashboardData;
import net.parz.hud.HudTrackers;

import java.util.List;

/**
 * /pz hud - drag the pinned HUD trackers to where you want them.
 */
public class HudEditorScreen extends Screen {
	private static final int GRID = 20;      // distance between two grid lines
	private static final int SNAP = 5;       // how close (pixels) before an edge or the middle snaps to a line
	private String dragging = null;
	private int snapLineX = -1;              // grid line the dragged element is snapped to (-1 = none)
	private int snapLineY = -1;
	private int grabOffsetX;
	private int grabOffsetY;

	public HudEditorScreen() {
		super(Component.literal("Parz HUD Editor"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		DashboardData data = DashboardData.get();
		List<String> pinned = HudTrackers.editorKeys();

		drawGrid(graphics);

		// move the element that is being dragged; its edges and its middle snap to the grid and the screen middle
		snapLineX = -1;
		snapLineY = -1;
		if (dragging != null) {
			int w = HudTrackers.scaledWidth(dragging);
			int h = HudTrackers.scaledHeight(dragging);
			int x = Math.max(0, Math.min(this.width - w, mouseX - grabOffsetX));
			int y = Math.max(0, Math.min(this.height - h, mouseY - grabOffsetY));
			int[] sx = snap(x, w, this.width);
			int[] sy = snap(y, h, this.height);
			x = Math.max(0, Math.min(this.width - w, sx[0]));
			y = Math.max(0, Math.min(this.height - h, sy[0]));
			snapLineX = sx[1];
			snapLineY = sy[1];
			data.hudPositions.put(dragging, new int[]{x, y});
		}
		// the lines the element is snapped to light up
		if (snapLineX >= 0) {
			graphics.fill(snapLineX, 0, snapLineX + 1, this.height, 0xFF55FFFF);
		}
		if (snapLineY >= 0) {
			graphics.fill(0, snapLineY, this.width, snapLineY + 1, 0xFF55FFFF);
		}

		for (int i = 0; i < pinned.size(); i++) {
			String key = pinned.get(i);
			int[] pos = HudTrackers.position(key, i, this.width, this.height);
			HudTrackers.draw(graphics, this.font, key, pos[0], pos[1]);
			boolean hovered = isOver(mouseX, mouseY, key, pos);
			int color = key.equals(dragging) ? 0xFF55FFFF : hovered ? 0xFFFFFFFF : 0xFFFFAA00;
			graphics.outline(pos[0] - 1, pos[1] - 1, HudTrackers.scaledWidth(key) + 2, HudTrackers.scaledHeight(key) + 2, color);
			if (hovered || key.equals(dragging)) {
				String size = Math.round(HudTrackers.scale(key) * 100) + "%";
				graphics.text(font, size, pos[0], Math.max(0, pos[1] - 11), 0xFF55FFFF, true);
			}
		}

		String title = "Parz HUD Editor";
		graphics.text(font, title, (this.width - font.width(title)) / 2, this.height / 2 - 30, 0xFFA87BFF, true);
		String hint = pinned.isEmpty()
				? "Nothing to move. Pin a skill or collection in /pz dash, or turn on the Dungeon Map."
				: "Drag to move (it snaps to the grid and the middle lines), scroll to resize. Press ESC to save.";
		graphics.text(font, hint, (this.width - font.width(hint)) / 2, this.height / 2 - 16, 0xFFE0E0E0, true);

		String reset = "[ Reset positions ]";
		int rx = (this.width - font.width(reset)) / 2;
		int ry = this.height / 2;
		boolean resetHover = mouseX >= rx && mouseX < rx + font.width(reset) && mouseY >= ry - 2 && mouseY < ry + 10;
		graphics.text(font, reset, rx, ry, resetHover ? 0xFFFF7777 : 0xFFD83A3A, true);
	}

	/** The grid: thin lines every GRID pixels, brighter lines through the middle of the screen. */
	private void drawGrid(GuiGraphicsExtractor graphics) {
		for (int x = GRID; x < this.width; x += GRID) {
			graphics.fill(x, 0, x + 1, this.height, 0x20FFFFFF);
		}
		for (int y = GRID; y < this.height; y += GRID) {
			graphics.fill(0, y, this.width, y + 1, 0x20FFFFFF);
		}
		int middleX = this.width / 2;
		int middleY = this.height / 2;
		graphics.fill(middleX, 0, middleX + 1, this.height, 0x80FFAA00);
		graphics.fill(0, middleY, this.width, middleY + 1, 0x80FFAA00);
	}

	/**
	 * Moves an element a few pixels so that its left edge, right edge or middle lies exactly on a line: the screen
	 * middle, a screen edge or a grid line. Returns {new position, the line it snapped to or -1}.
	 */
	private static int[] snap(int position, int size, int total) {
		int bestDelta = SNAP + 1;
		int bestLine = -1;
		int[] anchors = {0, size / 2, size}; // left/top edge, middle, right/bottom edge
		for (int anchor : anchors) {
			int point = position + anchor;
			int[] lines = {0, total / 2, total, Math.round(point / (float) GRID) * GRID};
			for (int line : lines) {
				int delta = line - point;
				// the middle of the screen and the grid win over the screen edges when equally close
				if (Math.abs(delta) < Math.abs(bestDelta)) {
					bestDelta = delta;
					bestLine = line;
				}
			}
		}
		if (Math.abs(bestDelta) > SNAP) {
			return new int[]{position, -1};
		}
		return new int[]{position + bestDelta, bestLine};
	}

	private boolean isOver(double mouseX, double mouseY, String key, int[] pos) {
		return mouseX >= pos[0] && mouseX < pos[0] + HudTrackers.scaledWidth(key)
				&& mouseY >= pos[1] && mouseY < pos[1] + HudTrackers.scaledHeight(key);
	}

	/** Mouse wheel over a tracker: bigger / smaller. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<String> pinned = HudTrackers.editorKeys();
		for (int i = pinned.size() - 1; i >= 0; i--) {
			String key = pinned.get(i);
			int[] pos = HudTrackers.position(key, i, this.width, this.height);
			if (isOver(mouseX, mouseY, key, pos)) {
				if (scrollY != 0) {
					// pin the current position so the tracker grows from its top left corner
					DashboardData.get().hudPositions.put(key, pos);
					HudTrackers.changeScale(key, scrollY > 0 ? 1 : -1);
				}
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();

		String reset = "[ Reset positions ]";
		int rx = (this.width - font.width(reset)) / 2;
		int ry = this.height / 2;
		if (mx >= rx && mx < rx + font.width(reset) && my >= ry - 2 && my < ry + 10) {
			DashboardData.get().hudPositions.clear();
			DashboardData.get().hudScales.clear();
			DashboardData.save();
			return true;
		}

		List<String> pinned = HudTrackers.editorKeys();
		// topmost (last drawn) first
		for (int i = pinned.size() - 1; i >= 0; i--) {
			String key = pinned.get(i);
			int[] pos = HudTrackers.position(key, i, this.width, this.height);
			if (isOver(mx, my, key, pos)) {
				dragging = key;
				grabOffsetX = (int) mx - pos[0];
				grabOffsetY = (int) my - pos[1];
				DashboardData.get().hudPositions.put(key, pos);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			DashboardData.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void removed() {
		DashboardData.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
