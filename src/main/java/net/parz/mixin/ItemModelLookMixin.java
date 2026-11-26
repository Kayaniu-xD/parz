package net.parz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import net.parz.share.SharedLooks;
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
		// another player may have chosen a model of a resource pack that you do not have
		boolean there = model != null
				&& ((ItemModelListAccessor) Minecraft.getInstance().getModelManager()).parz$itemModels().containsKey(model);
		return there ? model : original;
	}

	/** While the item of another player is drawn, their shared looks are used for it (see SharedLooks). */
	@WrapMethod(method = "appendItemLayers")
	private void parz$holder(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner,
							 int seed, Operation<Void> original) {
		String before = SharedLooks.renderOwner();
		SharedLooks.renderOwner(owner instanceof Player player && player != Minecraft.getInstance().player
				? player.getGameProfile().name() : null);
		try {
			original.call(state, stack, context, level, owner, seed);
		} finally {
			SharedLooks.renderOwner(before);
		}
	}
}
