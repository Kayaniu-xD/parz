package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.parz.config.ParzConfigDefinition;
import net.parz.pets.PetCollection;
import net.parz.skyblock.SkyBlockDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The /pets menu in the Parz book design. It does not open a new screen: the real SkyBlock menu stays
 * open (so everything the server does keeps working) and is only drawn differently. A pet is selected in
 * the list on the left page, its details and the buttons are on the right page. Every click is sent to the
 * server as a normal click on the slot of the real menu.
 */
public final class PetsView {
	private static final int MAX_W = 540;
	private static final int MAX_H = 330;
	private static final int ROW_H = 17;
	private static final int ROW_STEP = 19;
	private static final Pattern PET_NAME = Pattern.compile("^([^\\[]{0,4})\\[Lvl \\d+] .+");
	private static final Pattern TITLE = Pattern.compile("^(\\(\\d+/\\d+\\)\\s*)?Pets(\\s.*)?$");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	private record Entry(Slot slot, ItemStack stack, boolean pet, boolean favorite) {
	}

	private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private static final List<Hit> HITS = new ArrayList<>();
	private static BookStyle.Frame frame = null;
	private static int lastContainerId = -1;
	private static int selectedSlot = -1;
	private static final long DOUBLE_CLICK_MS = 400;
	private static int lastClickSlot = -1;
	private static long lastClickMs = 0;
	private static double listScroll;
	private static int listMaxScroll;
	private static double detailScroll;
	private static int detailMaxScroll;
	// the pet collection (second view of the book)
	/** A pet you do not have is shown as a grey dye. */
	private static final ItemStack MISSING_ICON = new ItemStack(Items.DYE.pick(DyeColor.GRAY));
	private static boolean collectionMode = false;
	private static int collectionFilter = 0; // 0 = all, 1 = missing, 2 = not at max rarity
	private static String collectionSelected = "";

	private PetsView() {
	}

	/** True if this screen is the /pets menu and should be drawn as a book. */
	public static boolean applies(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)
				|| !ParzConfigDefinition.PETS_MENU.isEnabled()
				|| !SkyBlockDetector.featuresActive()) {
			return false;
		}
		// "Pets" or, with several pages, "(1/4) Pets"
		if (!TITLE.matcher(clean(screen.getTitle().getString()).trim()).matches()) {
			return false;
		}
		for (Entry entry : entries(container)) {
			if (entry.pet()) {
				return true; // while the menu is still empty the normal menu is shown
			}
		}
		return false;
	}

	// ======================================================================================
	// Reading the menu
	// ======================================================================================

	private static List<Entry> entries(AbstractContainerScreen<?> container) {
		List<Entry> list = new ArrayList<>();
		for (Slot slot : container.getMenu().slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String name = clean(stack.getHoverName().getString()).trim();
			Matcher pet = PET_NAME.matcher(name);
			if (pet.matches()) {
				// a favourite pet has a star in front: "★ [Lvl 200] Golden Dragon"
				list.add(new Entry(slot, stack, true, !pet.group(1).isBlank()));
			} else if (!name.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("glass_pane")) {
				list.add(new Entry(slot, stack, false, false));
			}
		}
		return list;
	}

	private record Btn(String label, Runnable action, ItemStack tip) {}

	private static final Pattern LEVEL = Pattern.compile("\\[Lvl \\d+]\\s*");

	/**
	 * Splits a pet name like "star [Lvl 200] Golden Dragon" into {level, rest}, keeping the colors. Everything in
	 * front of the level (the favourite star) is dropped. Names without a level come back as {"", name}.
	 */
	private static Component[] splitName(Component name) {
		String full = name.getString();
		Matcher match = LEVEL.matcher(full);
		if (!match.find()) {
			return new Component[]{Component.empty(), name};
		}
		int levelStart = match.start();
		int levelEnd = match.end();
		int levelTextEnd = full.indexOf(']', levelStart) + 1;
		net.minecraft.network.chat.MutableComponent level = Component.empty();
		net.minecraft.network.chat.MutableComponent rest = Component.empty();
		int[] pos = {0};
		name.visit((style, text) -> {
			int from = pos[0];
			int to = from + text.length();
			pos[0] = to;
			int a = Math.max(from, levelStart);
			int b = Math.min(to, levelTextEnd);
			if (a < b) {
				level.append(Component.literal(text.substring(a - from, b - from)).setStyle(style));
			}
			int c = Math.max(from, levelEnd);
			if (c < to) {
				rest.append(Component.literal(text.substring(c - from)).setStyle(style));
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return new Component[]{level, rest};
	}

	private static String loreText(ItemStack stack) {
		StringBuilder sb = new StringBuilder();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				sb.append(clean(line.getString()).toLowerCase(Locale.ROOT)).append('\n');
			}
		}
		return sb.toString();
	}

	private static boolean isActive(ItemStack stack) {
		return loreText(stack).contains("click to despawn");
	}

	// ======================================================================================
	// Rendering
	// ======================================================================================

	public static void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		frame = BookStyle.Frame.centered(screen.width, screen.height, MAX_W, MAX_H, 16);
		HITS.clear();

		List<Entry> pets = new ArrayList<>();
		List<Entry> controls = new ArrayList<>();
		for (Entry entry : entries(container)) {
			(entry.pet() ? pets : controls).add(entry);
		}
		// the equipped pet is always at the top of the page, then the favourite pets (with a star);
		// the other pets keep the menu order
		pets.sort(java.util.Comparator.comparing((Entry entry) -> !isActive(entry.stack()))
				.thenComparing(entry -> !entry.favorite()));
		observePets(container, pets);

		// new menu page: forget the selection
		int containerId = container.getMenu().containerId;
		if (containerId != lastContainerId) {
			lastContainerId = containerId;
			selectedSlot = -1;
			listScroll = 0;
			detailScroll = 0;
		}
		Entry selected = null;
		for (Entry pet : pets) {
			if (pet.slot().index == selectedSlot) {
				selected = pet;
			}
		}
		if (selected == null) {
			for (Entry pet : pets) {
				if (selected == null && isActive(pet.stack())) {
					selected = pet;
				}
			}
			if (selected == null && !pets.isEmpty()) {
				selected = pets.getFirst();
			}
			selectedSlot = selected != null ? selected.slot().index : -1;
		}

		graphics.fill(0, 0, screen.width, screen.height, 0xA0000000);
		Mascot.draw(graphics, frame.px(), frame.py(), frame.ph());
		BookStyle.drawBook(graphics, frame);
		if (collectionMode) {
			drawCollectionLeft(graphics, font, mouseX, mouseY);
			drawCollectionRight(graphics, font, container, controls, mouseX, mouseY);
		} else {
			drawLeftPage(graphics, font, screen, pets, selected, mouseX, mouseY);
			drawRightPage(graphics, font, container, selected, controls, mouseX, mouseY);
		}
		BookStyle.drawRibbon(graphics, frame);
	}

	private static void drawLeftPage(GuiGraphicsExtractor graphics, Font font, Screen screen, List<Entry> pets,
									 Entry selected, int mouseX, int mouseY) {
		int x1 = frame.leftX1();
		int x2 = frame.leftX2();
		BookStyle.pageTitle(graphics, font, clean(screen.getTitle().getString()).trim(), x1, x2, frame.pgY1() + 10);

		int top = frame.pgY1() + 30;
		int bottom = frame.pgY2() - 20;
		int view = bottom - top;
		// the equipped pet is on its own at the top, below the heading "Equipped Pet"
		boolean hasEquipped = !pets.isEmpty() && isActive(pets.getFirst().stack());
		int headerH = hasEquipped ? 12 : 0;
		int separatorH = hasEquipped && pets.size() > 1 ? 8 : 0;
		listMaxScroll = Math.max(0, pets.size() * ROW_STEP + headerH + separatorH - view);
		listScroll = clamp(listScroll, 0, listMaxScroll);

		graphics.enableScissor(x1 - 1, top, x2 + 1, bottom);
		int y = top + 1 - (int) listScroll;
		if (hasEquipped) {
			graphics.text(font, "Equipped Pet", x1 + 2, y + 2, BookStyle.TEXT_DIM, true);
			y += headerH;
		}
		boolean mouseInList = mouseX >= x1 && mouseX < x2 && mouseY >= top && mouseY < bottom;
		// columns: icon | level | name | favourite star | active gem
		int levelW = 0;
		for (Entry pet : pets) {
			levelW = Math.max(levelW, font.width(splitName(pet.stack().getHoverName())[0]));
		}
		int levelX = x1 + 23;
		int nameX = levelX + levelW + 6;
		int gemX = x2 - 6 - 8;
		int starW = font.width("★");
		int starX = gemX - 4 - starW;
		for (Entry pet : pets) {
			boolean isSelected = pet == selected;
			boolean hovered = mouseInList && mouseY >= y && mouseY < y + ROW_H && mouseX >= x1 && mouseX < x2 - 6;
			BookStyle.plate(graphics, x1, y, x2 - 6, y + ROW_H, isSelected, hovered, true);
			graphics.item(pet.stack(), x1 + 3, y + 1);

			boolean active = isActive(pet.stack());
			int textY = y + (ROW_H - font.lineHeight) / 2 + 1;
			Component[] parts = splitName(pet.stack().getHoverName());
			graphics.text(font, parts[0], levelX, textY, BookStyle.TEXT, true);
			List<FormattedCharSequence> lines = font.split(parts[1], Math.max(10, starX - 4 - nameX));
			if (!lines.isEmpty()) {
				graphics.text(font, lines.getFirst(), nameX, textY, BookStyle.TEXT, true);
			}
			if (pet.favorite()) {
				graphics.text(font, "★", starX, textY, 0xFFFFFF55, true);
			}
			if (active) {
				BookStyle.gem(graphics, x2 - 6 - 8, y + ROW_H / 2, BookStyle.GREEN);
			}

			int slotIndex = pet.slot().index;
			HITS.add(new Hit(x1, y, x2 - 6, y + ROW_H, () -> {
				// one click selects the pet, a double click summons / despawns it
				long now = System.currentTimeMillis();
				boolean doubleClick = slotIndex == lastClickSlot && now - lastClickMs <= DOUBLE_CLICK_MS;
				lastClickSlot = slotIndex;
				lastClickMs = now;
				selectedSlot = slotIndex;
				detailScroll = 0;
				if (doubleClick) {
					lastClickSlot = -1;
					click((AbstractContainerScreen<?>) screen, pet.slot(), 0);
				}
			}));
			y += ROW_STEP;
			if (hasEquipped && pet == pets.getFirst() && separatorH > 0) {
				BookStyle.footerDivider(graphics, x1, x2 - 6, y + 1);
				y += separatorH;
			}
		}
		graphics.disableScissor();
		BookStyle.drawScrollbar(graphics, x2 - 2, top, bottom, view, listScroll, listMaxScroll);

		BookStyle.footerDivider(graphics, x1, x2, frame.pgY2() - 17);
		drawTabs(graphics, font, x1, x2, mouseX, mouseY);
	}

	private static void drawRightPage(GuiGraphicsExtractor graphics, Font font, AbstractContainerScreen<?> container,
									  Entry selected, List<Entry> controls, int mouseX, int mouseY) {
		int x1 = frame.rightX1();
		int x2 = frame.rightX2();
		if (selected == null) {
			BookStyle.pageTitle(graphics, font, "Pets", x1, x2, frame.pgY1() + 10);
			return;
		}
		BookStyle.pageTitle(graphics, font, selected.stack().getHoverName(), x1, x2, frame.pgY1() + 10);

// ---- bottom: buttons in three groups (computed first, the details use the space above) ----
		String lore = loreText(selected.stack());
		List<List<Btn>> groups = new ArrayList<>();
		List<Btn> actions = new ArrayList<>();   // despawn / convert / exp sharing
		List<Btn> navigation = new ArrayList<>(); // pages, go back, close, search, sort
		List<Btn> features = new ArrayList<>();   // pets, pet slots, autopet, score rewards, visibility
		actions.add(new Btn(lore.contains("click to despawn") ? "Despawn" : lore.contains("click to summon") ? "Summon" : "Select",
				() -> click(container, selected.slot(), 0), null));
		if (lore.contains("convert")) {
			actions.add(new Btn("Convert to item", () -> click(container, selected.slot(), 1), null));
		}
		List<String> navOrder = List.of("previous page", "next page", "go back", "close", "search", "sort");
		List<Btn> navSorted = new ArrayList<>();
		for (int i = 0; i < navOrder.size(); i++) {
			navSorted.add(null);
		}
		for (Entry control : controls) {
			String label = clean(control.stack().getHoverName().getString()).trim();
			String key = label.toLowerCase(Locale.ROOT);
			Btn btn = new Btn(label, () -> click(container, control.slot(), 0), control.stack());
			int nav = navOrder.indexOf(key);
			if (key.equals("exp sharing")) {
				actions.add(btn);
			} else if (nav >= 0) {
				navSorted.set(nav, btn);
			} else {
				features.add(btn);
			}
		}
		// "Previous Page" and "Next Page" always keep their own cell (an empty one if the page does not exist),
		// so "Next Page" never jumps to another spot
		boolean pageButtons = navSorted.get(0) != null || navSorted.get(1) != null;
		for (int i = 0; i < navSorted.size(); i++) {
			Btn btn = navSorted.get(i);
			if (btn != null || (pageButtons && i < 2)) {
				navigation.add(btn);
			}
		}
		groups.add(actions);
		groups.add(navigation);
		groups.add(features);
		groups.removeIf(List::isEmpty);

		// every group is a grid of equal cells: 3 columns, or 2 if a label does not fit into a cell
		int widest = 0;
		for (List<Btn> group : groups) {
			for (Btn btn : group) {
				if (btn != null) {
					widest = Math.max(widest, font.width(btn.label()));
				}
			}
		}
		int gap = 4;
		int groupGap = 6;
		int cols = (x2 - x1 - 2 * gap) / 3 >= widest + 10 ? 3 : 2;
		int cellW = (x2 - x1 - (cols - 1) * gap) / cols;
		int controlsH = 0;
		for (int g = 0; g < groups.size(); g++) {
			int groupRows = (groups.get(g).size() + cols - 1) / cols;
			controlsH += groupRows * (g == 0 ? 18 : 17) + (g > 0 ? groupGap : 0);
		}
		int bottomY = frame.pgY2() - 8;
		int mainTop = bottomY - controlsH;

		// ---- details (lore of the pet) ----
		int top = frame.pgY1() + 30;
		int bottom = mainTop - 6;
		int view = Math.max(10, bottom - top);
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Component> tooltip = Screen.getTooltipFromItem(Minecraft.getInstance(), selected.stack());
		for (int i = 1; i < tooltip.size(); i++) { // line 0 is the name
			List<FormattedCharSequence> wrapped = font.split(tooltip.get(i), x2 - x1 - 8);
			if (wrapped.isEmpty()) {
				lines.add(FormattedCharSequence.EMPTY);
			}
			lines.addAll(wrapped);
		}
		detailMaxScroll = Math.max(0, lines.size() * 10 - view);
		detailScroll = clamp(detailScroll, 0, detailMaxScroll);

		graphics.enableScissor(x1 - 2, top, x2 + 2, bottom);
		int ly = top + 1 - (int) detailScroll;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, x1 + 2, ly, BookStyle.TEXT, true);
			ly += 10;
		}
		graphics.disableScissor();
		BookStyle.drawScrollbar(graphics, x2 + 5, top, bottom, view, detailScroll, detailMaxScroll);

		BookStyle.footerDivider(graphics, x1, x2, mainTop - 3);

// ---- the buttons ----
		int ry = mainTop;
		for (int g = 0; g < groups.size(); g++) {
			List<Btn> group = groups.get(g);
			int h = g == 0 ? 16 : 14;
			int step = h + 2;
			for (int i = 0; i < group.size(); i++) {
				Btn btn = group.get(i);
				if (btn == null) {
					continue; // an empty cell keeps the place of a missing page button
				}
				int cx = x1 + (i % cols) * (cellW + gap);
				int cy = ry + (i / cols) * step;
				String label = btn.label();
				if (font.width(label) > cellW - 6) {
					label = font.plainSubstrByWidth(label, cellW - 6 - font.width("...")) + "...";
				}
				boolean hovered = drawButton(graphics, font, cx, cy, cellW, h, label, mouseX, mouseY, btn.action());
				if (hovered && btn.tip() != null) {
					graphics.setTooltipForNextFrame(font, btn.tip(), mouseX, mouseY);
				}
			}
			ry += ((group.size() + cols - 1) / cols) * step + groupGap;
		}
	}

	
	// ======================================================================================
	// Pet collection
	// ======================================================================================

	/** Hands the pets of the open page to the collection (it stores them and notices new or removed pets). */
	private static void observePets(AbstractContainerScreen<?> container, List<Entry> pets) {
		List<PetCollection.Scanned> scanned = new ArrayList<>();
		for (Entry entry : pets) {
			if (!entry.pet()) {
				continue;
			}
			Component[] parts = splitName(entry.stack().getHoverName());
			Matcher level = Pattern.compile("\\d+").matcher(parts[0].getString());
			int lvl = level.find() ? Integer.parseInt(level.group()) : 1;
			scanned.add(new PetCollection.Scanned(parts[1].getString(), PetCollection.rarityOf(parts[1]), lvl, entry.stack()));
		}
		PetCollection.observe(container.getMenu().containerId, clean(container.getTitle().getString()).trim(), scanned);
	}

	/** The two buttons at the bottom of the left page: the pet list and the collection. */
	private static void drawTabs(GuiGraphicsExtractor graphics, Font font, int x1, int x2, int mouseX, int mouseY) {
		int y = frame.pgY2() - 14;
		int half = (x2 - x1 - 4) / 2;
		drawTab(graphics, font, x1, y, half, 12, "Pets", !collectionMode, mouseX, mouseY, () -> {
			collectionMode = false;
			listScroll = 0;
		});
		String label = "Collection " + PetCollection.ownedCount() + "/" + PetCollection.totalCount();
		drawTab(graphics, font, x1 + half + 4, y, half, 12, label, collectionMode, mouseX, mouseY, () -> {
			collectionMode = true;
			listScroll = 0;
		});
	}

	private static void drawTab(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, String label,
								boolean active, int mouseX, int mouseY, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		BookStyle.plate(graphics, x, y, x + w, y + h, active, hovered, true);
		graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1,
				active ? BookStyle.TEXT : BookStyle.TEXT_DIM, true);
		HITS.add(new Hit(x, y, x + w, y + h, action));
	}

	private static List<PetCollection.Row> visibleRows() {
		List<PetCollection.Row> rows = new ArrayList<>();
		for (PetCollection.Row row : PetCollection.rows()) {
			boolean show = switch (collectionFilter) {
				case 1 -> !row.owned();
				case 2 -> row.owned() && !row.maxed();
				default -> true;
			};
			if (show) {
				rows.add(row);
			}
		}
		return rows;
	}

	private static PetCollection.Row selectedRow(List<PetCollection.Row> rows) {
		for (PetCollection.Row row : rows) {
			if (row.name().equals(collectionSelected)) {
				return row;
			}
		}
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private static void drawCollectionLeft(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int x1 = frame.leftX1();
		int x2 = frame.leftX2();
		BookStyle.pageTitle(graphics, font, "Pet Collection", x1, x2, frame.pgY1() + 10);

		List<PetCollection.Row> rows = visibleRows();
		PetCollection.Row selected = selectedRow(rows);
		int top = frame.pgY1() + 30;
		int bottom = frame.pgY2() - 20;
		int view = bottom - top;
		listMaxScroll = Math.max(0, rows.size() * ROW_STEP - view);
		listScroll = clamp(listScroll, 0, listMaxScroll);

		graphics.enableScissor(x1 - 1, top, x2 + 1, bottom);
		int y = top + 1 - (int) listScroll;
		boolean mouseInList = mouseX >= x1 && mouseX < x2 && mouseY >= top && mouseY < bottom;
		for (PetCollection.Row row : rows) {
			boolean isSelected = row == selected;
			boolean hovered = mouseInList && mouseY >= y && mouseY < y + ROW_H && mouseX >= x1 && mouseX < x2 - 6;
			BookStyle.plate(graphics, x1, y, x2 - 6, y + ROW_H, isSelected, hovered, true);
			// a missing pet is a grey dye
			graphics.item(row.owned() ? row.icon() : MISSING_ICON, x1 + 3, y + 1);

			int gemX = x2 - 6 - 8;
			String right = row.owned() ? PetCollection.rarityName(row.best()) : "Missing";
			int rightColor = row.owned() ? PetCollection.rarityColor(row.best()) : 0xFF7A7A82;
			int rightX = gemX - 5 - font.width(right);
			String name = row.name();
			int nameMax = rightX - 4 - (x1 + 23);
			if (font.width(name) > nameMax) {
				name = font.plainSubstrByWidth(name, Math.max(10, nameMax - font.width("..."))) + "...";
			}
			int textY = y + (ROW_H - font.lineHeight) / 2 + 1;
			graphics.text(font, name, x1 + 23, textY, row.owned() ? PetCollection.rarityColor(row.best()) : 0xFF7A7A82, true);
			graphics.text(font, right, rightX, textY, rightColor, true);
			if (row.owned()) {
				BookStyle.gem(graphics, gemX, y + ROW_H / 2, row.maxed() ? BookStyle.GREEN : 0xFFFFAA00);
			}
			String pick = row.name();
			HITS.add(new Hit(x1, y, x2 - 6, y + ROW_H, () -> collectionSelected = pick));
			y += ROW_STEP;
		}
		graphics.disableScissor();
		BookStyle.drawScrollbar(graphics, x2 - 2, top, bottom, view, listScroll, listMaxScroll);

		BookStyle.footerDivider(graphics, x1, x2, frame.pgY2() - 17);
		drawTabs(graphics, font, x1, x2, mouseX, mouseY);
	}

	private static void drawCollectionRight(GuiGraphicsExtractor graphics, Font font, AbstractContainerScreen<?> container,
											List<Entry> controls, int mouseX, int mouseY) {
		int x1 = frame.rightX1();
		int x2 = frame.rightX2();
		BookStyle.pageTitle(graphics, font, "Collection", x1, x2, frame.pgY1() + 10);

		int total = PetCollection.totalCount();
		int owned = PetCollection.ownedCount();
		int maxed = PetCollection.maxedCount();
		int y = frame.pgY1() + 30;
		y = drawStat(graphics, font, x1, x2, y, "Collected", owned, total, 0xFF55FF55);
		y = drawStat(graphics, font, x1, x2, y, "Max rarity", maxed, total, 0xFFFFAA00);

		// how much of the pets menu has been read
		int scanned = PetCollection.scannedPages();
		int pages = PetCollection.totalPages();
		boolean complete = pages > 0 && scanned >= pages;
		String scanText = scanned == 0 ? "No page scanned yet."
				: complete ? "All " + pages + " pages of your pets are scanned."
				: "Pages scanned: " + scanned + "/" + pages + ". Open the other pages of /pets to add them.";
		int scanColor = complete ? 0xFF55FF55 : 0xFFFFAA00;
		for (FormattedCharSequence line : font.split(Component.literal(scanText), x2 - x1)) {
			graphics.text(font, line, x1, y, scanColor, true);
			y += 10;
		}
		y += 4;

		// filters
		int missing = total - owned;
		int upgrade = 0;
		for (PetCollection.Row row : PetCollection.rows()) {
			if (row.owned() && !row.maxed()) {
				upgrade++;
			}
		}
		String[] labels = {"All " + total, "Missing " + missing, "Not max " + upgrade};
		int gap = 4;
		int width = (x2 - x1 - 2 * gap) / 3;
		for (int i = 0; i < 3; i++) {
			int filter = i;
			drawTab(graphics, font, x1 + i * (width + gap), y, width, 13, labels[i], collectionFilter == i, mouseX, mouseY, () -> {
				collectionFilter = filter;
				listScroll = 0;
			});
		}
		y += 19;
		BookStyle.footerDivider(graphics, x1, x2, y - 3);
		y += 3;

		// the pet that is selected in the list
		PetCollection.Row row = selectedRow(visibleRows());
		if (row != null) {
			graphics.item(row.owned() ? row.icon() : MISSING_ICON, x1, y);
			graphics.text(font, row.name(), x1 + 22, y + 4, row.owned() ? PetCollection.rarityColor(row.best()) : 0xFF9A9AA2, true);
			y += 24;
			y = drawFact(graphics, font, x1, y, "Highest rarity", PetCollection.rarityName(row.max()), PetCollection.rarityColor(row.max()));
			if (row.owned()) {
				y = drawFact(graphics, font, x1, y, "You have", PetCollection.rarityName(row.best()) + " (Lvl " + row.level() + ")",
						PetCollection.rarityColor(row.best()));
				y = drawFact(graphics, font, x1, y, "Status", row.maxed() ? "Max rarity" : "Can be upgraded",
						row.maxed() ? 0xFF55FF55 : 0xFFFFAA00);
			} else {
				y = drawFact(graphics, font, x1, y, "Status", "Missing", 0xFFFF6B6B);
			}
			if (!row.skill().isEmpty()) {
				drawFact(graphics, font, x1, y, "Skill", row.skill(), BookStyle.TEXT);
			}
		}

		// page buttons, so the next page can be opened and added from here too
		Entry previous = null;
		Entry next = null;
		for (Entry control : controls) {
			String name = clean(control.stack().getHoverName().getString()).trim().toLowerCase(Locale.ROOT);
			if (name.equals("previous page")) {
				previous = control;
			} else if (name.equals("next page")) {
				next = control;
			}
		}
		int by = frame.pgY2() - 22;
		int half = (x2 - x1 - 4) / 2;
		if (previous != null) {
			Entry target = previous;
			drawButton(graphics, font, x1, by, half, 14, "Previous Page", mouseX, mouseY, () -> click(container, target.slot(), 0));
		}
		if (next != null) {
			Entry target = next;
			drawButton(graphics, font, x1 + half + 4, by, half, 14, "Next Page", mouseX, mouseY, () -> click(container, target.slot(), 0));
		}
	}

	/** "Label        12 / 84" with a progress bar below. Returns the y of the next element. */
	private static int drawStat(GuiGraphicsExtractor graphics, Font font, int x1, int x2, int y, String label, int value, int total, int color) {
		graphics.text(font, label, x1, y, BookStyle.TEXT_DIM, true);
		String number = value + " / " + total;
		graphics.text(font, number, x2 - font.width(number), y, BookStyle.TEXT, true);
		graphics.fill(x1, y + 11, x2, y + 15, 0xFF2A2A2A);
		int filled = total <= 0 ? 0 : Math.round((x2 - x1) * (value / (float) total));
		if (filled > 0) {
			graphics.fill(x1, y + 11, x1 + filled, y + 15, color);
		}
		return y + 22;
	}

	private static int drawFact(GuiGraphicsExtractor graphics, Font font, int x1, int y, String label, String value, int color) {
		graphics.text(font, label + ":", x1, y, BookStyle.TEXT_DIM, true);
		graphics.text(font, value, x1 + font.width(label + ": ") + 4, y, color, true);
		return y + 11;
	}

	/** A button plate with centered text. Returns true if the mouse is over it. */
	private static boolean drawButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, String label,
									  int mouseX, int mouseY, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		BookStyle.plate(graphics, x, y, x + w, y + h, false, hovered, true);
		graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1, BookStyle.TEXT, true);
		HITS.add(new Hit(x, y, x + w, y + h, action));
		return hovered;
	}

	// ======================================================================================
	// Input
	// ======================================================================================

	/** Left click: select a pet / press a button. Always consumed so the normal menu logic never runs. */
	public static boolean click(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int i = HITS.size() - 1; i >= 0; i--) {
				if (HITS.get(i).contains(mouseX, mouseY)) {
					HITS.get(i).action().run();
					break;
				}
			}
		}
		return true;
	}

	public static boolean scroll(double mouseX, double mouseY, double amount) {
		if (frame == null) {
			return true;
		}
		if (mouseX < frame.mid()) {
			listScroll = clamp(listScroll - amount * ROW_STEP, 0, listMaxScroll);
		} else {
			detailScroll = clamp(detailScroll - amount * 20, 0, detailMaxScroll);
		}
		return true;
	}

	/** Sends a click on a slot of the real menu to the server. */
	private static void click(AbstractContainerScreen<?> container, Slot slot, int mouseButton) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode != null && client.player != null) {
			client.gameMode.handleContainerInput(container.getMenu().containerId, slot.index, mouseButton,
					ContainerInput.PICKUP, client.player);
		}
	}

	// ---- helpers -------------------------------------------------------------------------

	private static String clean(String text) {
		return FORMATTING.matcher(text).replaceAll("");
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
