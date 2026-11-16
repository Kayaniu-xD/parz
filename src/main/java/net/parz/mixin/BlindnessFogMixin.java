package net.parz.mixin;

import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.parz.config.ParzConfigDefinition;
import net.parz.skyblock.SkyBlockDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Remove Blindness": only removes the VISUAL part of blindness (the black fog
 * and the darkened sky). The effect itself stays active, so all gameplay
 * effects (e.g. not being able to sprint) still apply.
 */
@Mixin(MobEffectFogEnvironment.class)
public abstract class BlindnessFogMixin {

	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void parz$hideBlindnessFog(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof BlindnessFogEnvironment
				&& ParzConfigDefinition.REMOVE_BLINDNESS.isEnabled()
				&& SkyBlockDetector.featuresActive()) {
			cir.setReturnValue(false);
		}
	}
}
