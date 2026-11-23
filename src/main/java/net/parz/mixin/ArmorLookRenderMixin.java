package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Other Parz users see the armor look you chose with /pz customarmor: your colors on the armor pieces that can
 * show a color at all (leather armor; other materials are not affected - that is how Minecraft itself renders
 * armor), and your head instead of the helmet. The color you chose also wins over a dye the item already has.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorLookRenderMixin {

	@ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private ItemStack parz$armorLook(
			ItemStack itemStack,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			ItemStack original,
			EquipmentSlot slot,
			int lightCoords,
			HumanoidRenderState state
	) {
		try {
			if (itemStack.isEmpty() || !(state instanceof ArmorLookHolder holder)) {
				return itemStack;
			}
			ArmorLook look = holder.parz$getArmorLook();
			if (look == null) {
				return itemStack;
			}
			if (slot == EquipmentSlot.HEAD) {
				// the custom head is drawn by the head layer; the helmet itself would only be in the way
				return look.skin() != null ? ItemStack.EMPTY : itemStack;
			}
			Integer rgb = look.color(slot);
			if (rgb == null) {
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
