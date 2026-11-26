package net.parz.compat.skyhanni;

import net.minecraft.network.chat.Component;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The custom name in a line of SkyHanni's compact tab list, put in at the moment it is drawn (next to the head). */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabListRenderer", remap = false)
public class SkyHanniTabRendererMixin {
	@ModifyArg(method = "drawColumns", index = 1, at = @At(value = "INVOKE",
			target = "Lat/hannibal2/skyhanni/utils/GuiRenderUtils;drawString$default(Lat/hannibal2/skyhanni/utils/GuiRenderUtils;Lnet/minecraft/network/chat/Component;FFIZILjava/lang/Object;)V"))
	private Component parz$customName(Component line) {
		return PlayerBadge.applyInTextCached(line);
	}
}
