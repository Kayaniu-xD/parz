package net.parz.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.parz.armor.ArmorLook;
import net.parz.armor.HeadCatalog;
import net.parz.item.ItemLook;
import net.parz.mixin.ItemModelListAccessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** "Select Model": every item model of the game and your resource packs, and every SkyBlock head. */
final class ModelPickScreen extends Screen {
	private static final int CELL = 20;
	private static final int COLS = 16;
	private static final int ROWS = 7;
	private static final int BAR_W = 6;
	private static final int W = COLS * CELL + 8 + BAR_W + 24;
	private static final int H = ROWS * CELL + 58;

	/** One thing to click: the normal model (both null), an item model or a head. */
	private record Choice(String model, String head, String label, String search) {
	}

	private final Screen parent;
	private final ItemLook current;
	private final Consumer<ItemLook> chosen;
	private final Map<String, ItemStack> icons = new HashMap<>();
	private List<Choice> all = new ArrayList<>();
	private List<Choice> shown = new ArrayList<>();
	private int headCount = -1;
	private EditBox search;
	private int scroll = 0;
	private boolean dragging = false;
	private int px;
	private int py;

	ModelPickScreen(Screen parent, ItemLook current, Consumer<ItemLook> chosen) {
		super(Component.literal("Select Model"));
		this.parent = parent;
		this.current = current;
		this.chosen = chosen;
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;
		String query = search == null ? "" : search.getValue();
		search = new EditBox(this.font, px + 12, py + 22, W - 24, 18, Component.literal("Search"));
		search.setMaxLength(60);
		search.setValue(query);
		search.setHint(Component.literal("Search (item, or SkyBlock head)...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
		search.setResponder(text -> {
			filter();
			scroll = 0;
		});
		this.addRenderableWidget(search);
		this.setInitialFocus(search);
		collect();
	}

	/** All choices: first the item models of Minecraft, then those of packs and mods, then the heads. */
	private void collect() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice(null, null, "Default (normal model)", "default"));
		ItemModelListAccessor models = (ItemModelListAccessor) Minecraft.getInstance().getModelManager();
		models.parz$itemModels().keySet().stream()
				.filter(id -> !id.equals(Identifier.withDefaultNamespace("air")))
				.sorted(Comparator.comparing((Identifier id) -> !id.getNamespace().equals("minecraft")).thenComparing(Identifier::toString))
				.forEach(id -> list.add(new Choice(id.toString(), null, id.toString(),
						(id + " " + id.getPath().replace('_', ' ')).toLowerCase(Locale.ROOT))));
		List<HeadCatalog.Head> heads = HeadCatalog.heads();
		headCount = heads.size();
		for (HeadCatalog.Head head : heads) {
			list.add(new Choice(null, head.hash(), head.name(), head.searchText() + " head"));
		}
		all = list;
		filter();
	}

	private void filter() {
		String query = search.getValue().trim().toLowerCase(Locale.ROOT);
		shown = query.isEmpty() ? all : all.stream().filter(choice -> choice.search().contains(query)).toList();
		scroll = Math.min(scroll, maxScroll());
	}

	private int maxScroll() {
		return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
	}

	private int gridX() {
		return px + 12;
	}

	private int gridY() {
		return py + 46;
	}

	private ItemStack icon(Choice choice) {
		return icons.computeIfAbsent(choice.model() + "|" + choice.head(), key -> {
			if (choice.head() != null) {
				ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
				stack.set(DataComponents.PROFILE, ArmorLook.profileFor(choice.head()));
				return stack;
			}
			if (choice.model() == null) {
				return new ItemStack(Items.BARRIER);
			}
			ItemStack stack = new ItemStack(Items.STICK);
			stack.set(DataComponents.ITEM_MODEL, Identifier.parse(choice.model()));
			return stack;
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (HeadCatalog.heads().size() != headCount) {
			collect(); // the heads arrived
		}
		CustomUi.panel(graphics, px, py, W, H);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, "Select Model", px + 12, py + 9, CustomUi.TEXT, false);

		int gx = gridX();
		int gy = gridY();
		int w = COLS * CELL;
		int h = ROWS * CELL;
		graphics.fill(gx - 1, gy - 1, gx + w + 1, gy + h + 1, CustomUi.BOX_EDGE);
		graphics.fill(gx, gy, gx + w, gy + h, 0xFF11141C);
		Choice hovered = null;
		for (int i = 0; i < COLS * ROWS && scroll * COLS + i < shown.size(); i++) {
			Choice choice = shown.get(scroll * COLS + i);
			int x = gx + (i % COLS) * CELL;
			int y = gy + (i / COLS) * CELL;
			boolean hover = CustomUi.inside(mouseX, mouseY, x, y, CELL, CELL);
			boolean selected = Objects.equals(choice.model(), current.model()) && Objects.equals(choice.head(), current.head());
			graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, selected ? 0xFF3E8E57 : hover ? 0xAA6C7699 : 0x55000000);
			graphics.item(icon(choice), x + 2, y + 2);
			if (hover) {
				hovered = choice;
			}
		}
		if (shown.isEmpty()) {
			graphics.text(font, "No model matches your search.", gx + 6, gy + 8, CustomUi.TEXT_DIM, false);
		}

		int bx = gx + w + 8;
		graphics.fill(bx, gy, bx + BAR_W, gy + h, 0xFF11141C);
		int max = maxScroll();
		int thumb = max == 0 ? h : Math.max(14, h * ROWS / (max + ROWS));
		int thumbY = max == 0 ? gy : gy + (h - thumb) * scroll / max;
		graphics.fill(bx, thumbY, bx + BAR_W, thumbY + thumb, dragging ? CustomUi.SELECTED_EDGE : 0xFF7C86A6);

		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(hovered.label()), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			if (CustomUi.inside(mx, my, gridX(), gridY(), COLS * CELL, ROWS * CELL)) {
				int index = (scroll + (int) ((my - gridY()) / CELL)) * COLS + (int) ((mx - gridX()) / CELL);
				if (index < shown.size()) {
					Choice choice = shown.get(index);
					chosen.accept(choice.head() != null ? current.withHead(choice.head()) : current.withModel(choice.model()));
					onClose();
				}
				return true;
			}
			if (CustomUi.inside(mx, my, gridX() + COLS * CELL + 8, gridY(), BAR_W, ROWS * CELL)) {
				dragging = true;
				drag(my);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void drag(double mouseY) {
		double fraction = (mouseY - gridY()) / (ROWS * CELL);
		scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(fraction * maxScroll())));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			drag(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
