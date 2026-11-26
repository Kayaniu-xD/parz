package net.parz.share;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.parz.armor.ArmorConfig;
import net.parz.armor.ArmorLook;
import net.parz.badge.ModUsers;
import net.parz.item.ItemLooks;

/**
 * Which armor look to draw: for you, for another player and for the player models of profile viewers, which wear
 * copies of somebody's armor.
 */
public final class AvatarLooks {
	private AvatarLooks() {
	}

	/** The look of this player (or player model), or null if it looks normal. */
	public static ArmorLook of(Avatar entity) {
		boolean real = entity instanceof Player;
		if (real && entity == Minecraft.getInstance().player) {
			ArmorLook own = ArmorConfig.look();
			return own.isEmpty() ? null : own;
		}
		String name = real ? ((Player) entity).getGameProfile().name() : null;
		// what that player wears according to the registry, for armor the game tells no ID of
		ArmorLook told = real ? ModUsers.armorLookOf(name) : null;
		String skin = null;
		Integer[] colors = new Integer[3];
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			ItemStack stack = entity.getItemBySlot(slots[i]);
			String uuid = stack.isEmpty() ? null : ItemLooks.uuidOf(stack);
			SharedLooks.Piece piece = pieceFor(uuid, real);
			if (piece == null && told != null && (uuid == null || !SharedLooks.knows(name))) {
				piece = new SharedLooks.Piece(i == 0 ? told.skin() : null, i == 0 ? null : told.color(slots[i]));
			}
			if (piece == null) {
				continue;
			}
			if (i == 0) {
				skin = piece.skin();
			} else {
				colors[i - 1] = piece.color();
			}
		}
		ArmorLook look = new ArmorLook(skin, colors[0], colors[1], colors[2]);
		return look.isEmpty() ? null : look;
	}

	/**
	 * The look chosen for the armor piece with this ID. A real other player only shows what they share; a model or a
	 * menu may show your own armor, so your choice counts there first.
	 */
	public static SharedLooks.Piece pieceFor(String uuid, boolean otherPlayer) {
		if (uuid == null) {
			return null;
		}
		if (!otherPlayer) {
			ArmorConfig.Piece own = ArmorConfig.piece(uuid);
			if (own != null) {
				return new SharedLooks.Piece(own.skin(), own.color());
			}
		}
		return SharedLooks.carriedByYou(uuid) ? null : SharedLooks.piece(uuid);
	}

	/**
	 * The picture of an armor item in a menu: with its chosen head or color, or null if there is none for it.
	 */
	public static ItemStack armorIcon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		// what you wear is recognised even when it came without its ID
		String worn = ArmorConfig.wornKeyOf(stack);
		SharedLooks.Piece piece = pieceFor(worn != null ? worn : ItemLooks.uuidOf(stack), false);
		if (piece == null) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot slot = equippable != null ? equippable.slot() : stack.is(Items.PLAYER_HEAD) ? EquipmentSlot.HEAD : null;
		if (slot == null || (slot == EquipmentSlot.HEAD ? piece.skin() == null : piece.color() == null)) {
			return null;
		}
		ArmorLook look = new ArmorLook(piece.skin(), slot == EquipmentSlot.CHEST ? piece.color() : null,
				slot == EquipmentSlot.LEGS ? piece.color() : null, slot == EquipmentSlot.FEET ? piece.color() : null);
		return look.displayItem(slot, stack);
	}
}
