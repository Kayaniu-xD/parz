package net.parz.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SkullBlock;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import net.parz.badge.ModUsers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the render state of a player (an "Avatar") is filled in, remembers which armor look (if any) the registry
 * has for that player, for {@link ArmorLookRenderMixin} to use, and puts the custom head on the player: the vanilla
 * head layer draws a skull when the render state says there is one.
 */
@Mixin(AvatarRenderer.class)
public abstract class ArmorLookExtractMixin {

	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void parz$armorLook(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		try {
			ArmorLook look = null;
			if (entity instanceof Player player) {
				look = ModUsers.armorLookOf(player.getGameProfile().name());
			}
			((ArmorLookHolder) (Object) state).parz$setArmorLook(look);
			// headEquipment only holds helmets with an armor model; a SkyBlock helmet that is a player head (or any block or
			// item worn on the head) is drawn by the head layer instead, so the worn item itself has to be looked at
			boolean wearsHelmet = !state.headEquipment.isEmpty() || !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
			if (look != null && look.skin() != null && wearsHelmet) {
				// only a player who really wears something on the head gets the custom head
				state.wornHeadType = SkullBlock.Types.PLAYER;
				state.wornHeadProfile = look.headProfile();
				state.wornHeadAnimationPos = 0f;
			}
		} catch (RuntimeException ignored) {
			// no custom look this frame
		}
	}
}
