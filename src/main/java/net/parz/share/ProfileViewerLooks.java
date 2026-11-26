package net.parz.share;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;

import java.util.ArrayList;
import java.util.List;

/** Shows the item and armor looks in the profile viewer of BetterPV (/pv), which builds and draws items itself. */
public final class ProfileViewerLooks {
	private ProfileViewerLooks() {
	}

	/**
	 * The item the viewer made from the profile data gets the own ID of the real item (the viewer leaves it out), so
	 * the looks of that item are found for it.
	 */
	public static void identify(Object slot, ItemStack stack) {
		if (slot == null || stack == null || stack.isEmpty()) {
			return;
		}
		try {
			Object attributes = slot.getClass().getMethod("extraAttributes").invoke(slot);
			String uuid = attributes instanceof CompoundTag tag ? tag.getStringOr("uuid", "") : "";
			if (uuid.isEmpty()) {
				return;
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
			tag.putString("uuid", uuid);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		} catch (ReflectiveOperationException ignored) {
			// another version of the viewer: its items stay as they are
		}
	}

	/** Draws the icon with the look of the item; false if it has none (then the viewer draws it as always). */
	public static boolean drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemStack shown = AvatarLooks.armorIcon(stack);
		if (shown == null) {
			ItemLook look = ItemLooks.of(stack);
			if (look == null || (look.model() == null && look.head() == null && look.glint() == null)) {
				return false;
			}
			shown = stack; // the model and the glint are changed when the item is drawn
		}
		float scale = Math.max(1, size) / 16f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.item(shown, 0, 0);
		graphics.pose().popMatrix();
		return true;
	}

	/** The tooltip with the custom name as its first line. */
	public static List<Component> named(ItemStack stack, List<Component> lines) {
		Component name = stack == null || stack.isEmpty() ? null : ItemLooks.nameOf(stack);
		if (name == null || lines == null || lines.isEmpty()) {
			return lines;
		}
		List<Component> changed = new ArrayList<>(lines);
		changed.set(0, name);
		return changed;
	}
}
