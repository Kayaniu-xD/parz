package net.parz.compat.skyhanni;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The width of a line of SkyHanni's compact tab list with the custom name in it, so the columns are wide enough. */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabLine", remap = false)
public abstract class SkyHanniTabLineMixin {
	@Shadow
	public abstract Component getComponent();

	@Shadow
	public abstract Component getCustomName();

	@ModifyReturnValue(method = "getWidth", at = @At("RETURN"))
	private int parz$width(int width) {
		Component line = getCustomName() != null ? getCustomName() : getComponent();
		Component shown = PlayerBadge.applyInTextCached(line);
		if (shown == line) {
			return width;
		}
		Font font = Minecraft.getInstance().font;
		return width - font.width(line) + font.width(shown);
	}
}
