package net.parz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.parz.armor.ArmorLook;
import net.parz.armor.ArmorLookHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Other Parz users see the armor look you chose with /pz custom: your head instead of the helmet and your
 * colors on the chestplate, leggings and boots. A colored piece is drawn as dyed leather armor, whatever the real
 * armor is: Minecraft only dyes armor whose equipment model can be dyed, and armor from SkyBlock (or iron, diamond,
 * ...) uses its own model, so a dye on the item alone would change nothing.
 */
// a late priority: if another mod also swaps the armor that is drawn, the color chosen in Parz comes last
@Mixin(value = HumanoidArmorLayer.class, priority = 2000)
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
			if (itemStack.get(DataComponents.EQUIPPABLE) == null) {
				return itemStack; // nothing that is drawn as armor
			}
			// A NEW piece of leather armor, never a copy of the real one. Other mods find "their" armor by the data of the
			// item (a dye set for it in Skyblocker, its SkyBlock ID, the dye it carries) and put their own color or model on
			// it; Skyblocker even remembers the ID of a copy after that data was removed. A new item has nothing to find,
			// so the color chosen in Parz is the one that is drawn.
			ItemStack plain = new ItemStack(slot == EquipmentSlot.CHEST ? Items.LEATHER_CHESTPLATE
					: slot == EquipmentSlot.LEGS ? Items.LEATHER_LEGGINGS : Items.LEATHER_BOOTS);
			plain.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			if (itemStack.hasFoil()) {
				plain.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return plain;
		} catch (RuntimeException e) {
			return itemStack;
		}
	}
}
