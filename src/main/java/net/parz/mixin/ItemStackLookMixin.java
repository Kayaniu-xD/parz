package net.parz.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Your custom item name and glint (see {@link ItemLooks}). Only the name that is SHOWN is changed (tooltips), so
 * everything that reads the real name of an item keeps working.
 */
@Mixin(value = ItemStack.class, priority = 1500)
public class ItemStackLookMixin {
	@WrapOperation(method = "getStyledHoverName", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component parz$customName(ItemStack stack, Operation<Component> original) {
		Component name = ItemLooks.nameOf(stack);
		return name != null ? name : original.call(stack);
	}

	@ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
	private boolean parz$customGlint(boolean original) {
		ItemLook look = ItemLooks.of((ItemStack) (Object) this);
		return look == null || look.glint() == null ? original : look.glint();
	}
}
