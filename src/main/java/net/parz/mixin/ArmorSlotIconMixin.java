package net.parz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.parz.armor.ArmorConfig;
import net.parz.armor.ArmorLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The four armor slots of your own inventory show the look you chose with /pz customarmor (the head you picked, the dyed
 * leather pieces) instead of the real armor, like the player model next to them. Only the picture of the slot is changed:
 * the tooltip, clicking and the real item are not.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ArmorSlotIconMixin {
	@ModifyVariable(
			method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V",
			at = @At(value = "STORE", ordinal = 0),
			ordinal = 0
	)
	private ItemStack parz$armorIcon(ItemStack stack, GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		try {
			if (stack.isEmpty() || !(slot.container instanceof Inventory inventory) || inventory.player != Minecraft.getInstance().player) {
				return stack;
			}
			EquipmentSlot equipment = switch (slot.getContainerSlot()) {
				case 36 -> EquipmentSlot.FEET;
				case 37 -> EquipmentSlot.LEGS;
				case 38 -> EquipmentSlot.CHEST;
				case 39 -> EquipmentSlot.HEAD;
				default -> null;
			};
			if (equipment == null) {
				return stack;
			}
			ArmorLook look = ArmorConfig.look();
			return look == null || look.isEmpty() ? stack : look.displayItem(equipment, stack);
		} catch (RuntimeException e) {
			return stack;
		}
	}
}
