package net.parz.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The badge in the scoreboard (the sidebar): the lines of the SkyBlock sidebar, for example the party in a dungeon
 * ("[M] Name [Lv43]"), are the prefix and suffix of scoreboard teams. Changing them here changes every place
 * that reads them: the sidebar of the game and the mods that draw their own scoreboard from it.
 */
@Mixin(PlayerTeam.class)
public abstract class TeamBadgeMixin {
	@Inject(method = "getPlayerPrefix", at = @At("RETURN"), cancellable = true)
	private void parz$prefix(CallbackInfoReturnable<Component> cir) {
		try {
			cir.setReturnValue(PlayerBadge.applyInTextCached(cir.getReturnValue()));
		} catch (RuntimeException ignored) {
			// the text stays as it is
		}
	}

	@Inject(method = "getPlayerSuffix", at = @At("RETURN"), cancellable = true)
	private void parz$suffix(CallbackInfoReturnable<Component> cir) {
		try {
			cir.setReturnValue(PlayerBadge.applyInTextCached(cir.getReturnValue()));
		} catch (RuntimeException ignored) {
			// the text stays as it is
		}
	}
}
