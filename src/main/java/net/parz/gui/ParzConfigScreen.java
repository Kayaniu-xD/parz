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
import net.parz.update.ModUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The /parz menu as a dark, open book.
 * Left page: category list with search. Right page: the options of the selected category.
 * The tabs on the right edge of the book lead to the dashboard and the HUD editor.
 */
public class ParzConfigScreen extends Screen {
	// ---- colors (ARGB) ----------------------------------------------------------
	private static final int COVER = 0xFF0B0B0E;
	private static final int COVER_EDGE = 0xFF33343C;
	private static final int COVER_TRIM = 0xFF1B1C21;
	private static final int PAGE_OUTER = 0xFF2F3036;
	private static final int PAGE_INNER = 0xFF1B1C20;
	private static final int PAGE_EDGE = 0xFF45464E;
	private static final int PAGE_STACK_A = 0xFF3B3C43;
	private static final int PAGE_STACK_B = 0xFF222328;
	private static final int ORNAMENT = 0xFF565761;
	private static final int RIBBON = 0xFF8A1F2D;
	private static final int RIBBON_DARK = 0xFF5E1520;

	private static final int PLATE = 0xFF33343A;
	private static final int PLATE_HOVER = 0xFF3D3E45;
	private static final int PLATE_SELECTED = 0xFF474851;
	private static final int PLATE_BORDER = 0xFF121316;
	private static final int PLATE_HIGHLIGHT = 0xFF4E4F58;

	private static final int TEXT = 0xFFE4E4EA;
	private static final int TEXT_DIM = 0xFF8E8F99;
	private static final int TEXT_TITLE = 0xFFC9CAD6;
	private static final int ACCENT = 0xFF6FD6E8;
	private static final int TEXT_AUTHOR = 0xFFFF5555;
	private static final int DIVIDER = 0xFF4A4B54;
	private static final int TOGGLE_OFF = 0xFFD83A3A;
	private static final int TOGGLE_ON = 0xFF3FCF3F;

	private static final int MAX_WIDTH = 540;
	private static final int MAX_HEIGHT = 330;
	private static final int TAB_W = 24;
	private static final int ROW_H = 16;
	private static final int ROW_STEP = 19;
	private static final int SWITCH_W = 34;
	private static final int SWITCH_H = 8;

	// 8x8 pixel icons for the tabs
	private static final String[] ICON_GEAR = {
			"...##...",
			".#.##.#.",
			"..####..",
			"###..###",
			"###..###",
			"..####..",
			".#.##.#.",
			"...##..."};
	private static final String[] ICON_CHART = {
			"........",
			"......##",
			"......##",
			"...#..##",
			"...#..##",
			".#.#..##",
			".#.#..##",
			"########"};
	private static final String[] ICON_HUD = {
			"########",
			"#......#",
			"#.####.#",
			"#.#..#.#",
			"#.#..#.#",
			"#.####.#",
			"#......#",
			"########"};

	private record Tab(String label, String[] icon, int accent, Supplier<Screen> target) {
	}

	private static final List<Tab> TABS = List.of(
			new Tab("Config", ICON_GEAR, 0xFF6FD6E8, null),
			new Tab("Dashboard", ICON_CHART, 0xFFFFB84D, DashboardScreen::new),
			new Tab("HUD Editor", ICON_HUD, 0xFFB48CFF, HudEditorScreen::new));

	private record Row(Category category, int depth) {
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Hit> categoryHits = new ArrayList<>();
	private final List<Hit> contentHits = new ArrayList<>();
	private final List<Hit> tabHits = new ArrayList<>();

	private Category selected = ParzConfigDefinition.CATEGORIES.isEmpty() ? null : ParzConfigDefinition.CATEGORIES.getFirst();
	private EditBox searchBox;
	private String query = "";

	private double categoryScroll;
	private int categoryMaxScroll;
	private double contentScroll;
	private int contentMaxScroll;

	// layout (recomputed in init)
	private int px, py, pw, ph;
	private int pgX1, pgY1, pgX2, pgY2, mid;
	private int leftX1, leftX2, rightX1, rightX2;
	private int searchY, listY1, listY2;
	private int contentY1, contentY2;

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
		pw = Math.min(MAX_WIDTH, this.width - 2 * TAB_W - 16);
		ph = Math.min(MAX_HEIGHT, this.height - 24);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2 - 2;

		pgX1 = px + 7;
		pgX2 = px + pw - 7;
		pgY1 = py + 7;
		pgY2 = py + ph - 9;
		mid = px + pw / 2;

		leftX1 = pgX1 + 12;
		leftX2 = mid - 16;
		rightX1 = mid + 16;
		rightX2 = pgX2 - 14;

		searchY = pgY1 + 27;
		listY1 = pgY1 + 46;
		listY2 = pgY2 - 20;
		contentY1 = pgY1 + 40;
		contentY2 = pgY2 - 20;

		searchBox = new EditBox(this.font, leftX1 + 6, searchY + 3, leftX2 - leftX1 - 12, 10, Component.literal("Search"));
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
		// only addWidget: we draw the box ourselves so it ends up on top of the page
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
		tabHits.clear();

		drawTabs(graphics, mouseX, mouseY);
		drawBook(graphics);
		drawLeftPage(graphics, mouseX, mouseY, delta);
		drawRightPage(graphics, mouseX, mouseY);
		drawRibbon(graphics);
		drawTabTooltip(graphics, mouseX, mouseY);
	}

	/** Cover, pages, spine shadow, page stack and corner ornaments. */
	private void drawBook(GuiGraphicsExtractor graphics) {
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

	private void ornament(GuiGraphicsExtractor graphics, int x, int y, int dx, int dy) {
		int len = 5;
		graphics.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len) + 1, y + 1, ORNAMENT);
		graphics.fill(x, Math.min(y, y + dy * len), x + 1, Math.max(y, y + dy * len) + 1, ORNAMENT);
	}

	private void drawRibbon(GuiGraphicsExtractor graphics) {
		int x1 = mid - 4, x2 = mid + 4;
		int top = pgY2 - 6;
		int bottom = py + ph + 7;
		graphics.fill(x1, top, x2, bottom, RIBBON);
		graphics.fill(x1, top, x1 + 1, bottom, RIBBON_DARK);
		graphics.fill(x2 - 1, top, x2, bottom, RIBBON_DARK);
		// swallow-tail notch
		graphics.fill(mid - 1, bottom - 3, mid + 1, bottom, 0xFF0B0B0E);
	}

	// ---- left page --------------------------------------------------------------------

	private void drawLeftPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		pageTitle(graphics, "Categories", leftX1, leftX2, pgY1 + 10);

		// search field
		graphics.fill(leftX1, searchY, leftX2, searchY + 16, 0xFF121316);
		graphics.outline(leftX1, searchY, leftX2 - leftX1, 16, searchBox.isFocused() ? ACCENT : 0xFF3B3C45);
		if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
			graphics.text(font, "Search...", searchBox.getX(), searchBox.getY(), 0xFF666772, false);
		}
		searchBox.extractRenderState(graphics, mouseX, mouseY, delta);

		// category list
		List<Row> rows = visibleRows();
		int viewHeight = listY2 - listY1;
		categoryMaxScroll = Math.max(0, rows.size() * ROW_STEP - viewHeight);
		categoryScroll = clamp(categoryScroll, 0, categoryMaxScroll);

		graphics.enableScissor(leftX1 - 1, listY1, leftX2 + 1, listY2);
		int y = listY1 + 1 - (int) categoryScroll;
		boolean mouseInList = isInside(mouseX, mouseY, leftX1, listY1, leftX2, listY2);
		for (Row row : rows) {
			Category category = row.category();
			boolean isSelected = category == selected;
			int indent = row.depth() * 10;
			int x1 = leftX1 + indent;
			int x2 = leftX2 - 6;
			boolean hovered = mouseInList && isInside(mouseX, mouseY, x1, y, x2, y + ROW_H);

			graphics.fill(x1, y, x2, y + ROW_H, isSelected ? PLATE_SELECTED : hovered ? PLATE_HOVER : PLATE);
			graphics.outline(x1, y, x2 - x1, ROW_H, PLATE_BORDER);
			graphics.fill(x1 + 1, y + 1, x2 - 1, y + 2, PLATE_HIGHLIGHT);

			// gem
			int gemColor = isSelected ? ACCENT : hovered ? 0xFFB5B6C2 : 0xFF70717C;
			gem(graphics, x1 + 8, y + ROW_H / 2, gemColor);

			int textColor = isSelected ? 0xFFFFFFFF : row.depth() > 0 ? (hovered ? TEXT : TEXT_DIM) : TEXT;
			graphics.text(font, category.name, x1 + 16, y + (ROW_H - font.lineHeight) / 2 + 1, textColor, true);

			if (!category.children.isEmpty()) {
				String arrow = category.expanded || !query.isEmpty() ? "▼" : "▶";
				graphics.text(font, arrow, x2 - 4 - font.width(arrow), y + (ROW_H - font.lineHeight) / 2 + 1, TEXT_DIM, true);
			}

			categoryHits.add(new Hit(x1, y, x2, y + ROW_H, () -> {
				if (!category.children.isEmpty() && (selected == category || !category.expanded)) {
					category.expanded = !category.expanded;
				}
				if (selected != category) {
					selected = category;
					contentScroll = 0;
				}
			}));
			y += ROW_STEP;
		}
		graphics.disableScissor();
		drawScrollbar(graphics, leftX2 - 2, listY1, listY2, viewHeight, categoryScroll, categoryMaxScroll);

		// footer: SkyBlock status
		boolean sb = SkyBlockDetector.isOnSkyBlock();
		String status = sb ? "SkyBlock erkannt" : "Nicht auf SkyBlock";
		footerDivider(graphics, leftX1, leftX2, pgY2 - 17);
		graphics.text(font, status, (leftX1 + leftX2 - font.width(status)) / 2, pgY2 - 12, sb ? 0xFF3FCF3F : TEXT_DIM, true);
	}

	// ---- right page -------------------------------------------------------------------

	private void drawRightPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String title = selected == null ? "Parz" : selected.name;
		pageTitle(graphics, title, rightX1, rightX2, pgY1 + 10);

		// description under the title
		if (selected != null) {
			List<String> descLines = wrap(selected.description, rightX2 - rightX1);
			int dy = pgY1 + 27;
			for (String line : descLines) {
				graphics.text(font, line, (rightX1 + rightX2 - font.width(line)) / 2, dy, TEXT_DIM, true);
				dy += 10;
			}
			contentY1 = Math.max(pgY1 + 40, dy + 4);
		}

		int viewHeight = contentY2 - contentY1;
		boolean mouseInContent = isInside(mouseX, mouseY, rightX1 - 2, contentY1, rightX2 + 2, contentY2);

		if (selected != null) {
			int startY = contentY1 + 2;
			int y = startY - (int) contentScroll;
			graphics.enableScissor(rightX1 - 4, contentY1, rightX2 + 4, contentY2);
			boolean anything = false;
			if (selected.updatePage) {
				y += drawUpdateBlock(graphics, y, mouseX, mouseY, mouseInContent);
			}
			for (Toggle toggle : selected.toggles) {
				if (!toggle.matches(query)) {
					continue;
				}
				anything = true;
				y += drawToggleOption(graphics, toggle, y, mouseX, mouseY, mouseInContent);
			}
			if (!anything) {
				String text = query.isEmpty() ? "No options in this category." : "No matching options.";
				graphics.text(font, text, (rightX1 + rightX2 - font.width(text)) / 2, contentY1 + 14, TEXT_DIM, true);
			}
			graphics.disableScissor();

			int totalHeight = (y + (int) contentScroll) - startY;
			contentMaxScroll = Math.max(0, totalHeight - viewHeight + 4);
			contentScroll = clamp(contentScroll, 0, contentMaxScroll);
			drawScrollbar(graphics, rightX2 + 5, contentY1, contentY2, viewHeight, contentScroll, contentMaxScroll);
		}

		// footer: version + author
		String version = FabricLoader.getInstance().getModContainer("parz")
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		String part1 = "Parz " + version + " by ";
		String part2 = "Pay2Loze";
		int total = font.width(part1) + font.width(part2);
		int tx = (rightX1 + rightX2 - total) / 2;
		footerDivider(graphics, rightX1, rightX2, pgY2 - 17);
		graphics.text(font, part1, tx, pgY2 - 12, TEXT_DIM, true);
		graphics.text(font, part2, tx + font.width(part1), pgY2 - 12, TEXT_AUTHOR, true);
	}

	/** The version status and the buttons of the "Update" category. Returns its height. */
	private int drawUpdateBlock(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY, boolean mouseInContent) {
		int x1 = rightX1;
		int x2 = rightX2;
		ModUpdater.Status status = ModUpdater.status();
		String latest = ModUpdater.latestVersion();

		graphics.text(font, "Installed version:", x1 + 2, y + 8, TEXT_DIM, true);
		graphics.text(font, ModUpdater.currentVersion(), x1 + 2 + font.width("Installed version:  "), y + 8, TEXT, true);

		String text;
		int color;
		switch (status) {
			case CHECKING -> {
				text = "Checking GitHub...";
				color = TEXT_DIM;
			}
			case UP_TO_DATE -> {
				text = "You have the newest version.";
				color = TOGGLE_ON;
			}
			case AVAILABLE -> {
				text = "New version available: " + latest;
				color = 0xFFFFB84D;
			}
			case DOWNLOADING -> {
				text = "Downloading " + latest + "...";
				color = ACCENT;
			}
			case READY -> {
				text = "Version " + latest + " is downloaded. Restart Minecraft to install it.";
				color = TOGGLE_ON;
			}
			case ERROR -> {
				text = ModUpdater.message();
				color = TOGGLE_OFF;
			}
			default -> {
				text = "Not checked yet.";
				color = TEXT_DIM;
			}
		}
		int ty = y + 22;
		for (String line : wrap(text, (x2 - x1) - 4)) {
			graphics.text(font, line, x1 + 2, ty, color, true);
			ty += 10;
		}

		int by = ty + 6;
		boolean busy = status == ModUpdater.Status.CHECKING || status == ModUpdater.Status.DOWNLOADING;
		String checkLabel = "Check for updates";
		int checkW = font.width(checkLabel) + 16;
		drawButton(graphics, x1 + 2, by, checkW, checkLabel, !busy, mouseX, mouseY, mouseInContent, ModUpdater::check);

		boolean canUpdate = status == ModUpdater.Status.AVAILABLE;
		String updateLabel = status == ModUpdater.Status.READY ? "Downloaded"
				: latest.isEmpty() ? "Update" : "Update to " + latest;
		int updateW = font.width(updateLabel) + 16;
		drawButton(graphics, x1 + 2 + checkW + 6, by, updateW, updateLabel, canUpdate, mouseX, mouseY, mouseInContent,
				() -> ModUpdater.install(ModUpdater::toChat));

		int height = (by + 18 + 10) - y;
		for (int dx = x1; dx < x2; dx += 3) {
			graphics.fill(dx, y + height - 1, dx + 1, y + height, 0xFF3C3D45);
		}
		return height;
	}

	private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, String label, boolean enabled,
							int mouseX, int mouseY, boolean mouseInContent, Runnable action) {
		boolean hovered = enabled && mouseInContent && isInside(mouseX, mouseY, x, y, x + w, y + 16);
		int bg = !enabled ? 0xFF222328 : hovered ? PLATE_HOVER : PLATE;
		graphics.fill(x, y, x + w, y + 16, bg);
		graphics.outline(x, y, w, 16, hovered ? ACCENT : PLATE_BORDER);
		if (enabled) {
			graphics.fill(x + 1, y + 1, x + w - 1, y + 2, PLATE_HIGHLIGHT);
		}
		graphics.text(font, label, x + (w - font.width(label)) / 2, y + 4, enabled ? TEXT : 0xFF5C5D66, true);
		if (enabled) {
			contentHits.add(new Hit(x, y, x + w, y + 16, action));
		}
	}

	/** Draws one option (name, switch, description) and returns its height. */
	private int drawToggleOption(GuiGraphicsExtractor graphics, Toggle toggle, int y,
								 int mouseX, int mouseY, boolean mouseInContent) {
		int x1 = rightX1;
		int x2 = rightX2;
		List<String> nameLines = wrap(toggle.name, (x2 - x1) - SWITCH_W - 14);
		List<String> descLines = wrap(toggle.description, (x2 - x1) - 4);
		int height = 8 + nameLines.size() * 10 + 2 + descLines.size() * 10 + 8;

		boolean hovered = mouseInContent && isInside(mouseX, mouseY, x1 - 2, y, x2 + 2, y + height);
		if (hovered) {
			graphics.fill(x1 - 3, y, x2 + 3, y + height - 1, 0x16FFFFFF);
		}

		int ny = y + 8;
		for (String line : nameLines) {
			graphics.text(font, line, x1 + 2, ny, TEXT, true);
			ny += 10;
		}
		ny += 2;
		for (String line : descLines) {
			graphics.text(font, line, x1 + 2, ny, TEXT_DIM, true);
			ny += 10;
		}

		int switchX = x2 - SWITCH_W - 2;
		int switchY = y + 8;
		drawSwitch(graphics, toggle, switchX, switchY, SWITCH_W, SWITCH_H, hovered);

		// dotted separator
		for (int dx = x1; dx < x2; dx += 3) {
			graphics.fill(dx, y + height - 1, dx + 1, y + height, 0xFF3C3D45);
		}

		contentHits.add(new Hit(x1 - 2, y, x2 + 2, y + height, toggle::flip));
		return height;
	}

	private void drawSwitch(GuiGraphicsExtractor graphics, Toggle toggle, int x, int y, int w, int h, boolean hovered) {
		float target = toggle.isEnabled() ? 1f : 0f;
		toggle.animation += (target - toggle.animation) * 0.3f;
		if (Math.abs(target - toggle.animation) < 0.01f) {
			toggle.animation = target;
		}

		// track
		graphics.fill(x, y, x + w, y + h, 0xFF0C0C0E);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0xFF45464E : 0xFF35363D);
		for (int i = 1; i < 5; i++) {
			int sx = x + i * w / 5;
			graphics.fill(sx, y + 2, sx + 1, y + h - 2, 0xFF222328);
		}

		// knob: red = off (left), green = on (right)
		int knobW = 7;
		int kx = x + Math.round((w - knobW) * toggle.animation);
		int color = lerpColor(TOGGLE_OFF, TOGGLE_ON, toggle.animation);
		graphics.fill(kx, y - 2, kx + knobW, y + h + 2, 0xFF0C0C0E);
		graphics.fill(kx + 1, y - 1, kx + knobW - 1, y + h + 1, color);
		graphics.fill(kx + 1, y - 1, kx + knobW - 1, y, 0x55FFFFFF);
	}

	// ---- tabs on the right edge of the book -------------------------------------------

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tabH = 28;
		int gap = 5;
		int x = px + pw - 3;
		int y = py + 16;
		for (int i = 0; i < TABS.size(); i++) {
			Tab tab = TABS.get(i);
			boolean active = tab.target() == null;
			int ty = y + i * (tabH + gap);
			boolean hovered = isInside(mouseX, mouseY, x, ty, x + TAB_W + 6, ty + tabH);
			int extra = active ? 5 : hovered ? 3 : 0;
			int x2 = x + TAB_W + extra;

			graphics.fill(x, ty, x2, ty + tabH, active ? 0xFF35363E : hovered ? 0xFF2C2D34 : 0xFF202126);
			graphics.outline(x, ty, x2 - x, tabH, 0xFF08080A);
			graphics.fill(x + 1, ty + 1, x2 - 1, ty + 2, active ? 0xFF50515B : 0xFF34353C);
			// colored edge stripe
			graphics.fill(x2 - 3, ty + 2, x2 - 1, ty + tabH - 2, active || hovered ? tab.accent() : dim(tab.accent()));

			int iconColor = active || hovered ? tab.accent() : 0xFF9A9BA6;
			drawIcon(graphics, tab.icon(), x + 6 + extra / 2, ty + (tabH - 16) / 2, 2, iconColor);

			if (!active) {
				Supplier<Screen> target = tab.target();
				tabHits.add(new Hit(x, ty, x + TAB_W + 6, ty + tabH, () -> {
					if (this.minecraft != null) {
						this.minecraft.gui.setScreen(target.get());
					}
				}));
			}
		}
	}

	private void drawTabTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tabH = 28;
		int gap = 5;
		int x = px + pw - 3;
		int y = py + 16;
		for (int i = 0; i < TABS.size(); i++) {
			int ty = y + i * (tabH + gap);
			if (isInside(mouseX, mouseY, x, ty, x + TAB_W + 6, ty + tabH)) {
				String label = TABS.get(i).label();
				int w = font.width(label) + 10;
				int bx2 = x - 4;
				int bx1 = bx2 - w;
				int by1 = ty + (tabH - 16) / 2;
				graphics.fill(bx1, by1, bx2, by1 + 16, 0xF00B0B0E);
				graphics.outline(bx1, by1, w, 16, TABS.get(i).accent());
				graphics.text(font, label, bx1 + 5, by1 + 4, TEXT, true);
				return;
			}
		}
	}

	// ---- drawing helpers --------------------------------------------------------------

	private void pageTitle(GuiGraphicsExtractor graphics, String title, int x1, int x2, int y) {
		graphics.text(font, title, (x1 + x2 - font.width(title)) / 2, y, TEXT_TITLE, true);
		int ly = y + 13;
		int cx = (x1 + x2) / 2;
		// fading divider line with a small diamond in the middle
		for (int x = x1; x < x2; x++) {
			float distance = Math.abs(x - cx) / (float) ((x2 - x1) / 2);
			int alpha = (int) (0xFF * (1f - distance * 0.85f));
			graphics.fill(x, ly, x + 1, ly + 1, (alpha << 24) | (DIVIDER & 0xFFFFFF));
		}
		graphics.fill(cx - 1, ly - 2, cx + 2, ly + 3, 0xFF1B1C20);
		gem(graphics, cx, ly, ACCENT);
	}

	private void footerDivider(GuiGraphicsExtractor graphics, int x1, int x2, int y) {
		int cx = (x1 + x2) / 2;
		for (int x = x1; x < x2; x++) {
			float distance = Math.abs(x - cx) / (float) ((x2 - x1) / 2);
			int alpha = (int) (0xB0 * (1f - distance));
			graphics.fill(x, y, x + 1, y + 1, (alpha << 24) | (DIVIDER & 0xFFFFFF));
		}
	}

	/** A small diamond, 5px wide, centered on (cx, cy). */
	private void gem(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
		graphics.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
		graphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
		graphics.fill(cx, cy - 1, cx + 1, cy, 0x66FFFFFF);
	}

	private void drawIcon(GuiGraphicsExtractor graphics, String[] rows, int x, int y, int scale, int color) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			for (int c = 0; c < row.length(); c++) {
				if (row.charAt(c) == '#') {
					graphics.fill(x + c * scale, y + r * scale, x + (c + 1) * scale, y + (r + 1) * scale, color);
				}
			}
		}
	}

	private void gradientH(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int from, int to) {
		int width = x2 - x1;
		if (width <= 0) {
			return;
		}
		for (int x = 0; x < width; x += 2) {
			float t = x / (float) width;
			graphics.fill(x1 + x, y1, Math.min(x1 + x + 2, x2), y2, lerpColor(from, to, t));
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int viewHeight, double scroll, int maxScroll) {
		if (maxScroll <= 0) {
			return;
		}
		int trackHeight = bottom - top;
		int thumbHeight = Math.max(12, trackHeight * viewHeight / (viewHeight + maxScroll));
		int thumbY = top + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0xFF141518);
		graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xFF7C7D88);
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

		for (Hit hit : List.copyOf(tabHits)) {
			if (hit.contains(mx, my)) {
				hit.action().run();
				return true;
			}
		}
		if (isInside(mx, my, leftX1, listY1, leftX2, listY2)) {
			for (Hit hit : List.copyOf(categoryHits)) {
				if (hit.contains(mx, my)) {
					hit.action().run();
					return true;
				}
			}
		}
		if (isInside(mx, my, rightX1 - 2, contentY1, rightX2 + 2, contentY2)) {
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
		if (isInside(mouseX, mouseY, pgX1, pgY1, mid, pgY2)) {
			categoryScroll = clamp(categoryScroll - scrollY * ROW_STEP, 0, categoryMaxScroll);
			return true;
		}
		if (isInside(mouseX, mouseY, mid, pgY1, pgX2, pgY2)) {
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

	private static boolean isInside(double x, double y, int x1, int y1, int x2, int y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Darkens a color to roughly 55% brightness. */
	private static int dim(int color) {
		return lerpColor(color, 0xFF111114, 0.5f);
	}

	private static int lerpColor(int from, int to, float t) {
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
