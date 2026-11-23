package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import net.parz.armor.CatharsisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right before a player is drawn (after every mod has filled in the render state) the custom models of other mods
 * are switched off for the armor slots the player chose something for, see {@link CatharsisCompat}.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ArmorLookSubmitMixin {

	@Inject(
			method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD")
	)
	private void parz$otherModsOff(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
								   CameraRenderState camera, CallbackInfo ci) {
		try {
			if (state instanceof ArmorLookHolder holder) {
				ArmorLook look = holder.parz$getArmorLook();
				if (look != null) {
					CatharsisCompat.neutralize(state, look.skin() != null, look.chest() != null, look.legs() != null, look.feet() != null);
				}
			}
		} catch (RuntimeException ignored) {
			// the look is drawn without this
		}
	}
}
