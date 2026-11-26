package net.parz.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.parz.item.ItemLooks;

import java.util.List;
import java.util.function.Consumer;

/** "Select Item": your inventory; only items with an own SkyBlock ID can be picked. */
final class ItemPickScreen extends Screen {
	private static final int CELL = 22;
	private static final int W = 9 * CELL + 24;
	private static final int H = 5 * CELL + 52;
	/** The slots of the inventory in the order they are shown (-1 = gap): armor and off hand, bag, hotbar. */
	private static final int[] SLOTS = slots();

	private final Screen parent;
	private final Consumer<String> chosen;
	private int px;
	private int py;

	ItemPickScreen(Screen parent, Consumer<String> chosen) {
		super(Component.literal("Select Item"));
		this.parent = parent;
		this.chosen = chosen;
	}

	private static int[] slots() {
		int[] slots = new int[45];
		int[] top = {39, 38, 37, 36, -1, 40, -1, -1, -1};
		for (int i = 0; i < 45; i++) {
			slots[i] = i < 9 ? top[i] : i < 36 ? i : i - 36;
		}
		return slots;
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;
	}

	private int cellX(int index) {
		return px + 12 + (index % 9) * CELL;
	}

	/** A small gap sets the armor and the hotbar apart. */
	private int cellY(int index) {
		int row = index / 9;
		return py + 24 + row * CELL + (row > 0 ? 5 : 0) + (row == 4 ? 5 : 0);
	}

	private static ItemStack stackAt(int index) {
		Minecraft client = Minecraft.getInstance();
		return client.player == null || SLOTS[index] < 0 ? ItemStack.EMPTY : client.player.getInventory().getItem(SLOTS[index]);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		CustomUi.panel(graphics, px, py, W, H);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, "Select Item", px + 12, py + 9, CustomUi.TEXT, false);
		ItemStack hovered = ItemStack.EMPTY;
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] < 0) {
				continue;
			}
			ItemStack stack = stackAt(i);
			int x = cellX(i);
			int y = cellY(i);
			boolean usable = !stack.isEmpty() && ItemLooks.uuidOf(stack) != null;
			boolean hover = CustomUi.inside(mouseX, mouseY, x, y, CELL, CELL);
			graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, hover && usable ? CustomUi.HOVER : usable ? CustomUi.BOX : 0x55000000);
			if (!stack.isEmpty()) {
				graphics.item(stack, x + 3, y + 3);
				if (!usable) {
					graphics.nextStratum();
					graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xB0101218);
				}
				if (hover) {
					hovered = stack;
				}
			}
		}
		if (!hovered.isEmpty()) {
			if (ItemLooks.uuidOf(hovered) != null) {
				graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
			} else {
				graphics.setComponentTooltipForNextFrame(font, List.of(hovered.getStyledHoverName(),
						Component.literal("Cannot be customized").withStyle(ChatFormatting.RED)), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = 0; i < SLOTS.length; i++) {
				if (CustomUi.inside(event.x(), event.y(), cellX(i), cellY(i), CELL, CELL)) {
					ItemStack stack = stackAt(i);
					String uuid = stack.isEmpty() ? null : ItemLooks.uuidOf(stack);
					if (uuid != null) {
						chosen.accept(uuid);
						onClose();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
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
