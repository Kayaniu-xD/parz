package net.parz.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.parz.gui.SkyBlockMenuButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the top left glass pane of the /skyblockmenu with a Parz button.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SkyBlockMenuMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V", at = @At("HEAD"), cancellable = true)
	private void parz$drawButton(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (SkyBlockMenuButton.isButton((Screen) (Object) this, slot)) {
			SkyBlockMenuButton.draw(graphics, slot.x, slot.y, slot == this.hoveredSlot);
			ci.cancel();
		}
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	private void parz$clickButton(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (SkyBlockMenuButton.isButton((Screen) (Object) this, slot)) {
			SkyBlockMenuButton.open();
			ci.cancel();
		}
	}

	@Inject(method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), cancellable = true)
	private void parz$tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (SkyBlockMenuButton.isButton((Screen) (Object) this, this.hoveredSlot)) {
			SkyBlockMenuButton.drawTooltip(graphics, mouseX, mouseY);
			ci.cancel();
		}
	}
}
