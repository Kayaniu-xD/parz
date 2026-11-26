package net.parz.mixin;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** All item models of the game and its resource packs (for "Select Model"). */
@Mixin(ModelManager.class)
public interface ItemModelListAccessor {
	@Accessor("bakedItemStackModels")
	Map<Identifier, ItemModel> parz$itemModels();
}
