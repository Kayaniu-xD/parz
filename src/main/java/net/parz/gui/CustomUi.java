package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** What the screens of /pz custom share: the colors, the panel, the two tabs and the simple buttons. */
final class CustomUi {
	static final int PANEL_W = 398;
	static final int PANEL_H = 218;
	static final int PAD = 12;

	static final int PANEL = 0xF0252B3B;
	static final int PANEL_EDGE = 0xFF5C6580;
	static final int BOX = 0xFF3A4157;
	static final int BOX_EDGE = 0xFF171A24;
	static final int BUTTON = 0xFF1B1F2C;
	static final int HOVER = 0xFF5A6280;
	static final int SELECTED_EDGE = 0xFFE8F0FF;
	static final int TEXT = 0xFFFFFFFF;
	static final int TEXT_DIM = 0xFFA6ADC4;

	static final int ARMOR_TAB = 0;
	static final int ITEM_TAB = 1;
	private static final String[] TABS = {"Armor", "Item"};
	private static final int TAB_W = 62;
	private static final int TAB_H = 16;

	private CustomUi() {
	}

	static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, PANEL);
		graphics.outline(x, y, w, h, PANEL_EDGE);
	}

	static void box(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, BOX);
		graphics.outline(x, y, w, h, BOX_EDGE);
	}

	static void button(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int w, int h, int mouseX, int mouseY) {
		graphics.fill(x, y, x + w, y + h, inside(mouseX, mouseY, x, y, w, h) ? HOVER : BUTTON);
		graphics.centeredText(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
	}

	/** The tabs at the top of the panel; note is the text on the right (who sees the changes). */
	static void tabs(GuiGraphicsExtractor graphics, Font font, int px, int py, int active, String note, int mouseX, int mouseY) {
		for (int i = 0; i < TABS.length; i++) {
			int x = px + PAD + i * (TAB_W + 4);
			int y = py + 8;
			graphics.fill(x, y, x + TAB_W, y + TAB_H, i == active ? 0xFF6A7088 : inside(mouseX, mouseY, x, y, TAB_W, TAB_H) ? HOVER : BOX);
			graphics.centeredText(font, TABS[i], x + TAB_W / 2, y + 4, TEXT);
			if (i == active) {
				graphics.fill(x + 6, y + 14, x + TAB_W - 6, y + 15, TEXT);
			}
		}
		graphics.text(font, note, px + PANEL_W - PAD - font.width(note), py + 12, TEXT_DIM, false);
	}

	/** Opens the other tab if it was clicked; true if so. */
	static boolean clickTab(double mx, double my, int px, int py, int active) {
		for (int i = 0; i < TABS.length; i++) {
			if (i != active && inside(mx, my, px + PAD + i * (TAB_W + 4), py + 8, TAB_W, TAB_H)) {
				Minecraft.getInstance().gui.setScreen(i == ARMOR_TAB ? new ArmorScreen() : new ItemScreen());
				return true;
			}
		}
		return false;
	}

	static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
