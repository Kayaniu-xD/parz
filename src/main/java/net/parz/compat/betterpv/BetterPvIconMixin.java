package net.parz.compat.betterpv;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.parz.share.ProfileViewerLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BetterPV draws some icons straight from the textures of the Hypixel resource pack. An item with a custom look is
 * drawn with that look instead.
 */
@Pseudo
@Mixin(targets = "dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer", remap = false)
public class BetterPvIconMixin {
	@Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Ljava/lang/String;III)V",
			at = @At("HEAD"), cancellable = true)
	private static void parz$customLook(GuiGraphicsExtractor graphics, ItemStack stack, String id, String model, int x, int y,
										int size, CallbackInfo callback) {
		if (ProfileViewerLooks.drawIcon(graphics, stack, x, y, size)) {
			callback.cancel();
		}
	}
}
