package net.parz.compat.skyhanni;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SkyHanni reads the tab list: it gets the real names, so its compact tab list still knows which player a line
 * belongs to and draws the head. The custom name is put in when the line is drawn (SkyHanniTabRendererMixin).
 * These mixins are only used if SkyHanni is installed, and do nothing if its code looks different.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.utils.TabListData", remap = false)
public class SkyHanniTabDataMixin {
	@WrapOperation(method = "readTabList$lambda$0", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
	private static Component parz$realName(PlayerTabOverlay overlay, PlayerInfo info, Operation<Component> original) {
		PlayerBadge.SKYHANNI_READS.set(true);
		try {
			return original.call(overlay, info);
		} finally {
			PlayerBadge.SKYHANNI_READS.set(false);
		}
	}
}
