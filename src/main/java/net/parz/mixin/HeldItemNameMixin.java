package net.parz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.parz.item.ItemLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Your custom item name in the line above the hotbar that appears when you switch items. */
@Mixin(Hud.class)
public class HeldItemNameMixin {
	@WrapOperation(method = "extractSelectedItemName", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component parz$customName(ItemStack stack, Operation<Component> original) {
		Component name = ItemLooks.nameOf(stack);
		return name != null ? name : original.call(stack);
	}
}
