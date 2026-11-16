package net.parz.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import net.parz.gui.PetsView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Draws the /pets menu as a book (see PetsView). The real menu stays open, only drawing and mouse input are
 * taken over while the screen is the pets menu.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class PetsMenuMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
	private void parz$renderPets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		if (PetsView.applies(screen)) {
			// no slot is hovered in the book: keys like 1-9 or Q must not act on a slot behind it
			this.hoveredSlot = null;
			PetsView.render(screen, graphics, mouseX, mouseY);
			ci.cancel();
		}
	}

	@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
	private void parz$clickPets(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (PetsView.applies((Screen) (Object) this)) {
			cir.setReturnValue(PetsView.click(event.x(), event.y(), event.button()));
		}
	}

	@Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
	private void parz$scrollPets(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (PetsView.applies((Screen) (Object) this)) {
			cir.setReturnValue(PetsView.scroll(mouseX, mouseY, scrollY));
		}
	}
}
