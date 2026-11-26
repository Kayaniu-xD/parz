package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

/**
 * Other Parz users see the armor look you chose with /pz custom: your head instead of the helmet and your
 * colors on the chestplate, leggings and boots. A colored piece is drawn as dyed leather armor, whatever the real
 * armor is: Minecraft only dyes armor whose equipment model can be dyed, and armor from SkyBlock (or iron, diamond,
 * ...) uses its own model, so a dye on the item alone would change nothing.
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
			Integer rgb = look.rgb(slot); // an animated dye: its color of this moment
			if (rgb == null) {
				return itemStack;
			}
			ItemStack copy = itemStack.copy();
			Equippable equippable = copy.get(DataComponents.EQUIPPABLE);
			if (equippable != null && !Optional.of(EquipmentAssets.LEATHER).equals(equippable.assetId())) {
				// the same piece of armor, but with the (dyeable) leather model
				copy.set(DataComponents.EQUIPPABLE, new Equippable(equippable.slot(), equippable.equipSound(),
						Optional.of(EquipmentAssets.LEATHER), equippable.cameraOverlay(), equippable.allowedEntities(),
						equippable.dispensable(), equippable.swappable(), equippable.damageOnHurt(),
						equippable.equipOnInteract(), equippable.canBeSheared(), equippable.shearingSound()));
			}
			copy.remove(DataComponents.TRIM);
			// Animated dyes (and custom armor models of resource packs / other mods) are looked up by the data of the item
			// (its SkyBlock id, the dye it carries, custom model data), not by the color: without this data the copy is plain
			// dyed leather armor, and the animation (or the other mod) cannot take the color away again
			copy.remove(DataComponents.CUSTOM_DATA);
			copy.remove(DataComponents.CUSTOM_MODEL_DATA);
			copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			return copy;
		} catch (RuntimeException e) {
			return itemStack;
		}
	}
}
