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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.parz.config.ParzConfigDefinition;
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

	/** Sort key of a control button: 0 = Previous Page, 1 = Next Page, 2 = all others (stable sort keeps their order). */
	private static int controlOrder(Entry entry) {
		String name = clean(entry.stack().getHoverName().getString()).toLowerCase(Locale.ROOT);
		if (name.contains("previous page")) {
			return 0;
		}
		if (name.contains("next page")) {
			return 1;
		}
		return 2;
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
		// favourite pets (with a star) are at the top of the pet list, the other pets keep the menu order
		pets.sort(java.util.Comparator.comparing((Entry entry) -> !entry.favorite()));
		// Previous Page and Next Page first and next to each other, everything else keeps the menu order
		controls.sort(java.util.Comparator.comparingInt(PetsView::controlOrder));

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
		BookStyle.drawBook(graphics, frame);
		drawLeftPage(graphics, font, screen, pets, selected, mouseX, mouseY);
		drawRightPage(graphics, font, container, selected, controls, mouseX, mouseY);
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
		listMaxScroll = Math.max(0, pets.size() * ROW_STEP - view);
		listScroll = clamp(listScroll, 0, listMaxScroll);

		graphics.enableScissor(x1 - 1, top, x2 + 1, bottom);
		int y = top + 1 - (int) listScroll;
		boolean mouseInList = mouseX >= x1 && mouseX < x2 && mouseY >= top && mouseY < bottom;
		for (Entry pet : pets) {
			boolean isSelected = pet == selected;
			boolean hovered = mouseInList && mouseY >= y && mouseY < y + ROW_H && mouseX >= x1 && mouseX < x2 - 6;
			BookStyle.plate(graphics, x1, y, x2 - 6, y + ROW_H, isSelected, hovered, true);
			graphics.item(pet.stack(), x1 + 3, y + 1);

			boolean active = isActive(pet.stack());
			int nameMax = (x2 - 6 - x1) - 26 - (active ? 12 : 0);
			List<FormattedCharSequence> lines = font.split(pet.stack().getHoverName(), Math.max(10, nameMax));
			if (!lines.isEmpty()) {
				graphics.text(font, lines.getFirst(), x1 + 23, y + (ROW_H - font.lineHeight) / 2 + 1, BookStyle.TEXT, true);
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
		}
		graphics.disableScissor();
		BookStyle.drawScrollbar(graphics, x2 - 2, top, bottom, view, listScroll, listMaxScroll);

		BookStyle.footerDivider(graphics, x1, x2, frame.pgY2() - 17);
		String footer = pets.size() + (pets.size() == 1 ? " pet" : " pets");
		graphics.text(font, footer, (x1 + x2 - font.width(footer)) / 2, frame.pgY2() - 12, BookStyle.TEXT_DIM, true);
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

		// ---- bottom: buttons (computed first, the details use the space above) ----
		String lore = loreText(selected.stack());
		List<String[]> mainButtons = new ArrayList<>(); // label, mouse button
		mainButtons.add(new String[]{lore.contains("click to despawn") ? "Despawn" : lore.contains("click to summon") ? "Summon" : "Select", "0"});
		if (lore.contains("convert")) {
			mainButtons.add(new String[]{"Convert to item", "1"});
		}

		List<Integer> controlWidths = new ArrayList<>();
		List<String> controlLabels = new ArrayList<>();
		for (Entry control : controls) {
			String label = clean(control.stack().getHoverName().getString()).trim();
			if (label.length() > 22) {
				label = label.substring(0, 21) + "...";
			}
			controlLabels.add(label);
			controlWidths.add(font.width(label) + 12);
		}
		int rows = 0;
		int cx = x1;
		for (int w : controlWidths) {
			if (cx > x1 && cx + w > x2) {
				rows++;
				cx = x1;
			}
			cx += w + 4;
		}
		if (!controls.isEmpty()) {
			rows++;
		}
		int controlsH = rows * 17;
		int bottomY = frame.pgY2() - 8;
		int controlsTop = bottomY - controlsH;
		int mainTop = controlsTop - (controls.isEmpty() ? 0 : 6) - 18;

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

		// ---- main buttons ----
		int bx = x1;
		for (String[] button : mainButtons) {
			int w = font.width(button[0]) + 18;
			int mouseButton = Integer.parseInt(button[1]);
			drawButton(graphics, font, bx, mainTop, w, 16, button[0], mouseX, mouseY, () -> click(container, selected.slot(), mouseButton));
			bx += w + 6;
		}

		// ---- the other buttons of the menu (next page, close, ...) ----
		int ry = controlsTop;
		cx = x1;
		for (int i = 0; i < controls.size(); i++) {
			int w = controlWidths.get(i);
			if (cx > x1 && cx + w > x2) {
				ry += 17;
				cx = x1;
			}
			Entry control = controls.get(i);
			boolean hovered = drawButton(graphics, font, cx, ry, w, 14, controlLabels.get(i), mouseX, mouseY,
					() -> click(container, control.slot(), 0));
			if (hovered) {
				graphics.setTooltipForNextFrame(font, control.stack(), mouseX, mouseY);
			}
			cx += w + 4;
		}
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
