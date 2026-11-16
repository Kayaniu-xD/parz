package net.parz.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.parz.dashboard.DashboardData;
import net.parz.hud.HudTrackers;

import java.util.List;

/**
 * /parz hud - drag the pinned HUD trackers to where you want them.
 */
public class HudEditorScreen extends Screen {
	private String dragging = null;
	private int grabOffsetX;
	private int grabOffsetY;

	public HudEditorScreen() {
		super(Component.literal("Parz HUD Editor"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		DashboardData data = DashboardData.get();
		List<String> pinned = data.hudPinned;

		// move the element that is being dragged
		if (dragging != null) {
			int x = Math.max(0, Math.min(this.width - HudTrackers.WIDTH, mouseX - grabOffsetX));
			int y = Math.max(0, Math.min(this.height - HudTrackers.HEIGHT, mouseY - grabOffsetY));
			data.hudPositions.put(dragging, new int[]{x, y});
		}

		for (int i = 0; i < pinned.size(); i++) {
			String key = pinned.get(i);
			int[] pos = HudTrackers.position(key, i, this.width, this.height);
			HudTrackers.draw(graphics, this.font, key, pos[0], pos[1]);
			boolean hovered = isOver(mouseX, mouseY, pos);
			int color = key.equals(dragging) ? 0xFF55FFFF : hovered ? 0xFFFFFFFF : 0xFFFFAA00;
			graphics.outline(pos[0] - 1, pos[1] - 1, HudTrackers.WIDTH + 2, HudTrackers.HEIGHT + 2, color);
		}

		String title = "Parz HUD Editor";
		graphics.text(font, title, (this.width - font.width(title)) / 2, this.height / 2 - 30, 0xFFA87BFF, true);
		String hint = pinned.isEmpty()
				? "No trackers pinned. Open /parz-dash and click a skill or collection card."
				: "Drag the trackers to move them. Press ESC to save.";
		graphics.text(font, hint, (this.width - font.width(hint)) / 2, this.height / 2 - 16, 0xFFE0E0E0, true);

		String reset = "[ Reset positions ]";
		int rx = (this.width - font.width(reset)) / 2;
		int ry = this.height / 2;
		boolean resetHover = mouseX >= rx && mouseX < rx + font.width(reset) && mouseY >= ry - 2 && mouseY < ry + 10;
		graphics.text(font, reset, rx, ry, resetHover ? 0xFFFF7777 : 0xFFD83A3A, true);
	}

	private boolean isOver(double mouseX, double mouseY, int[] pos) {
		return mouseX >= pos[0] && mouseX < pos[0] + HudTrackers.WIDTH
				&& mouseY >= pos[1] && mouseY < pos[1] + HudTrackers.HEIGHT;
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
			DashboardData.save();
			return true;
		}

		List<String> pinned = DashboardData.get().hudPinned;
		// topmost (last drawn) first
		for (int i = pinned.size() - 1; i >= 0; i--) {
			String key = pinned.get(i);
			int[] pos = HudTrackers.position(key, i, this.width, this.height);
			if (isOver(mx, my, pos)) {
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
