package net.parz.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.parz.config.ParzConfigDefinition;
import net.parz.dashboard.DashboardData;
import net.parz.dashboard.DashboardData.Skill;
import net.parz.dashboard.DashboardTracker;
import net.parz.dashboard.PlaytimeFormat;
import net.parz.hud.HudTrackers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SkyBlock Personal Dashboard (/pz dash): a card grid for all skills and
 * collections (data from the tab list and /collection), plus stats.
 * Clicking a card pins it to the HUD.
 */
public class DashboardScreen extends Screen {
	// ---- colors (ARGB) ----------------------------------------------------------
	private static final int PANEL_BG = 0xF0202127;
	private static final int PANEL_BORDER = 0xFF4A4B55;
	private static final int BOX_BG = 0xFF1A1B20;
	private static final int BOX_BORDER_DARK = 0xFF0E0E11;
	private static final int BOX_BORDER_LIGHT = 0xFF3B3C45;
	private static final int CARD_BG = 0xFF3A3A42;
	private static final int CARD_BG_HOVER = 0xFF45454E;
	private static final int CARD_BORDER = 0xFF26262C;
	private static final int ELEMENT_BG = 0xFF2A2B32;
	private static final int ELEMENT_BG_HOVER = 0xFF33343C;
	private static final int ELEMENT_BORDER = 0xFF41424C;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF9A9A9A;
	private static final int TEXT_SELECTED = 0xFF55FFFF;
	private static final int TEXT_HEADER = 0xFFA87BFF;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int BLUE = 0xFF3F9FFF;

	private static final int MAX_WIDTH = 660;
	private static final int MAX_HEIGHT = 400;
	private static final int CARD_H = 52;
	private static final int GAP = 4;

	private enum Sort {
		AZ("A-Z"), PERCENT("%"), LEVEL("Tier");
		final String label;

		Sort(String label) {
			this.label = label;
		}
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Hit> fixedHits = new ArrayList<>();
	private final List<Hit> contentHits = new ArrayList<>();

	/** "Skills", a collection category or "Stats". */
	private static String lastTab = "Skills";
	private static Sort sort = Sort.AZ;
	private String tab = lastTab;

	private double scroll;
	private int maxScroll;

	// layout
	private int px, py, pw, ph;
	private int conX1, conY1, conX2, conY2;

	public DashboardScreen() {
		super(Component.literal("Parz Dashboard"));
	}

	private List<String> tabs() {
		List<String> list = new ArrayList<>();
		list.add("Skills");
		list.addAll(DashboardData.get().categories());
		list.add("Stats");
		return list;
	}

	@Override
	protected void init() {
		pw = Math.min(MAX_WIDTH, this.width - 20);
		ph = Math.min(MAX_HEIGHT, this.height - 20);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;

		conX1 = px + 8;
		conX2 = px + pw - 8;
		conY1 = py + 44;
		conY2 = py + ph - 18;

	}

	private void switchTab(String newTab) {
		if (!newTab.equals(tab)) {
			tab = newTab;
			lastTab = newTab;
			scroll = 0;
		}
	}

	// ======================================================================================
	// Rendering
	// ======================================================================================

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		fixedHits.clear();
		contentHits.clear();

		Mascot.draw(graphics, px, py, ph);
		graphics.fill(px, py, px + pw, py + ph, PANEL_BG);
		graphics.outline(px, py, pw, ph, PANEL_BORDER);

		// title
		String title = "Parz Dashboard - " + tab;
		graphics.text(font, title, px + (pw - font.width(title)) / 2, py + 7, 0xFFFFFFFF, true);

		// sort button (only for card grids)
		boolean grid = !"Stats".equals(tab);
		if (grid) {
			int bw = 34;
			int bx2 = px + pw - 8, bx1 = bx2 - bw, by1 = py + 4, by2 = py + 18;
			boolean hover = isInside(mouseX, mouseY, bx1, by1, bx2, by2);
			graphics.fill(bx1, by1, bx2, by2, hover ? 0xFF4A4B55 : 0xFF33343C);
			graphics.outline(bx1, by1, bw, by2 - by1, BOX_BORDER_LIGHT);
			graphics.text(font, sort.label, bx1 + (bw - font.width(sort.label)) / 2, by1 + 3, TEXT, true);
			fixedHits.add(new Hit(bx1, by1, bx2, by2, () -> sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]));
		}

		drawTabs(graphics, mouseX, mouseY);
		drawBox(graphics, conX1, conY1, conX2, conY2);

		int top = conY1;

		int startY = top + 5;
		int y = startY - (int) scroll;
		boolean mouseIn = isInside(mouseX, mouseY, conX1, top, conX2, conY2);
		graphics.enableScissor(conX1 + 1, top + 1, conX2 - 1, conY2 - 1);
		if ("Skills".equals(tab)) {
			y = drawSkillGrid(graphics, y, mouseX, mouseY, mouseIn);
		} else if ("Stats".equals(tab)) {
			y = drawStats(graphics, conX1 + 5, conX2 - 9, y);
		} else {
			y = drawCollectionGrid(graphics, tab, y, mouseX, mouseY, mouseIn);
		}
		graphics.disableScissor();

		int viewH = conY2 - top - 8;
		maxScroll = Math.max(0, (y + (int) scroll) - startY - viewH);
		scroll = clamp(scroll, 0, maxScroll);
		drawScrollbar(graphics, conX2 - 5, top + 2, conY2 - 2, viewH);

		// footer hint
		String hint = grid
				? "Click a card to pin/unpin it on your HUD  -  /pz hud to move HUD trackers"
				: "Stats are saved automatically";
		graphics.text(font, trim(hint, pw - 16), px + 8, py + ph - 13, TEXT_DIM, true);
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = px + 8;
		int y = py + 24;
		for (String t : tabs()) {
			int w = font.width(t) + 12;
			boolean selected = t.equals(tab);
			boolean hover = isInside(mouseX, mouseY, x, y, x + w, y + 15);
			graphics.fill(x, y, x + w, y + 15, selected ? 0xFF3B3C45 : hover ? 0xFF2E2F37 : 0xFF24252B);
			graphics.outline(x, y, w, 15, selected ? TEXT_SELECTED : BOX_BORDER_LIGHT);
			graphics.text(font, t, x + 6, y + 4, selected ? TEXT_SELECTED : TEXT, true);
			fixedHits.add(new Hit(x, y, x + w, y + 15, () -> switchTab(t)));
			x += w + 3;
		}
	}

	// ---- card grid ------------------------------------------------------------------

	private int columns() {
		return Math.max(2, (conX2 - conX1 - 14) / 150);
	}

	private int cardWidth(int cols) {
		return ((conX2 - conX1 - 14) - (cols - 1) * GAP) / cols;
	}

	private int drawSkillGrid(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY, boolean mouseIn) {
		List<Skill> skills = new ArrayList<>(DashboardData.get().skills.values());
		switch (sort) {
			case AZ -> skills.sort(Comparator.comparing(s -> s.name));
			case PERCENT -> skills.sort(Comparator.comparingDouble((Skill s) -> s.progress()).reversed());
			case LEVEL -> skills.sort(Comparator.comparingInt((Skill s) -> s.maxed ? 999 : s.level).reversed());
		}
		int cols = columns();
		int cw = cardWidth(cols);
		int i = 0;
		for (Skill skill : skills) {
			int x = conX1 + 5 + (i % cols) * (cw + GAP);
			int cy = y + (i / cols) * (CARD_H + GAP);
			drawSkillCard(graphics, skill, x, cy, cw, mouseX, mouseY, mouseIn);
			i++;
		}
		int rows = (skills.size() + cols - 1) / cols;
		return y + rows * (CARD_H + GAP);
	}

	private void drawSkillCard(GuiGraphicsExtractor graphics, Skill skill, int x, int y, int w,
							   int mouseX, int mouseY, boolean mouseIn) {
		String key = DashboardData.key(skill);
		boolean pinned = DashboardData.get().isPinned(key);
		boolean hover = mouseIn && isInside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		cardBackground(graphics, x, y, w, hover, pinned);
		graphics.item(DashboardData.iconFor(skill), x + 4, y + 4);

		int color = skill.maxed ? GOLD : skill.known() ? skillColor(skill.name) : TEXT_DIM;
		graphics.text(font, trim(skill.name, w - 60), x + 24, y + 5, color, true);
		int maxLevel = skill.maxLevel();
		String number = skill.maxed ? "MAX" : skill.level >= 0
				? skill.level + (maxLevel > 0 ? "/" + maxLevel : "") : "?";
		graphics.text(font, number, x + w - 4 - font.width(number), y + 4, color, true);
		String pct = skill.maxed ? "100%" : skill.percent >= 0 ? String.format(Locale.ROOT, "%.2f%%", skill.percent) : "";
		graphics.text(font, pct, x + w - 4 - font.width(pct), y + 14, color, true);
		bar(graphics, x + 4, y + 24, w - 8, skill.progress(), skillColor(skill.name));

		String line1;
		String line2 = "";
		if (!skill.known()) {
			line1 = "No data yet - open the tab list";
			line2 = "or gain " + skill.name + " XP";
		} else {
			double toMax = skill.xpToMax();
			if (skill.maxed || toMax == 0) {
				line1 = "Skill maxed!";
			} else if (toMax > 0) {
				line1 = "To max: " + HudTrackers.shortNumber(toMax) + " XP";
			} else {
				line1 = skill.needed > 0
						? HudTrackers.shortNumber(skill.current) + " / " + HudTrackers.shortNumber(skill.needed) + " XP"
						: "Progress to level " + (skill.level + 1);
			}
			if (ParzConfigDefinition.SKILL_XP_PER_HOUR.isEnabled()) {
				line2 = "XP/h: " + HudTrackers.shortNumber(DashboardTracker.xpPerHour(skill.name));
				if (skill.sessionXp > 0) {
					line2 += "  (+" + HudTrackers.shortNumber(skill.sessionXp) + " session)";
				}
			}
		}
		graphics.text(font, trim(line1, w - 8), x + 4, y + 32, TEXT_DIM, true);
		graphics.text(font, trim(line2, w - (pinned ? 30 : 8)), x + 4, y + 42, TEXT_DIM, true);
		pinTag(graphics, x, y, w, pinned);
		contentHits.add(new Hit(x, y, x + w, y + CARD_H, () -> DashboardData.get().togglePin(key)));
	}

	private int drawCollectionGrid(GuiGraphicsExtractor graphics, String category, int y, int mouseX, int mouseY, boolean mouseIn) {
		List<DashboardData.Collection> list = DashboardData.get().collectionsIn(category);
		switch (sort) {
			case AZ -> list.sort(Comparator.comparing(c -> c.name));
			case PERCENT -> list.sort(Comparator.comparingDouble((DashboardData.Collection c) -> c.progress()).reversed());
			case LEVEL -> list.sort(Comparator.comparingInt((DashboardData.Collection c) ->
					"MAXED".equals(c.status) ? 999 : c.tierNumber).reversed());
		}
		if (DashboardData.BOSS_CATEGORY.equalsIgnoreCase(category)) {
			// Floor 1-7 bosses in floor order (A-Z), Kuudra always at the end
			if (sort == Sort.AZ) {
				list.sort(Comparator.comparingInt(c -> DashboardData.bossIndex(c.name)));
			} else {
				list.sort(Comparator.comparingInt(c -> "Kuudra".equals(c.name) ? 1 : 0));
			}
		}
		if (list.isEmpty()) {
			centered(graphics, "No collections in this category yet - open /collections.", y + 6, TEXT_DIM);
			return y + 20;
		}
		// summary line for this category
		long scanned = list.stream().filter(c -> !"UNKNOWN".equals(c.status)).count();
		long maxedCount = list.stream().filter(c -> "MAXED".equals(c.status)).count();
		String header = category + ": " + maxedCount + "/" + list.size() + " maxed, " + scanned + " scanned";
		String fromMenu = DashboardData.get().categorySummary.get(category);
		if (fromMenu != null) {
			header += "   |   /collections: " + fromMenu;
		}
		graphics.text(font, trim(header, conX2 - conX1 - 20), conX1 + 6, y + 1, TEXT_HEADER, true);
		y += 14;

		int cols = columns();
		int cw = cardWidth(cols);
		int i = 0;
		for (DashboardData.Collection c : list) {
			int x = conX1 + 5 + (i % cols) * (cw + GAP);
			int cy = y + (i / cols) * (CARD_H + GAP);
			drawCollectionCard(graphics, c, x, cy, cw, mouseX, mouseY, mouseIn);
			i++;
		}
		int rows = (list.size() + cols - 1) / cols;
		return y + rows * (CARD_H + GAP);
	}

	private void drawCollectionCard(GuiGraphicsExtractor graphics, DashboardData.Collection c, int x, int y, int w,
									int mouseX, int mouseY, boolean mouseIn) {
		String key = DashboardData.key(c);
		boolean pinned = DashboardData.get().isPinned(key);
		boolean hover = mouseIn && isInside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		cardBackground(graphics, x, y, w, hover, pinned);
		graphics.item(DashboardData.iconFor(c), x + 4, y + 4);

		int color = switch (c.status) {
			case "MAXED" -> GOLD;
			case "PROGRESS" -> GREEN;
			case "LOCKED" -> RED;
			default -> TEXT_DIM;
		};
		graphics.text(font, trim(c.name, w - 60), x + 24, y + 5, color, true);
		int maxTier = c.maxTier();
		String number = "MAXED".equals(c.status) ? "MAX" : "UNKNOWN".equals(c.status) ? "?"
				: c.tierNumber + (maxTier > 0 ? "/" + maxTier : "");
		graphics.text(font, number, x + w - 4 - font.width(number), y + 4, color, true);
		String pct = c.percent >= 0 && !"UNKNOWN".equals(c.status) ? String.format(Locale.ROOT, "%.2f%%", c.percent) : "";
		graphics.text(font, pct, x + w - 4 - font.width(pct), y + 14, color, true);
		bar(graphics, x + 4, y + 24, w - 8, c.progress(), categoryColor(c.category));

		String line1;
		String line2;
		switch (c.status) {
			case "LOCKED" -> {
				line1 = "Locked - find this item";
				line2 = c.maxAmount() > 0 ? "To max: " + HudTrackers.shortNumber(c.maxAmount()) : "";
			}
			case "MAXED" -> {
				line1 = "Total: " + HudTrackers.shortNumber(c.total);
				line2 = "Collection maxed!";
			}
			case "PROGRESS" -> {
				if (DashboardData.BOSS_CATEGORY.equalsIgnoreCase(c.category)) {
					int floor = DashboardData.FLOOR_BOSSES.indexOf(c.name) + 1;
					line1 = c.needed > 0
							? "Next: " + HudTrackers.shortNumber(c.current) + " / " + HudTrackers.shortNumber(c.needed)
							: "Kills: " + HudTrackers.shortNumber(c.current);
					line2 = floor > 0 ? "Floor " + floor + "  -  Master run counts 2" : "";
					break;
				}
				line1 = c.needed > 0
						? "Next: " + HudTrackers.shortNumber(c.current) + " / " + HudTrackers.shortNumber(c.needed)
						: "Total: " + HudTrackers.shortNumber(c.total);
				double toMax = c.neededToMax();
				line2 = toMax >= 0
						? "To max: " + HudTrackers.shortNumber(toMax) + String.format(Locale.ROOT, " (%.0f%%)", c.maxProgress() * 100)
						: "Total: " + HudTrackers.shortNumber(c.total);
			}
			default -> {
				line1 = "Not scanned yet - open /collections";
				line2 = c.maxAmount() > 0 ? "Max at " + HudTrackers.shortNumber(c.maxAmount()) : "";
			}
		}
		graphics.text(font, trim(line1, w - 8), x + 4, y + 32, TEXT_DIM, true);
		graphics.text(font, trim(line2, w - (pinned ? 30 : 8)), x + 4, y + 42, TEXT_DIM, true);
		pinTag(graphics, x, y, w, pinned);
		contentHits.add(new Hit(x, y, x + w, y + CARD_H, () -> DashboardData.get().togglePin(key)));
	}

	private void cardBackground(GuiGraphicsExtractor graphics, int x, int y, int w, boolean hover, boolean pinned) {
		graphics.fill(x, y, x + w, y + CARD_H, hover ? CARD_BG_HOVER : CARD_BG);
		graphics.outline(x, y, w, CARD_H, pinned ? GOLD : CARD_BORDER);
	}

	private void pinTag(GuiGraphicsExtractor graphics, int x, int y, int w, boolean pinned) {
		if (pinned) {
			graphics.text(font, "HUD", x + w - 4 - font.width("HUD"), y + 42, GOLD, true);
		}
	}

	private void bar(GuiGraphicsExtractor graphics, int x, int y, int w, float progress, int color) {
		graphics.fill(x, y, x + w, y + 6, 0xFF1A1A1E);
		graphics.fill(x + 1, y + 1, x + w - 1, y + 5, 0xFF2C2C32);
		int filled = Math.round((w - 2) * Math.max(0f, Math.min(1f, progress)));
		if (filled > 0) {
			graphics.fill(x + 1, y + 1, x + 1 + filled, y + 5, color);
		}
	}

	private static int skillColor(String skill) {
		return switch (skill) {
			case "Farming" -> 0xFFE6C23A;
			case "Mining" -> 0xFF7FA6C8;
			case "Combat" -> 0xFFD84A4A;
			case "Foraging" -> 0xFF5EBD4E;
			case "Fishing" -> 0xFF3FB8C8;
			case "Enchanting" -> 0xFFB060FF;
			case "Alchemy" -> 0xFFE0609A;
			case "Taming" -> 0xFFE08A3A;
			case "Carpentry" -> 0xFFB08A5A;
			case "Runecrafting" -> 0xFFD070E0;
			case "Social" -> 0xFF55D58A;
			default -> 0xFF8A8AFF;
		};
	}

	private static int categoryColor(String category) {
		return switch (category) {
			case "Farming" -> 0xFFE6C23A;
			case "Mining" -> 0xFF7FA6C8;
			case "Combat" -> 0xFFD84A4A;
			case "Foraging" -> 0xFF5EBD4E;
			case "Fishing" -> 0xFF3FB8C8;
			default -> 0xFFB060FF;
		};
	}

	// ---- Stats ------------------------------------------------------------------------

	private int drawStats(GuiGraphicsExtractor graphics, int x1, int x2, int y) {
		DashboardData data = DashboardData.get();
		List<DashboardData.Collection> all = new ArrayList<>(data.collections.values());
		long scanned = all.stream().filter(c -> !"UNKNOWN".equals(c.status)).count();
		long maxed = all.stream().filter(c -> "MAXED".equals(c.status)).count();
		long locked = all.stream().filter(c -> "LOCKED".equals(c.status)).count();
		double sessionXp = data.skills.values().stream().mapToDouble(s -> s.sessionXp).sum();
		double avgLevel = data.skills.values().stream().filter(s -> s.level >= 0 && !"Runecrafting".equals(s.name)
				&& !"Social".equals(s.name)).mapToInt(s -> s.level).average().orElse(0);

		List<String[]> skillRows = new ArrayList<>();
		skillRows.add(new String[]{"Skill average", String.format(Locale.ROOT, "%.2f", avgLevel)});
		skillRows.add(new String[]{"Skill XP this session", HudTrackers.shortNumber(sessionXp)});
		if (ParzConfigDefinition.SKILL_XP_PER_HOUR.isEnabled()) {
			for (Skill s : data.skills.values()) {
				double rate = DashboardTracker.xpPerHour(s.name);
				if (rate > 0) {
					skillRows.add(new String[]{s.name + " XP/h", HudTrackers.shortNumber(rate)});
				}
			}
		}
		y = statSection(graphics, "Skills", x1, x2, y, skillRows.toArray(new String[0][]));

		y = statSection(graphics, "Collections", x1, x2, y, new String[][]{
				{"Known collections", String.valueOf(all.size())},
				{"Scanned from /collection", String.valueOf(scanned)},
				{"Maxed", String.valueOf(maxed)},
				{"Missing (not maxed)", String.valueOf(scanned - maxed)},
				{"Locked", String.valueOf(locked)}
		});
		long session = DashboardData.sessionPlaytimeMs();
		y = statSection(graphics, "Playtime", x1, x2, y, new String[][]{
				{"This session", PlaytimeFormat.format(session)},
				{"Total playtime", PlaytimeFormat.format(data.totalPlaytimeMs())}
		});
		return y;
	}

	private int statSection(GuiGraphicsExtractor graphics, String title, int x1, int x2, int y, String[][] rows) {
		int height = 16 + rows.length * 11 + 4;
		graphics.fill(x1, y, x2, y + height, ELEMENT_BG);
		graphics.outline(x1, y, x2 - x1, height, ELEMENT_BORDER);
		graphics.text(font, title, x1 + 6, y + 5, GOLD, true);
		int ry = y + 18;
		for (String[] row : rows) {
			graphics.text(font, row[0], x1 + 12, ry, TEXT_DIM, true);
			graphics.text(font, row[1], x2 - 8 - font.width(row[1]), ry, TEXT, true);
			ry += 11;
		}
		return y + height + 4;
	}

	// ======================================================================================
	// Helpers
	// ======================================================================================

	private void drawBox(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, BOX_BG);
		graphics.fill(x1, y1, x2, y1 + 1, BOX_BORDER_DARK);
		graphics.fill(x1, y1, x1 + 1, y2, BOX_BORDER_DARK);
		graphics.fill(x1, y2 - 1, x2, y2, BOX_BORDER_LIGHT);
		graphics.fill(x2 - 1, y1, x2, y2, BOX_BORDER_LIGHT);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int viewH) {
		if (maxScroll <= 0) {
			return;
		}
		int trackH = bottom - top;
		int thumbH = Math.max(12, trackH * viewH / (viewH + maxScroll));
		int thumbY = top + (int) ((trackH - thumbH) * (scroll / maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0xFF2A2A2A);
		graphics.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFF8A8A8A);
	}

	private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
		String t = trim(text, conX2 - conX1 - 16);
		graphics.text(font, t, (conX1 + conX2 - font.width(t)) / 2, y, color, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		this.setFocused(null);
		if (event.button() != 0) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		for (Hit hit : List.copyOf(fixedHits)) {
			if (hit.contains(mx, my)) {
				hit.action().run();
				return true;
			}
		}
		int top = conY1;
		if (isInside(mx, my, conX1, top, conX2, conY2)) {
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
		if (isInside(mouseX, mouseY, conX1, conY1, conX2, conY2)) {
			scroll = clamp(scroll - scrollY * 24, 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
}
