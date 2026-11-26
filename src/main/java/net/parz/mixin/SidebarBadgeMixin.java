package net.parz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Team;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The badge in the scoreboard (the sidebar of the game), for example the party in a dungeon ("[M] Name [Lv43]").
 * Only the line that is DRAWN is changed, never the scoreboard itself: other mods read the teams from it (dungeon
 * maps) and need the real names.
 */
@Mixin(Hud.class)
public class SidebarBadgeMixin {
	@WrapOperation(method = "lambda$displayScoreboardSidebar$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent parz$badge(Team team, Component name, Operation<MutableComponent> original) {
		MutableComponent line = original.call(team, name);
		try {
			Component changed = PlayerBadge.applyInText(line);
			if (changed == line) {
				return line;
			}
			return changed instanceof MutableComponent mutable ? mutable : Component.empty().append(changed);
		} catch (RuntimeException ignored) {
			return line; // the text stays as it is
		}
	}
}
