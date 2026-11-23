package net.parz.mixin;

import com.mojang.blaze3d.platform.Window;
import net.parz.misc.WindowTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every title set through Minecraft's Window: another mod's title is replaced by the right one (see WindowTitle). */
@Mixin(Window.class)
public abstract class WindowSetTitleMixin {
	@ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
	private String parz$title(String title) {
		try {
			return WindowTitle.adjustSet(title);
		} catch (RuntimeException e) {
			return title;
		}
	}
}
