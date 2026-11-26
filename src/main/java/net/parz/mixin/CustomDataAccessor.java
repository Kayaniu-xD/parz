package net.parz.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the data of an item without copying it (only ever read, never changed). */
@Mixin(CustomData.class)
public interface CustomDataAccessor {
	@Accessor("tag")
	CompoundTag parz$tag();
}
