package net.parz.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.parz.badge.ModUsers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the render state of a player (an "Avatar") is filled in, remembers which armor color (if any) the
 * registry has for that player, for {@link ArmorColorRenderMixin} to use.
 */
@Mixin(AvatarRenderer.class)
public abstract class ArmorColorExtractMixin {

	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void parz$armorColor(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		try {
			Integer rgb = entity instanceof Player player ? ModUsers.armorColorOf(player.getGameProfile().name()) : null;
			((ArmorColorHolder) (Object) state).parz$setArmorColor(rgb);
		} catch (RuntimeException ignored) {
			// no custom color this frame
		}
	}
}
