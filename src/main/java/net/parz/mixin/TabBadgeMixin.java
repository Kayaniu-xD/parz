package net.parz.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The [H] badge in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void parz$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		try {
			cir.setReturnValue(PlayerBadge.apply(info.getProfile().name(), cir.getReturnValue()));
		} catch (RuntimeException ignored) {
			// the name stays as it is
		}
	}
}
