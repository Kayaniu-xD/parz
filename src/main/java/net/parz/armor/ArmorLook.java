package net.parz.armor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * How a player wants their armor to look for other Parz users: a head (the texture of any SkyBlock head item,
 * shown instead of the helmet) and a color for the chestplate, leggings and boots (each piece is then drawn as
 * dyed leather armor in that color). Every part is optional; null means "not changed".
 *
 * @param skin  the texture hash of the head (the last part of the textures.minecraft.net address), or null
 * @param chest 0xRRGGBB for the chestplate, or null
 * @param legs  0xRRGGBB for the leggings, or null
 * @param feet  0xRRGGBB for the boots, or null
 */
public record ArmorLook(String skin, Integer chest, Integer legs, Integer feet) {
	public static final ArmorLook NONE = new ArmorLook(null, null, null, null);

	/** Texture hashes are 59 to 64 hex digits in practice; this also keeps a bad value from the registry out. */
	private static final Pattern HASH = Pattern.compile("[0-9a-f]{20,64}");
	private static final Pattern WIRE = Pattern.compile("([0-9a-f]{20,64})?\\|([0-9a-f]{6})?\\|([0-9a-f]{6})?\\|([0-9a-f]{6})?");
	private static final Map<String, ResolvableProfile> PROFILES = new ConcurrentHashMap<>();

	public boolean isEmpty() {
		return skin == null && chest == null && legs == null && feet == null;
	}

	/** The color of this armor slot, or null (the helmet has no color: it is the head). */
	public Integer color(EquipmentSlot slot) {
		return switch (slot) {
			case CHEST -> chest;
			case LEGS -> legs;
			case FEET -> feet;
			default -> null;
		};
	}

	private static final Map<String, ItemStack> HEAD_ICONS = new ConcurrentHashMap<>();
	private static final Map<Long, ItemStack> ARMOR_ICONS = new ConcurrentHashMap<>();

	/**
	 * What an armor slot of your inventory shows: the real item is replaced by the item of this look (the head as a player head
	 * with the chosen texture, a colored piece as dyed leather armor). Empty slots and pieces without a choice stay as they are.
	 */
	public ItemStack displayItem(EquipmentSlot slot, ItemStack real) {
		if (real.isEmpty()) {
			return real;
		}
		if (slot == EquipmentSlot.HEAD) {
			return skin == null ? real : HEAD_ICONS.computeIfAbsent(skin, h -> {
				ItemStack head = new ItemStack(Items.PLAYER_HEAD);
				head.set(DataComponents.PROFILE, profileFor(h));
				return head;
			});
		}
		Integer rgb = color(slot);
		if (rgb == null) {
			return real;
		}
		Item item = slot == EquipmentSlot.CHEST ? Items.LEATHER_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.LEATHER_LEGGINGS : Items.LEATHER_BOOTS;
		long key = (long) slot.ordinal() << 32 | (rgb & 0xFFFFFFL);
		return ARMOR_ICONS.computeIfAbsent(key, k -> {
			ItemStack stack = new ItemStack(item);
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			return stack;
		});
	}

	public ArmorLook withSkin(String hash) {
		return new ArmorLook(hash, chest, legs, feet);
	}

	/** A copy with another color for one slot (null = no color); the helmet slot is ignored. */
	public ArmorLook withColor(EquipmentSlot slot, Integer rgb) {
		return switch (slot) {
			case CHEST -> new ArmorLook(skin, rgb, legs, feet);
			case LEGS -> new ArmorLook(skin, chest, rgb, feet);
			case FEET -> new ArmorLook(skin, chest, legs, rgb);
			default -> this;
		};
	}

	public ArmorLook withAllColors(Integer rgb) {
		return new ArmorLook(skin, rgb, rgb, rgb);
	}

	/** The form that is sent to the registry: "skin|chest|legs|feet", every part empty or lower-case hex. */
	public String wire() {
		return (skin == null ? "" : skin) + "|" + hex(chest) + "|" + hex(legs) + "|" + hex(feet);
	}

	/** The look of the registry form, or null if it is empty or not valid. */
	public static ArmorLook fromWire(String text) {
		if (text == null) {
			return null;
		}
		java.util.regex.Matcher m = WIRE.matcher(text);
		if (!m.matches()) {
			return null;
		}
		ArmorLook look = new ArmorLook(m.group(1), rgb(m.group(2)), rgb(m.group(3)), rgb(m.group(4)));
		return look.isEmpty() ? null : look;
	}

	public static boolean validHash(String hash) {
		return hash != null && HASH.matcher(hash).matches();
	}

	/** The profile of the head with this texture; the same object every time, so Minecraft loads the skin only once. */
	public static ResolvableProfile profileFor(String hash) {
		return PROFILES.computeIfAbsent(hash, h -> {
			String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + h + "\"}}}";
			String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
			Multimap<String, Property> properties = HashMultimap.create();
			properties.put("textures", new Property("textures", value));
			UUID id = UUID.nameUUIDFromBytes(("parz-head:" + h).getBytes(StandardCharsets.UTF_8));
			return ResolvableProfile.createResolved(new GameProfile(id, "", new PropertyMap(properties)));
		});
	}

	/** The profile of this look's head, or null if it has none. */
	public ResolvableProfile headProfile() {
		return skin == null ? null : profileFor(skin);
	}

	private static String hex(Integer rgb) {
		return rgb == null ? "" : String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF);
	}

	private static Integer rgb(String hex) {
		return hex == null ? null : Integer.parseInt(hex, 16);
	}
}
