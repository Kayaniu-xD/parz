package net.parz.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The dark open-book design that /pz and the custom /pets menu share: cover, two pages with a shadow
 * at the spine, page stack, ornaments, ribbon, page titles, dividers and plates.
 */
public final class BookStyle {
	// ---- colors (ARGB) ----------------------------------------------------------------
	public static final int COVER = 0xFF0B0B0E;
	public static final int COVER_EDGE = 0xFF33343C;
	public static final int COVER_TRIM = 0xFF1B1C21;
	public static final int PAGE_OUTER = 0xFF2F3036;
	public static final int PAGE_INNER = 0xFF1B1C20;
	public static final int PAGE_EDGE = 0xFF45464E;
	public static final int PAGE_STACK_A = 0xFF3B3C43;
	public static final int PAGE_STACK_B = 0xFF222328;
	public static final int ORNAMENT = 0xFF565761;
	public static final int RIBBON = 0xFF8A1F2D;
	public static final int RIBBON_DARK = 0xFF5E1520;

	public static final int PLATE = 0xFF33343A;
	public static final int PLATE_HOVER = 0xFF3D3E45;
	public static final int PLATE_SELECTED = 0xFF474851;
	public static final int PLATE_BORDER = 0xFF121316;
	public static final int PLATE_HIGHLIGHT = 0xFF4E4F58;

	public static final int TEXT = 0xFFE4E4EA;
	public static final int TEXT_DIM = 0xFF8E8F99;
	public static final int TEXT_TITLE = 0xFFC9CAD6;
	public static final int ACCENT = 0xFF6FD6E8;
	public static final int DIVIDER = 0xFF4A4B54;
	public static final int GREEN = 0xFF3FCF3F;

	private BookStyle() {
	}

	/** Where the book is: outer rectangle, the two pages (pgX1..pgX2 / pgY1..pgY2) and the spine position. */
	public record Frame(int px, int py, int pw, int ph, int pgX1, int pgY1, int pgX2, int pgY2, int mid) {
		/** A book centered on the screen, at most maxW x maxH, with sideMargin free on both sides. */
		public static Frame centered(int screenW, int screenH, int maxW, int maxH, int sideMargin) {
			int pw = Math.min(maxW, screenW - 2 * sideMargin);
			int ph = Math.min(maxH, screenH - 24);
			int px = (screenW - pw) / 2;
			int py = (screenH - ph) / 2 - 2;
			return new Frame(px, py, pw, ph, px + 7, py + 7, px + pw - 7, py + ph - 9, px + pw / 2);
		}

		public int leftX1() {
			return pgX1 + 12;
		}

		public int leftX2() {
			return mid - 16;
		}

		public int rightX1() {
			return mid + 16;
		}

		public int rightX2() {
			return pgX2 - 14;
		}
	}

	/** Cover, pages, spine shadow, page stack and corner ornaments. */
	public static void drawBook(GuiGraphicsExtractor graphics, Frame f) {
		int px = f.px(), py = f.py(), pw = f.pw(), ph = f.ph();
		int pgX1 = f.pgX1(), pgY1 = f.pgY1(), pgX2 = f.pgX2(), pgY2 = f.pgY2(), mid = f.mid();

		// cover
		graphics.fill(px, py, px + pw, py + ph, COVER);
		graphics.outline(px, py, pw, ph, COVER_EDGE);
		graphics.outline(px + 2, py + 2, pw - 4, ph - 4, COVER_TRIM);
		// cover corners
		int[][] corners = {{px, py}, {px + pw - 4, py}, {px, py + ph - 4}, {px + pw - 4, py + ph - 4}};
		for (int[] c : corners) {
			graphics.fill(c[0], c[1], c[0] + 4, c[1] + 4, COVER_EDGE);
			graphics.fill(c[0] + 1, c[1] + 1, c[0] + 3, c[1] + 3, COVER);
		}

		// page stack (layered paper edges below the pages)
		for (int i = 0; i < 5; i++) {
			int color = i % 2 == 0 ? PAGE_STACK_A : PAGE_STACK_B;
			graphics.fill(pgX1 + i, pgY2 + i, mid - 2, pgY2 + i + 1, color);
			graphics.fill(mid + 2, pgY2 + i, pgX2 - i, pgY2 + i + 1, color);
		}

		// pages: lighter at the outer edge, darker towards the spine
		gradientH(graphics, pgX1, pgY1, mid - 2, pgY2, PAGE_OUTER, PAGE_INNER);
		gradientH(graphics, mid + 2, pgY1, pgX2, pgY2, PAGE_INNER, PAGE_OUTER);
		graphics.outline(pgX1, pgY1, mid - 2 - pgX1, pgY2 - pgY1, PAGE_EDGE);
		graphics.outline(mid + 2, pgY1, pgX2 - (mid + 2), pgY2 - pgY1, PAGE_EDGE);

		// spine shadow
		graphics.fill(mid - 2, pgY1, mid + 2, pgY2 + 5, 0xFF050506);
		for (int i = 0; i < 16; i++) {
			int alpha = (int) (0xA0 * (1f - i / 16f));
			int color = alpha << 24;
			graphics.fill(mid - 3 - i, pgY1 + 1, mid - 2 - i, pgY2 - 1, color);
			graphics.fill(mid + 2 + i, pgY1 + 1, mid + 3 + i, pgY2 - 1, color);
		}

		// small corner ornaments on all four page corners
		ornament(graphics, pgX1 + 4, pgY1 + 4, 1, 1);
		ornament(graphics, pgX2 - 5, pgY1 + 4, -1, 1);
		ornament(graphics, pgX1 + 4, pgY2 - 5, 1, -1);
		ornament(graphics, pgX2 - 5, pgY2 - 5, -1, -1);
		ornament(graphics, mid - 8, pgY1 + 4, -1, 1);
		ornament(graphics, mid + 7, pgY1 + 4, 1, 1);
		ornament(graphics, mid - 8, pgY2 - 5, -1, -1);
		ornament(graphics, mid + 7, pgY2 - 5, 1, -1);
	}

	private static void ornament(GuiGraphicsExtractor graphics, int x, int y, int dx, int dy) {
		int len = 5;
		graphics.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len) + 1, y + 1, ORNAMENT);
		graphics.fill(x, Math.min(y, y + dy * len), x + 1, Math.max(y, y + dy * len) + 1, ORNAMENT);
	}

	public static void drawRibbon(GuiGraphicsExtractor graphics, Frame f) {
		int mid = f.mid();
		int x1 = mid - 4, x2 = mid + 4;
		int top = f.pgY2() - 6;
		int bottom = f.py() + f.ph() + 7;
		graphics.fill(x1, top, x2, bottom, RIBBON);
		graphics.fill(x1, top, x1 + 1, bottom, RIBBON_DARK);
		graphics.fill(x2 - 1, top, x2, bottom, RIBBON_DARK);
		// swallow-tail notch
		graphics.fill(mid - 1, bottom - 3, mid + 1, bottom, COVER);
	}

	// ---- text, dividers, plates --------------------------------------------------------

	public static void pageTitle(GuiGraphicsExtractor graphics, Font font, String title, int x1, int x2, int y) {
		graphics.text(font, title, (x1 + x2 - font.width(title)) / 2, y, TEXT_TITLE, true);
		titleDivider(graphics, x1, x2, y);
	}

	/** Title as a styled component (keeps its colors, e.g. a pet name in its rarity color). */
	public static void pageTitle(GuiGraphicsExtractor graphics, Font font, Component title, int x1, int x2, int y) {
		graphics.text(font, title, (x1 + x2 - font.width(title)) / 2, y, TEXT_TITLE, true);
		titleDivider(graphics, x1, x2, y);
	}

	private static void titleDivider(GuiGraphicsExtractor graphics, int x1, int x2, int y) {
		int ly = y + 13;
		int cx = (x1 + x2) / 2;
		// fading divider line with a small diamond in the middle
		for (int x = x1; x < x2; x++) {
			float distance = Math.abs(x - cx) / (float) ((x2 - x1) / 2);
			int alpha = (int) (0xFF * (1f - distance * 0.85f));
			graphics.fill(x, ly, x + 1, ly + 1, (alpha << 24) | (DIVIDER & 0xFFFFFF));
		}
		graphics.fill(cx - 1, ly - 2, cx + 2, ly + 3, PAGE_INNER);
		gem(graphics, cx, ly, ACCENT);
	}

	public static void footerDivider(GuiGraphicsExtractor graphics, int x1, int x2, int y) {
		int cx = (x1 + x2) / 2;
		for (int x = x1; x < x2; x++) {
			float distance = Math.abs(x - cx) / (float) ((x2 - x1) / 2);
			int alpha = (int) (0xB0 * (1f - distance));
			graphics.fill(x, y, x + 1, y + 1, (alpha << 24) | (DIVIDER & 0xFFFFFF));
		}
	}

	/** A small diamond, 5px wide, centered on (cx, cy). */
	public static void gem(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		graphics.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
		graphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
		graphics.fill(cx, cy - 1, cx + 1, cy, 0x66FFFFFF);
	}

	/** A button / list plate like the category rows of /pz. */
	public static void plate(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, boolean selected, boolean hovered, boolean enabled) {
		int bg = !enabled ? 0xFF222328 : selected ? PLATE_SELECTED : hovered ? PLATE_HOVER : PLATE;
		graphics.fill(x1, y1, x2, y2, bg);
		graphics.outline(x1, y1, x2 - x1, y2 - y1, hovered && enabled ? ACCENT : PLATE_BORDER);
		if (enabled) {
			graphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, PLATE_HIGHLIGHT);
		}
	}

	public static void gradientH(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int from, int to) {
		int width = x2 - x1;
		if (width <= 0) {
			return;
		}
		for (int x = 0; x < width; x += 2) {
			float t = x / (float) width;
			graphics.fill(x1 + x, y1, Math.min(x1 + x + 2, x2), y2, lerpColor(from, to, t));
		}
	}

	public static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int viewHeight, double scroll, int maxScroll) {
		if (maxScroll <= 0) {
			return;
		}
		int trackHeight = bottom - top;
		int thumbHeight = Math.max(12, trackHeight * viewHeight / (viewHeight + maxScroll));
		int thumbY = top + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0xFF141518);
		graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xFF7C7D88);
	}

	public static int lerpColor(int from, int to, float t) {
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
