package net.parz.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The other end of the marked text of a text field (one end is the cursor). */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
	@Accessor("highlightPos")
	int parz$highlightPos();
}
