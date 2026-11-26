package net.parz.compat.betterpv;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.parz.share.ProfileViewerLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The items BetterPV makes from a profile get the ID of the real item, and their tooltips the custom name. These
 * mixins are only used if BetterPV is installed, and do nothing if its code looks different.
 */
@Pseudo
@Mixin(targets = "dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory", remap = false)
public class BetterPvItemMixin {
	@Inject(method = {"toStack", "toEquippedStack"}, at = @At("RETURN"))
	private static void parz$identity(@Coerce Object slot, CallbackInfoReturnable<ItemStack> callback) {
		ProfileViewerLooks.identify(slot, callback.getReturnValue());
	}

	// "*": both tooltipLines methods (a name alone would only change the first one)
	@ModifyReturnValue(method = "tooltipLines*", at = @At("RETURN"))
	private static List<Component> parz$customName(List<Component> lines, @Local(argsOnly = true) ItemStack stack) {
		return ProfileViewerLooks.named(stack, lines);
	}
}
