package net.parz.item;

import java.util.List;

/**
 * How one of your items looks on your screen. Every part is optional; null means "not changed".
 *
 * @param model the id of an item model ("minecraft:diamond_sword")
 * @param head  the texture hash of a SkyBlock head the item is drawn as (instead of a model)
 * @param glint true = always the enchantment glint, false = never
 * @param name  the custom name
 */
public record ItemLook(String model, String head, Boolean glint, List<ItemName.Run> name) {
	public static final ItemLook NONE = new ItemLook(null, null, null, null);

	public boolean isEmpty() {
		return model == null && head == null && glint == null && name == null;
	}

	/** Draws the item with this model (null = its own); a chosen head is dropped. */
	public ItemLook withModel(String id) {
		return new ItemLook(id, null, glint, name);
	}

	/** Draws the item as the head with this texture; a chosen model is dropped. */
	public ItemLook withHead(String hash) {
		return new ItemLook(null, hash, glint, name);
	}

	public ItemLook withGlint(Boolean next) {
		return new ItemLook(model, head, next, name);
	}

	public ItemLook withName(List<ItemName.Run> next) {
		return new ItemLook(model, head, glint, next == null ? null : List.copyOf(next));
	}
}
