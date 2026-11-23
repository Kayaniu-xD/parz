package net.parz.armor;

import java.lang.reflect.Method;

/**
 * Makes the armor look work together with the Catharsis mod (part of the SkyBlock modpacks).
 *
 * <p>Catharsis draws the custom SkyBlock armor models (3D armor, re-textured armor, custom helmets) itself: for every
 * player it keeps a state for helmet, chestplate, leggings and boots, and when a slot has a custom model the normal
 * armor and head drawing is skipped or replaced. Our changes to the armor item would never be seen. So for every
 * slot the player chose something for, this sets that slot's Catharsis state to "no custom model" ({@code
 * Fallthrough}, what Catharsis itself uses for armor without a definition); then the normal drawing runs and our head
 * and colors show.
 *
 * <p>Everything is done by reflection: Catharsis is not required, and if its classes look different (another
 * version) this switches itself off instead of crashing.
 */
public final class CatharsisCompat {
	private static boolean checked = false;
	private static boolean available = false;
	private static Method getState;
	private static Method[] setters;
	private static Object fallthrough;

	private CatharsisCompat() {
	}

	/** Resets the Catharsis model of the chosen slots of this render state (see the class text). */
	public static void neutralize(Object renderState, boolean head, boolean chest, boolean legs, boolean feet) {
		if (!(head || chest || legs || feet)) {
			return;
		}
		try {
			if (!checked) {
				init(renderState);
			}
			if (!available) {
				return;
			}
			Object definition = getState.invoke(renderState);
			if (definition == null) {
				return;
			}
			boolean[] chosen = {head, chest, legs, feet};
			for (int i = 0; i < chosen.length; i++) {
				if (chosen[i]) {
					setters[i].invoke(definition, fallthrough);
				}
			}
		} catch (Throwable e) {
			available = false;
			checked = true;
		}
	}

	private static synchronized void init(Object renderState) {
		if (checked) {
			return;
		}
		checked = true;
		try {
			getState = renderState.getClass().getMethod("catharsis$getArmorDefinitionRenderState");
			Object definition = getState.invoke(renderState);
			ClassLoader loader = definition.getClass().getClassLoader();
			Class<?> modelState = Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState", false, loader);
			Class<?> definitionClass = definition.getClass();
			setters = new Method[]{
					definitionClass.getMethod("setHead", modelState),
					definitionClass.getMethod("setChest", modelState),
					definitionClass.getMethod("setLegs", modelState),
					definitionClass.getMethod("setFeet", modelState)
			};
			fallthrough = Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState$Fallthrough", true, loader)
					.getField("INSTANCE").get(null);
			available = true;
		} catch (Throwable e) {
			// Catharsis is not installed (or too different): nothing to do
			available = false;
		}
	}
}
