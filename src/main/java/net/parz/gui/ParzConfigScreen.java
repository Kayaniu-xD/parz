package net.parz.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.parz.config.ConfigModel.Category;
import net.parz.config.ConfigModel.Toggle;
import net.parz.config.ParzConfig;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu in the style of the SkyHanni / MoulConfig menu:
 * header, category list on the left, description bar with search and an
 * (empty) content area on the right.
 */
public class ParzConfigScreen extends Screen {
	// ---- colors (ARGB) ----------------------------------------------------------
	private static final int PANEL_BG = 0xF0202127;
	private static final int PANEL_BORDER = 0xFF4A4B55;
	private static final int BOX_BG = 0xFF1A1B20;
	private static final int BOX_BORDER_DARK = 0xFF0E0E11;
	private static final int BOX_BORDER_LIGHT = 0xFF3B3C45;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF8A8A8A;
	private static final int TEXT_SELECTED = 0xFF55FFFF;
	private static final int TEXT_HEADER = 0xFFA87BFF;
	private static final int TEXT_AUTHOR = 0xFFFF5555;
	private static final int ELEMENT_BG = 0xFF2A2B32;
	private static final int ELEMENT_BORDER = 0xFF41424C;
	private static final int TOGGLE_OFF = 0xFFD83A3A;
	private static final int TOGGLE_ON = 0xFF3FCF3F;
	private static final int OPTION_LEFT = 104;

	private static final int MAX_WIDTH = 560;
	private static final int MAX_HEIGHT = 360;
	private static final int CATEGORY_ROW = 17;

	private record Row(Category category, int depth) {
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Hit> categoryHits = new ArrayList<>();
	private final List<Hit> contentHits = new ArrayList<>();

	private Category selected = ParzConfigDefinition.CATEGORIES.isEmpty() ? null : ParzConfigDefinition.CATEGORIES.getFirst();
	private EditBox searchBox;
	private String query = "";

	private double categoryScroll;
	private int categoryMaxScroll;
	private double contentScroll;
	private int contentMaxScroll;

	// layout (recomputed in init)
	private int px, py, pw, ph;
	private int catX1, catY1, catX2, catY2;
	private int conX1, conY1, conX2, conY2;
	private int infoY;

	public ParzConfigScreen() {
		super(Component.literal("Parz"));
		for (Category category : ParzConfigDefinition.CATEGORIES) {
			for (Toggle toggle : category.toggles) {
				toggle.animation = toggle.isEnabled() ? 1f : 0f;
			}
		}
	}

	@Override
	protected void init() {
		pw = Math.min(MAX_WIDTH, this.width - 20);
		ph = Math.min(MAX_HEIGHT, this.height - 20);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;

		int leftWidth = Math.max(110, pw / 4);
		catX1 = px + 8;
		catX2 = catX1 + leftWidth;
		catY1 = py + 40;
		catY2 = py + ph - 8;

		conX1 = catX2 + 8;
		conX2 = px + pw - 8;
		infoY = py + 25;
		conY1 = py + 44;
		conY2 = py + ph - 8;

		searchBox = new EditBox(this.font, conX2 - 104, infoY + 4, 96, 10, Component.literal("Search"));
		searchBox.setBordered(false);
		searchBox.setMaxLength(50);
		searchBox.setValue(query);
		searchBox.setResponder(value -> {
			query = value.trim().toLowerCase(Locale.ROOT);
			categoryScroll = 0;
			contentScroll = 0;
			if (selected == null || !selected.matches(query)) {
				List<Row> rows = visibleRows();
				if (!rows.isEmpty()) {
					selected = rows.getFirst().category();
				}
			}
		});
		// only addWidget: we draw the box ourselves so it ends up on top of the panel
		this.addWidget(searchBox);
	}

	// ======================================================================================
	// Rendering
	// ======================================================================================

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		categoryHits.clear();
		contentHits.clear();

		// main panel
		graphics.fill(px, py, px + pw, py + ph, PANEL_BG);
		graphics.outline(px, py, pw, ph, PANEL_BORDER);

		drawHeader(graphics);
		drawCategories(graphics, mouseX, mouseY);
		drawInfoBar(graphics, mouseX, mouseY, delta);
		drawContent(graphics, mouseX, mouseY);
	}

	private void drawHeader(GuiGraphicsExtractor graphics) {
		int x1 = px + 4, y1 = py + 4, x2 = px + pw - 4, y2 = py + 20;
		drawBox(graphics, x1, y1, x2, y2);

		String version = FabricLoader.getInstance().getModContainer("parz")
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		String part1 = "Parz " + version + " by ";
		String part2 = "Pay2Loze";
		int total = font.width(part1) + font.width(part2);
		int tx = px + (pw - total) / 2;
		int ty = y1 + 4;
		graphics.text(font, part1, tx, ty, TEXT, true);
		graphics.text(font, part2, tx + font.width(part1), ty, TEXT_AUTHOR, true);

		// SkyBlock status on the right side of the header
		boolean sb = SkyBlockDetector.isOnSkyBlock();
		String status = sb ? "SkyBlock erkannt" : "Nicht auf SkyBlock";
		graphics.text(font, status, x2 - 6 - font.width(status), ty, sb ? 0xFF3FCF3F : 0xFF8A8A8A, true);
	}

	private void drawCategories(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String header = "Categories";
		graphics.text(font, header, catX1 + (catX2 - catX1 - font.width(header)) / 2, py + 27, TEXT_HEADER, true);

		drawBox(graphics, catX1, catY1, catX2, catY2);

		List<Row> rows = visibleRows();
		int viewHeight = catY2 - catY1 - 8;
		categoryMaxScroll = Math.max(0, rows.size() * CATEGORY_ROW - viewHeight);
		categoryScroll = clamp(categoryScroll, 0, categoryMaxScroll);

		graphics.enableScissor(catX1 + 1, catY1 + 1, catX2 - 1, catY2 - 1);
		int y = catY1 + 6 - (int) categoryScroll;
		int centerX = (catX1 + catX2) / 2;
		for (Row row : rows) {
			Category category = row.category();
			boolean isSelected = category == selected;
			boolean hovered = isInside(mouseX, mouseY, catX1, catY1, catX2, catY2)
					&& mouseY >= y - 3 && mouseY < y - 3 + CATEGORY_ROW;

			int color;
			if (isSelected) {
				color = TEXT_SELECTED;
			} else if (row.depth() > 0) {
				color = hovered ? TEXT : TEXT_DIM;
			} else {
				color = hovered ? 0xFFFFFFFF : TEXT;
			}

			if (!category.children.isEmpty()) {
				String arrow = category.expanded || !query.isEmpty() ? "▼" : "▶";
				graphics.text(font, arrow, catX1 + 8, y, TEXT, true);
			}

			int textWidth = font.width(category.name);
			int tx = centerX - textWidth / 2;
			graphics.text(font, category.name, tx, y, color, true);
			if (isSelected) {
				graphics.fill(tx, y + font.lineHeight, tx + textWidth, y + font.lineHeight + 1, TEXT_SELECTED);
			}

			categoryHits.add(new Hit(catX1, y - 3, catX2, y - 3 + CATEGORY_ROW, () -> {
				if (!category.children.isEmpty() && (selected == category || !category.expanded)) {
					category.expanded = !category.expanded;
				}
				if (selected != category) {
					selected = category;
					contentScroll = 0;
				}
			}));
			y += CATEGORY_ROW;
		}
		graphics.disableScissor();

		drawScrollbar(graphics, catX2 - 4, catY1 + 2, catY2 - 2, viewHeight, categoryScroll, categoryMaxScroll);
	}

	private void drawInfoBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		drawBox(graphics, conX1, infoY, conX2, infoY + 16);
		int maxWidth = (searchBox.getX() - 10) - (conX1 + 6);
		String info = selected == null ? "" : selected.description;
		graphics.text(font, trim(info, maxWidth), conX1 + 6, infoY + 4, TEXT, true);

		// search field
		int sx1 = searchBox.getX() - 4, sy1 = infoY + 2, sx2 = conX2 - 3, sy2 = infoY + 14;
		graphics.fill(sx1, sy1, sx2, sy2, 0xFF101014);
		graphics.outline(sx1, sy1, sx2 - sx1, sy2 - sy1, searchBox.isFocused() ? 0xFFAAAAAA : BOX_BORDER_LIGHT);
		if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
			graphics.text(font, "Search...", searchBox.getX(), searchBox.getY(), 0xFF6A6A6A, false);
		}
		searchBox.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawBox(graphics, conX1, conY1, conX2, conY2);
		if (selected == null) {
			return;
		}

		int x1 = conX1 + 5;
		int x2 = conX2 - 9;
		int startY = conY1 + 5;
		int y = startY - (int) contentScroll;
		boolean mouseInContent = isInside(mouseX, mouseY, conX1, conY1, conX2, conY2);

		graphics.enableScissor(conX1 + 1, conY1 + 1, conX2 - 1, conY2 - 1);
		boolean anything = false;
		for (Toggle toggle : selected.toggles) {
			if (!toggle.matches(query)) {
				continue;
			}
			anything = true;
			y += drawToggleOption(graphics, toggle, x1, x2, y, mouseX, mouseY, mouseInContent) + 3;
		}
		if (!anything) {
			String text = query.isEmpty() ? "No options in this category." : "No matching options.";
			graphics.text(font, text, (conX1 + conX2 - font.width(text)) / 2, conY1 + 12, TEXT_DIM, true);
		}
		graphics.disableScissor();

		int viewHeight = conY2 - conY1 - 8;
		int totalHeight = (y + (int) contentScroll) - startY;
		contentMaxScroll = Math.max(0, totalHeight - viewHeight);
		contentScroll = clamp(contentScroll, 0, contentMaxScroll);
		drawScrollbar(graphics, conX2 - 5, conY1 + 2, conY2 - 2, viewHeight, contentScroll, contentMaxScroll);
	}

	/** Draws one on/off option box and returns its height. */
	private int drawToggleOption(GuiGraphicsExtractor graphics, Toggle toggle, int x1, int x2, int y,
								 int mouseX, int mouseY, boolean mouseInContent) {
		List<String> nameLines = wrap(toggle.name, OPTION_LEFT - 8);
		List<String> descLines = wrap(toggle.description, (x2 - x1) - OPTION_LEFT - 10);

		int switchW = 48;
		int switchH = 8;
		int switchY = y + 6 + nameLines.size() * 10 + 4;
		int height = Math.max(Math.max(36, switchY - y + switchH + 8), descLines.size() * 10 + 12);

		graphics.fill(x1, y, x2, y + height, ELEMENT_BG);
		graphics.outline(x1, y, x2 - x1, height, ELEMENT_BORDER);

		// name (left column, centered)
		int ny = y + 6;
		for (String line : nameLines) {
			graphics.text(font, line, x1 + (OPTION_LEFT - font.width(line)) / 2, ny, TEXT, true);
			ny += 10;
		}

		// switch
		int switchX = x1 + (OPTION_LEFT - switchW) / 2;
		boolean hovered = mouseInContent && isInside(mouseX, mouseY, switchX - 3, switchY - 3, switchX + switchW + 3, switchY + switchH + 3);
		drawSwitch(graphics, toggle, switchX, switchY, switchW, switchH, hovered);
		contentHits.add(new Hit(switchX - 3, switchY - 3, switchX + switchW + 3, switchY + switchH + 3, toggle::flip));

		// description (right column, vertically centered)
		int dy = y + (height - descLines.size() * 10) / 2 + 1;
		for (String line : descLines) {
			graphics.text(font, line, x1 + OPTION_LEFT + 6, dy, TEXT, true);
			dy += 10;
		}
		return height;
	}

	private void drawSwitch(GuiGraphicsExtractor graphics, Toggle toggle, int x, int y, int w, int h, boolean hovered) {
		float target = toggle.isEnabled() ? 1f : 0f;
		toggle.animation += (target - toggle.animation) * 0.3f;
		if (Math.abs(target - toggle.animation) < 0.01f) {
			toggle.animation = target;
		}

		// track
		graphics.fill(x, y, x + w, y + h, 0xFF101010);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0xFF4A4A4A : 0xFF3A3A3A);
		for (int i = 1; i < 5; i++) {
			int sx = x + i * w / 5;
			graphics.fill(sx, y + 2, sx + 1, y + h - 2, 0xFF262626);
		}

		// knob: red = off (left), green = on (right)
		int knobW = 7;
		int kx = x + Math.round((w - knobW) * toggle.animation);
		int color = lerpColor(TOGGLE_OFF, TOGGLE_ON, toggle.animation);
		graphics.fill(kx, y - 2, kx + knobW, y + h + 2, 0xFF101010);
		graphics.fill(kx + 1, y - 1, kx + knobW - 1, y + h + 1, color);
		graphics.fill(kx + 1, y - 1, kx + knobW - 1, y, 0x55FFFFFF);
	}

	private void drawBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, BOX_BG);
		graphics.fill(x1, y1, x2, y1 + 1, BOX_BORDER_DARK);
		graphics.fill(x1, y1, x1 + 1, y2, BOX_BORDER_DARK);
		graphics.fill(x1, y2 - 1, x2, y2, BOX_BORDER_LIGHT);
		graphics.fill(x2 - 1, y1, x2, y2, BOX_BORDER_LIGHT);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int viewHeight, double scroll, int maxScroll) {
		if (maxScroll <= 0) {
			return;
		}
		int trackHeight = bottom - top;
		int thumbHeight = Math.max(12, trackHeight * viewHeight / (viewHeight + maxScroll));
		int thumbY = top + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0xFF2A2A2A);
		graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xFF8A8A8A);
	}

	// ======================================================================================
	// Input
	// ======================================================================================

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		this.setFocused(null); // clicking anywhere else removes focus from the search field
		if (event.button() != 0) {
			return false;
		}
		double mx = event.x();
		double my = event.y();

		if (isInside(mx, my, catX1, catY1, catX2, catY2)) {
			for (Hit hit : List.copyOf(categoryHits)) {
				if (hit.contains(mx, my)) {
					hit.action().run();
					return true;
				}
			}
		}
		if (isInside(mx, my, conX1, conY1, conX2, conY2)) {
			for (Hit hit : List.copyOf(contentHits)) {
				if (hit.contains(mx, my)) {
					hit.action().run();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isInside(mouseX, mouseY, catX1, catY1, catX2, catY2)) {
			categoryScroll = clamp(categoryScroll - scrollY * 17, 0, categoryMaxScroll);
			return true;
		}
		if (isInside(mouseX, mouseY, conX1, conY1, conX2, conY2)) {
			contentScroll = clamp(contentScroll - scrollY * 20, 0, contentMaxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void removed() {
		ParzConfig.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ======================================================================================
	// Helpers
	// ======================================================================================

	private List<Row> visibleRows() {
		List<Row> rows = new ArrayList<>();
		for (Category category : ParzConfigDefinition.CATEGORIES) {
			if (!category.matches(query)) {
				continue;
			}
			rows.add(new Row(category, 0));
			if (category.expanded || !query.isEmpty()) {
				for (Category child : category.children) {
					if (child.matches(query)) {
						rows.add(new Row(child, 1));
					}
				}
			}
		}
		return rows;
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (font.width(candidate) <= maxWidth || line.isEmpty()) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private String trim(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String result = text;
		while (!result.isEmpty() && font.width(result + "...") > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}
		return result + "...";
	}

	private static boolean isInside(double x, double y, int x1, int y1, int x2, int y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int lerpColor(int from, int to, float t) {
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
