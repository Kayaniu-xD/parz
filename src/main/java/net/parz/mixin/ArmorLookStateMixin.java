package net.parz.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the armor look of the player this render state belongs to (set by {@link ArmorLookExtractMixin}). */
@Mixin(AvatarRenderState.class)
public abstract class ArmorLookStateMixin implements ArmorLookHolder {
	@Unique
	private ArmorLook parz$armorLook = null;

	@Override
	public ArmorLook parz$getArmorLook() {
		return parz$armorLook;
	}

	@Override
	public void parz$setArmorLook(ArmorLook look) {
		parz$armorLook = look;
	}
}
