package net.parz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Your chosen item model (or head). The priority is high so your choice also wins over other mods that swap item
 * models at the same place (Catharsis, Skyblocker).
 */
@Mixin(value = ItemModelResolver.class, priority = 1500)
public class ItemModelLookMixin {
	@ModifyExpressionValue(method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object parz$customModel(Object original, @Local(argsOnly = true) ItemStack stack) {
		ItemLook look = ItemLooks.of(stack);
		if (look == null) {
			return original;
		}
		if (look.head() != null) {
			return Identifier.withDefaultNamespace("player_head");
		}
		Identifier model = look.model() == null ? null : Identifier.tryParse(look.model());
		return model != null ? model : original;
	}
}
