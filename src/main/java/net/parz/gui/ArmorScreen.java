package net.parz.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.parz.armor.AnimatedDyes;
import net.parz.armor.ArmorConfig;
import net.parz.armor.ArmorLook;
import net.parz.armor.DyeIcons;
import net.parz.armor.HeadCatalog;
import net.parz.armor.StaticDyes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Armor" tab of /pz custom - how your armor looks to the other Parz users. Pick a slot below the preview (helmet,
 * chestplate, leggings, boots) and click what you want in the grid: for the helmet any SkyBlock head (armor heads,
 * minions, ...), for the other slots a color (the piece is then drawn as leather armor in that color). A click is saved
 * at once and sent to the registry, so everybody sees it; the preview shows it right away.
 */
public class ArmorScreen extends Screen {
	private static final int PANEL_W = 398;
	private static final int PANEL_H = 218;
	private static final int PAD = 12;
	private static final int PREVIEW_W = 90;
	private static final int PREVIEW_H = 136;
	private static final int CELL = 20;
	private static final int COLS = 13;
	private static final int ROWS = 6;
	private static final int BAR_W = 6;
	private static final int SLOT = 24;

	private static final int PANEL = 0xF0252B3B;
	private static final int PANEL_EDGE = 0xFF5C6580;
	private static final int BOX = 0xFF3A4157;
	private static final int BOX_EDGE = 0xFF171A24;
	private static final int CELL_BG = 0x55000000;
	private static final int CELL_HOVER = 0xAA6C7699;
	private static final int CELL_SELECTED = 0xFF3E8E57;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFA6ADC4;

	/** The slots in the order they are shown. */
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] SLOT_NAMES = {"Helmet (head)", "Chestplate", "Leggings", "Boots"};

	/** One thing to click in the grid: nothing (the red circle), a head or a color. */
	private record Entry(HeadCatalog.Head head, Integer rgb, String label) {
		boolean none() {
			return head == null && rgb == null;
		}
	}

	private static final Map<String, ItemStack> HEAD_ICONS = new HashMap<>();
	private static final Map<Long, ItemStack> ARMOR_ICONS = new HashMap<>();

	private int slot = 0;
	private int scroll = 0;
	private boolean draggingBar = false;
	private String status = "";
	private List<Entry> entries = new ArrayList<>();
	private int lastHeadCount = -1;

	private EditBox search;
	private Button sameColorButton;
	private Button resetButton;
	private int px;
	private int py;

	public ArmorScreen() {
		super(Component.literal("Parz Armor"));
	}

	@Override
	protected void init() {
		HeadCatalog.ensureLoaded();
		px = (this.width - PANEL_W) / 2;
		py = (this.height - PANEL_H) / 2;

		search = new EditBox(this.font, gridX(), contentY(), COLS * CELL + 8 + BAR_W, 18, Component.literal("Search"));
		search.setHint(Component.literal("Search...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
		search.setMaxLength(40);
		search.setResponder(text -> {
			rebuild();
			scroll = 0;
		});
		this.addRenderableWidget(search);
		this.setInitialFocus(search);

		int buttonsY = gridY() + ROWS * CELL + 4;
		sameColorButton = Button.builder(Component.literal("Same color on all pieces"), b -> sameColor())
				.bounds(gridX(), buttonsY, 150, 20).build();
		this.addRenderableWidget(sameColorButton);
		resetButton = Button.builder(Component.literal("Reset everything"), b -> {
			ArmorConfig.set(ArmorLook.NONE);
			status = "Reset: your armor looks normal again.";
			rebuild();
		}).bounds(gridX() + 156, buttonsY, 118, 20).build();
		this.addRenderableWidget(resetButton);

		rebuild();
	}

	// ---- layout ----------------------------------------------------------------------------

	private int leftX() {
		return px + PAD;
	}

	private int contentY() {
		return py + 30;
	}

	private int gridX() {
		return px + PAD + PREVIEW_W + 10;
	}

	private int gridY() {
		return contentY() + 22;
	}

	private int slotX(int index) {
		return leftX() + (PREVIEW_W - 4 * SLOT - 3 * 2) / 2 + index * (SLOT + 2);
	}

	private int slotY() {
		return contentY() + PREVIEW_H + 6;
	}

	// ---- the list in the grid ----------------------------------------------------------------

	private boolean headSlot() {
		return slot == 0;
	}

	/** Builds the entries of the grid for the selected slot and the search text. */
	private void rebuild() {
		String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		List<Entry> list = new ArrayList<>();
		list.add(new Entry(null, null, "Nothing (normal armor)"));
		if (headSlot()) {
			List<HeadCatalog.Head> heads = HeadCatalog.heads();
			lastHeadCount = heads.size();
			for (HeadCatalog.Head head : heads) {
				if (query.isEmpty() || head.searchText().contains(query)) {
					list.add(new Entry(head, null, head.name()));
				}
			}
		} else {
			Integer typed = null;
			try {
				typed = parseColor(query);
			} catch (IllegalArgumentException ignored) {
				// not a color: it is only searched in the names
			}
			if (typed != null) {
				list.add(new Entry(null, typed, "#" + hex(typed)));
			}
			for (AnimatedDyes.Dye dye : AnimatedDyes.all()) {
				if (query.isEmpty() || dye.name().toLowerCase(Locale.ROOT).contains(query) || "animated".contains(query)) {
					list.add(new Entry(null, dye.code(), dye.name()));
				}
			}
			for (StaticDyes.Dye dye : StaticDyes.all()) {
				if (query.isEmpty() || dye.name().toLowerCase(Locale.ROOT).contains(query)) {
					list.add(new Entry(null, dye.rgb(), dye.name()));
				}
			}
		}
		entries = list;
		scroll = Math.min(scroll, maxScroll());
	}

	private int maxScroll() {
		int rows = (entries.size() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	private boolean isSelected(Entry entry) {
		ArmorLook look = ArmorConfig.look();
		if (headSlot()) {
			return entry.none() ? look.skin() == null : entry.head() != null && entry.head().hash().equals(look.skin());
		}
		Integer current = look.color(SLOTS[slot]);
		return entry.none() ? current == null : entry.rgb() != null && entry.rgb().equals(current);
	}

	private void choose(Entry entry) {
		ArmorLook look = ArmorConfig.look();
		if (headSlot()) {
			ArmorConfig.set(look.withSkin(entry.head() == null ? null : entry.head().hash()));
		} else {
			ArmorConfig.set(look.withColor(SLOTS[slot], entry.rgb()));
		}
		status = entry.none() ? "Removed: " + SLOT_NAMES[slot] + " looks normal again."
				: "Saved " + entry.label() + ". Other Parz users see it now.";
	}

	private void sameColor() {
		Integer color = ArmorConfig.look().color(SLOTS[Math.max(1, slot)]);
		ArmorConfig.set(ArmorConfig.look().withAllColors(color));
		status = color == null ? "Colors removed from chestplate, leggings and boots."
				: "Saved " + colorName(color) + " for chestplate, leggings and boots.";
	}

	// ---- drawing -----------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (headSlot() && HeadCatalog.heads().size() != lastHeadCount) {
			rebuild();
		}
		sameColorButton.visible = !headSlot();
		sameColorButton.active = !headSlot();

		graphics.fill(px, py, px + PANEL_W, py + PANEL_H, PANEL);
		graphics.outline(px, py, PANEL_W, PANEL_H, PANEL_EDGE);
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (search.getValue().isEmpty()) {
			graphics.text(font, Component.literal("Search...").withStyle(ChatFormatting.ITALIC), search.getX() + 4, search.getY() + 5, 0xFF8B8B8B, false);
		}

		CustomUi.tabs(graphics, font, px, py, CustomUi.ARMOR_TAB, "Everyone with Parz sees this", mouseX, mouseY);

		drawPreview(graphics, mouseX, mouseY);
		drawSlots(graphics, mouseX, mouseY);
		drawGrid(graphics, mouseX, mouseY);

		if (!status.isEmpty()) {
			graphics.text(font, status, leftX(), py + PANEL_H - 16, 0xFF7CE08F, false);
		} else {
			graphics.text(font, "A dye draws the piece as dyed leather armor; a head replaces the helmet.", leftX(), py + PANEL_H - 16, TEXT_DIM, false);
		}
	}

	private void drawPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = leftX();
		int y = contentY();
		graphics.fill(x, y, x + PREVIEW_W, y + PREVIEW_H, BOX);
		graphics.outline(x, y, PREVIEW_W, PREVIEW_H, BOX_EDGE);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		ArmorConfig.previewing = true;
		try {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 1, y + 1, x + PREVIEW_W - 1, y + PREVIEW_H - 1,
					52, 0.0625F, mouseX, mouseY, client.player);
		} catch (RuntimeException ignored) {
			// no preview this frame
		} finally {
			ArmorConfig.previewing = false;
		}
	}

	private void drawSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ArmorLook look = ArmorConfig.look();
		for (int i = 0; i < SLOTS.length; i++) {
			int x = slotX(i);
			int y = slotY();
			boolean hover = inside(mouseX, mouseY, x, y, SLOT, SLOT);
			graphics.fill(x, y, x + SLOT, y + SLOT, hover ? 0xFF5A6280 : BOX);
			graphics.outline(x, y, SLOT, SLOT, i == slot ? 0xFFE8F0FF : BOX_EDGE);
			graphics.item(slotIcon(i, look), x + 4, y + 4);
			if (hover) {
				graphics.setTooltipForNextFrame(font, Component.literal(SLOT_NAMES[i]), mouseX, mouseY);
			}
		}
	}

	private ItemStack slotIcon(int index, ArmorLook look) {
		if (index == 0) {
			return look.skin() != null ? headIcon(look.skin()) : armorIcon(Items.LEATHER_HELMET, null);
		}
		Item item = index == 1 ? Items.LEATHER_CHESTPLATE : index == 2 ? Items.LEATHER_LEGGINGS : Items.LEATHER_BOOTS;
		return armorIcon(item, AnimatedDyes.resolve(look.color(SLOTS[index])));
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int gx = gridX();
		int gy = gridY();
		int w = COLS * CELL;
		int h = ROWS * CELL;
		graphics.fill(gx - 1, gy - 1, gx + w + 1, gy + h + 1, BOX_EDGE);
		graphics.fill(gx, gy, gx + w, gy + h, 0xFF11141C);

		if (entries.size() <= 1 && headSlot() && !HeadCatalog.status().isEmpty()) {
			graphics.text(font, HeadCatalog.status(), gx + 6, gy + 28, TEXT_DIM, false);
		}
		Entry hovered = null;
		for (int i = 0; i < COLS * ROWS; i++) {
			int index = scroll * COLS + i;
			if (index >= entries.size()) {
				break;
			}
			Entry entry = entries.get(index);
			int x = gx + (i % COLS) * CELL;
			int y = gy + (i / COLS) * CELL;
			boolean hover = inside(mouseX, mouseY, x, y, CELL, CELL);
			graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, isSelected(entry) ? CELL_SELECTED : hover ? CELL_HOVER : CELL_BG);
			graphics.item(iconOf(entry), x + 2, y + 2);
			if (hover) {
				hovered = entry;
			}
		}
		if (entries.size() == 1 && !headSlot()) {
			graphics.text(font, "No dye matches. You can also type a color like #FF8800.", gx + 6, gy + 28, TEXT_DIM, false);
		} else if (entries.size() == 1 && HeadCatalog.status().isEmpty()) {
			graphics.text(font, "No head matches your search.", gx + 6, gy + 28, TEXT_DIM, false);
		}

		// scroll bar
		int bx = gx + w + 8;
		graphics.fill(bx, gy, bx + BAR_W, gy + h, 0xFF11141C);
		int max = maxScroll();
		int rows = (entries.size() + COLS - 1) / COLS;
		int thumb = max == 0 ? h : Math.max(14, h * ROWS / rows);
		int thumbY = max == 0 ? gy : gy + (h - thumb) * scroll / max;
		graphics.fill(bx, thumbY, bx + BAR_W, thumbY + thumb, draggingBar ? 0xFFE8F0FF : 0xFF7C86A6);

		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(font, tooltipOf(hovered), mouseX, mouseY);
		}
	}

	private List<Component> tooltipOf(Entry entry) {
		List<Component> tip = new ArrayList<>();
		if (entry.none()) {
			tip.add(Component.literal(entry.label()).withStyle(ChatFormatting.RED));
		} else if (entry.head() != null) {
			tip.add(Component.literal(entry.head().name()).withStyle(tierColor(entry.head().tier())));
			tip.add(Component.literal(entry.head().id()).withStyle(ChatFormatting.DARK_GRAY));
		} else {
			tip.add(Component.literal(entry.label()).withStyle(DyeIcons.color(entry.label())));
			if (AnimatedDyes.isAnimated(entry.rgb())) {
				tip.add(Component.literal("Animated dye (SkyBlock)").withStyle(ChatFormatting.LIGHT_PURPLE));
			} else {
				tip.add(Component.literal("#" + hex(entry.rgb())).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		return tip;
	}

	private ItemStack iconOf(Entry entry) {
		if (entry.none()) {
			return new ItemStack(Items.BARRIER);
		}
		if (entry.head() != null) {
			return headIcon(entry.head().hash());
		}
		// a SkyBlock dye is shown as its item (a head with the texture of the dye), like in the game
		String dyeHead = DyeIcons.hash(entry.label());
		if (dyeHead != null) {
			return headIcon(dyeHead);
		}
		return armorIcon(SLOTS[slot] == EquipmentSlot.LEGS ? Items.LEATHER_LEGGINGS
				: SLOTS[slot] == EquipmentSlot.FEET ? Items.LEATHER_BOOTS : Items.LEATHER_CHESTPLATE, AnimatedDyes.resolve(entry.rgb()));
	}

	private static ItemStack headIcon(String hash) {
		return HEAD_ICONS.computeIfAbsent(hash, h -> {
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ArmorLook.profileFor(h));
			return stack;
		});
	}

	private static ItemStack armorIcon(Item item, Integer rgb) {
		long key = (long) System.identityHashCode(item) << 32 | (rgb == null ? 0x1000000L : rgb & 0xFFFFFFL);
		return ARMOR_ICONS.computeIfAbsent(key, k -> {
			ItemStack stack = new ItemStack(item);
			if (rgb != null) {
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			}
			return stack;
		});
	}

	private static ChatFormatting tierColor(String tier) {
		return switch (tier == null ? "" : tier) {
			case "UNCOMMON" -> ChatFormatting.GREEN;
			case "RARE" -> ChatFormatting.BLUE;
			case "EPIC" -> ChatFormatting.DARK_PURPLE;
			case "LEGENDARY" -> ChatFormatting.GOLD;
			case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
			case "DIVINE" -> ChatFormatting.AQUA;
			case "SPECIAL", "VERY_SPECIAL" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}

	// ---- input -------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			if (CustomUi.clickTab(mx, my, px, py, CustomUi.ARMOR_TAB)) {
				return true;
			}
			for (int i = 0; i < SLOTS.length; i++) {
				if (inside(mx, my, slotX(i), slotY(), SLOT, SLOT)) {
					slot = i;
					scroll = 0;
					status = "";
					search.setValue("");
					rebuild();
					return true;
				}
			}
			int gx = gridX();
			int gy = gridY();
			if (inside(mx, my, gx, gy, COLS * CELL, ROWS * CELL)) {
				int column = (int) ((mx - gx) / CELL);
				int row = (int) ((my - gy) / CELL);
				int index = (scroll + row) * COLS + column;
				if (index < entries.size()) {
					choose(entries.get(index));
				}
				return true;
			}
			if (inside(mx, my, gx + COLS * CELL + 8, gy, BAR_W, ROWS * CELL)) {
				draggingBar = true;
				dragBar(my);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingBar) {
			dragBar(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		return super.mouseReleased(event);
	}

	private void dragBar(double mouseY) {
		int max = maxScroll();
		if (max > 0) {
			double fraction = (mouseY - gridY()) / (ROWS * CELL);
			scroll = Math.max(0, Math.min(max, (int) Math.round(fraction * max)));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (inside(mouseX, mouseY, gridX(), gridY(), COLS * CELL + 8 + BAR_W, ROWS * CELL)) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- helpers -----------------------------------------------------------------------------

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** "#RRGGBB", or the name of an animated dye. */
	private static String colorName(int color) {
		AnimatedDyes.Dye dye = AnimatedDyes.dyeOf(color);
		if (dye != null) {
			return dye.name();
		}
		String name = StaticDyes.nameOf(color);
		return name != null ? name : "#" + hex(color);
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF);
	}

	/** Parses "#RRGGBB" or "RRGGBB" into 0xRRGGBB; throws IllegalArgumentException if that is not valid. */
	public static int parseColor(String text) {
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6) {
			throw new IllegalArgumentException("A color must look like #RRGGBB");
		}
		try {
			return Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("A color must look like #RRGGBB");
		}
	}
}
