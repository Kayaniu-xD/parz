package net.parz.mixin;

import net.parz.badge.ArmorColorHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the armor color of the player this render state belongs to (set by {@link ArmorColorExtractMixin}). */
@Mixin(AvatarRenderState.class)
public abstract class ArmorColorStateMixin implements ArmorColorHolder {
	@Unique
	private Integer parz$armorColor = null;

	@Override
	public Integer parz$getArmorColor() {
		return parz$armorColor;
	}

	@Override
	public void parz$setArmorColor(Integer rgb) {
		parz$armorColor = rgb;
	}
}
