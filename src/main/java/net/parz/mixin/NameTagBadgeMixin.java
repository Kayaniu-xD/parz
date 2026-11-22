package net.parz.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The [H] badge above the head of a player. */
@Mixin(EntityRenderer.class)
public abstract class NameTagBadgeMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void parz$badge(Entity entity, CallbackInfoReturnable<Component> cir) {
		try {
			if (entity instanceof Player player) {
				cir.setReturnValue(PlayerBadge.apply(player.getGameProfile().name(), cir.getReturnValue()));
			}
		} catch (RuntimeException ignored) {
			// the name stays as it is
		}
	}
}
