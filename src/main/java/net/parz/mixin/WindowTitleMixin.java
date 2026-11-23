package net.parz.mixin;

import net.minecraft.client.Minecraft;
import net.parz.misc.WindowTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The title Minecraft makes for its window: the star of a modded game is removed in vanilla mode (see WindowTitle). */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void parz$title(CallbackInfoReturnable<String> cir) {
		try {
			cir.setReturnValue(WindowTitle.adjust(cir.getReturnValue()));
		} catch (RuntimeException ignored) {
			// the title stays as it is
		}
	}
}
