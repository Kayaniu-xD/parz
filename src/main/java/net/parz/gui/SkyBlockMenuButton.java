package net.parz.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;

/**
 * The Parz button in the /skyblockmenu: it replaces the glass pane in the top left slot.
 * A click opens the /pz menu (the click is not sent to the server).
 */
public final class SkyBlockMenuButton {
	/** Menu slot that is replaced (0 = top left corner of the chest menu). */
	private static final int BUTTON_SLOT = 0;
	private static final ItemStack ICON = new ItemStack(Items.ENCHANTED_BOOK);

	private SkyBlockMenuButton() {
	}

	/** True if this slot of this screen is the Parz button. */
	public static boolean isButton(Screen screen, Slot slot) {
		return slot != null
				&& slot.index == BUTTON_SLOT
				&& !(slot.container instanceof Inventory)
				&& isSkyBlockMenu(screen);
	}

	private static boolean isSkyBlockMenu(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?>)
				|| !ParzConfigDefinition.MENU_BUTTON.isEnabled()
				|| !SkyBlockDetector.featuresActive()) {
			return false;
		}
		String title = screen.getTitle().getString().replaceAll("§.", "");
		return title.contains("SkyBlock Menu");
	}

	/** Draws the button over the slot (x, y = top left pixel of the 16x16 slot area). */
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, boolean hovered) {
		graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0B0B0E);
		graphics.fill(x, y, x + 16, y + 16, hovered ? 0xFF3D3E45 : 0xFF2A2B31);
		graphics.fill(x, y, x + 16, y + 1, hovered ? 0xFF5A5B65 : 0xFF45464E);
		graphics.outline(x - 1, y - 1, 18, 18, hovered ? 0xFF6FD6E8 : 0xFF33343C);
		graphics.item(ICON, x, y);
	}

	public static void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		String title = "Parz Menu";
		String hint = "Click to open";
		int w = Math.max(font.width(title), font.width(hint)) + 10;
		int x = mouseX + 10;
		int y = mouseY - 8;
		graphics.fill(x, y, x + w, y + 28, 0xF00B0B0E);
		graphics.outline(x, y, w, 28, 0xFF6FD6E8);
		graphics.text(font, title, x + 5, y + 5, 0xFFE4E4EA, true);
		graphics.text(font, hint, x + 5, y + 16, 0xFF8E8F99, true);
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new ParzConfigScreen());
	}
}
