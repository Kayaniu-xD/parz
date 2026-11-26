package net.parz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import net.parz.armor.ArmorLook;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The texture of the SkyBlock head an item is drawn as. */
@Mixin(value = PlayerHeadSpecialRenderer.class, priority = 1500)
public class ItemHeadLookMixin {
	@ModifyExpressionValue(method = "extractArgument", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object parz$customHead(Object original, @Local(argsOnly = true) ItemStack stack) {
		ItemLook look = ItemLooks.of(stack);
		return look == null || look.head() == null ? original : ArmorLook.profileFor(look.head());
	}
}
