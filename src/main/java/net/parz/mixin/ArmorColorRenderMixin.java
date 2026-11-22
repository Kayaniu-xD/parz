package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Other Parz users see the armor color you chose with /pz customarmor, on the armor pieces that can show a
 * color at all (leather armor; other materials are not affected - that is how Minecraft itself renders armor).
 * A real dye (from Hypixel/vanilla) always wins over this cosmetic override.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorColorRenderMixin {

	@ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private ItemStack parz$armorColor(
			ItemStack itemStack,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			ItemStack original,
			EquipmentSlot slot,
			int lightCoords,
			HumanoidRenderState state
	) {
		try {
			if (itemStack.isEmpty() || !(state instanceof ArmorColorHolder holder)) {
				return itemStack;
			}
			Integer rgb = holder.parz$getArmorColor();
			if (rgb == null || itemStack.has(DataComponents.DYED_COLOR)) {
				// no color chosen, or a real dye is already there: the real dye always wins
				return itemStack;
			}
			ItemStack copy = itemStack.copy();
			copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			return copy;
		} catch (RuntimeException e) {
			return itemStack;
		}
	}
}
