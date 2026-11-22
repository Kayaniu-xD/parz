package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Remove Damage Shake": the camera tilts and shakes for a moment when you take damage. This skips that tilt.
 * The red hurt flash and everything else stays.
 */
@Mixin(GameRenderer.class)
public abstract class HurtShakeMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void parz$noHurtShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
		if (ParzConfigDefinition.NO_HURT_SHAKE.isEnabled() && SkyBlockDetector.featuresActive()) {
			ci.cancel();
		}
	}
}
