package net.parz.badge;

/**
 * Added (by {@link net.parz.mixin.ArmorColorStateMixin}) to the render state of every player, so the armor-rendering mixin
 * can read which color, if any, that player chose for their (leather) armor.
 */
public interface ArmorColorHolder {
	/** The color (0xRRGGBB), or null if that player did not set one. */
	Integer parz$getArmorColor();

	void parz$setArmorColor(Integer rgb);
}
