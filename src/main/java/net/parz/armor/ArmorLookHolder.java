package net.parz.armor;

/**
 * Added (by {@code ArmorLookStateMixin}) to the render state of every player, so the armor-rendering mixin can read
 * how that player wants their armor to look. This interface must NOT be in the mixin package: Mixin refuses to
 * load classes of that package directly, and the mixins cast to this interface.
 */
public interface ArmorLookHolder {
	/** The look this player chose, or null if they did not choose one. */
	ArmorLook parz$getArmorLook();

	void parz$setArmorLook(ArmorLook look);
}
