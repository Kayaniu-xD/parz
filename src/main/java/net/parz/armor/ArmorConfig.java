package net.parz.armor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.parz.Debug;
import net.parz.badge.ModUsers;
import net.parz.mixin.CustomDataAccessor;
import net.parz.skyblock.SkyBlockDetector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YOUR armor looks, chosen in /pz custom and saved in config/parz-armor.json. A choice belongs to ONE armor piece
 * (recognised by the own ID SkyBlock gives the item): it shows while you wear that piece, and other armor keeps its
 * normal look. What you wear right now ({@link #look()}) is sent to the registry, so every other Parz user sees it.
 */
public final class ArmorConfig {
	/** What was chosen for one armor piece: a head (shown instead of a helmet) or a color. */
	public record Piece(String name, String skin, Integer color) {
	}

	public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-armor.json");
	/** What you wear must stay the same this long before it is sent (warping and the wardrobe change it quickly). */
	private static final int SETTLE_TICKS = 40;
	/** And it is sent at most this often. */
	private static final long REPORT_EVERY_MS = 10_000;

	/** The choices, by the key of the piece (see {@link #keyOf}). */
	private static volatile Map<String, Piece> pieces = Map.of();
	/** The one look for all armor of earlier versions, until it was moved to pieces. */
	private static ArmorLook earlier = ArmorLook.NONE;
	/** What the registry was told. */
	private static volatile ArmorLook reported = ArmorLook.NONE;
	private static ArmorLook settling = ArmorLook.NONE;
	private static int settledTicks = 0;
	private static long lastReport = 0;
	private static boolean toldAboutEarlier = false;

	private ArmorConfig() {
	}

	public static void register() {
		load();
		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("armor look", ArmorConfig::tick));
	}

	// ---- which piece -------------------------------------------------------------------------

	/**
	 * What a choice is saved under: the own ID of the SkyBlock item; for an item without one its SkyBlock name, and
	 * without that the kind of item. Null for an empty slot.
	 */
	public static String keyOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = ((CustomDataAccessor) (Object) data).parz$tag();
			String uuid = tag.getStringOr("uuid", "");
			if (!uuid.isEmpty()) {
				return uuid;
			}
			String id = tag.getStringOr("id", "");
			if (!id.isEmpty()) {
				return "id:" + id;
			}
		}
		return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	/** The armor piece you wear in this slot (empty if none). */
	public static ItemStack worn(EquipmentSlot slot) {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? ItemStack.EMPTY : client.player.getItemBySlot(slot);
	}

	/** The name of the piece you wear in this slot, or null. */
	public static String wornName(EquipmentSlot slot) {
		ItemStack stack = worn(slot);
		return stack.isEmpty() ? null : stack.getHoverName().getString().replaceAll("§.", "").trim();
	}

	private static Piece pieceIn(EquipmentSlot slot) {
		String key = keyOf(worn(slot));
		return key == null ? null : pieces.get(key);
	}

	/** How your armor looks with the pieces you wear now. */
	public static ArmorLook look() {
		if (pieces.isEmpty()) {
			return ArmorLook.NONE;
		}
		Piece head = pieceIn(EquipmentSlot.HEAD);
		Piece chest = pieceIn(EquipmentSlot.CHEST);
		Piece legs = pieceIn(EquipmentSlot.LEGS);
		Piece feet = pieceIn(EquipmentSlot.FEET);
		return new ArmorLook(head == null ? null : head.skin(), chest == null ? null : chest.color(),
				legs == null ? null : legs.color(), feet == null ? null : feet.color());
	}

	// ---- choosing ----------------------------------------------------------------------------

	/** Puts the choice for the piece in the slot into the map (a choice of nothing removes it); false without a piece. */
	private static boolean put(Map<String, Piece> map, EquipmentSlot slot, String skin, Integer color) {
		String key = keyOf(worn(slot));
		if (key == null) {
			return false;
		}
		boolean head = slot == EquipmentSlot.HEAD;
		if (head ? skin == null : color == null) {
			map.remove(key);
		} else {
			map.put(key, new Piece(wornName(slot), head ? skin : null, head ? null : color));
		}
		return true;
	}

	/**
	 * Takes this head (helmet slot) or color (other slots) for the piece you wear in the slot; null = it looks normal
	 * again. Returns false if you wear nothing there.
	 */
	public static boolean choose(EquipmentSlot slot, String skin, Integer color) {
		Map<String, Piece> next = new HashMap<>(pieces);
		if (!put(next, slot, skin, color)) {
			return false;
		}
		save(next);
		return true;
	}

	/** One color (null = normal) for the chestplate, leggings and boots you wear; returns their names. */
	public static List<String> chooseWornColors(Integer color) {
		Map<String, Piece> next = new HashMap<>(pieces);
		List<String> names = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			if (slot != EquipmentSlot.HEAD && put(next, slot, null, color)) {
				names.add(wornName(slot));
			}
		}
		save(next);
		return names;
	}

	/** The pieces you wear now look normal again. */
	public static void resetWorn() {
		Map<String, Piece> next = new HashMap<>(pieces);
		for (EquipmentSlot slot : SLOTS) {
			put(next, slot, null, null);
		}
		save(next);
	}

	/** Every piece looks normal again. */
	public static void resetAll() {
		earlier = ArmorLook.NONE;
		save(Map.of());
	}

	/**
	 * Moves the look of an earlier version (one for all armor) to the pieces you wear now, as far as it has something
	 * for their slots; returns the names of those pieces.
	 */
	public static List<String> moveEarlierLook() {
		List<String> names = new ArrayList<>();
		if (earlier.isEmpty()) {
			return names;
		}
		Map<String, Piece> next = new HashMap<>(pieces);
		for (EquipmentSlot slot : SLOTS) {
			boolean head = slot == EquipmentSlot.HEAD;
			String skin = head ? earlier.skin() : null;
			Integer color = head ? null : earlier.color(slot);
			if ((head ? skin == null : color == null) || !put(next, slot, skin, color)) {
				continue;
			}
			names.add(wornName(slot));
			earlier = head ? earlier.withSkin(null) : earlier.withColor(slot, null);
		}
		if (!names.isEmpty()) {
			save(next);
		}
		return names;
	}

	private static void save(Map<String, Piece> next) {
		pieces = Map.copyOf(next);
		write();
		// your own choice is sent at once
		report(look());
	}

	// ---- telling the others ------------------------------------------------------------------

	private static void report(ArmorLook look) {
		reported = look;
		settling = look;
		settledTicks = 0;
		lastReport = System.currentTimeMillis();
		ModUsers.armorChanged();
	}

	/** You put on other armor: once it stays, the registry learns how you look now. */
	private static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		ArmorLook now = look();
		if (!now.equals(settling)) {
			settling = now;
			settledTicks = 0;
		} else {
			settledTicks++;
		}
		if (!now.equals(reported) && settledTicks >= SETTLE_TICKS && System.currentTimeMillis() - lastReport >= REPORT_EVERY_MS) {
			report(now);
		}
		if (!toldAboutEarlier && !earlier.isEmpty() && SkyBlockDetector.featuresActive()
				&& SLOTS.stream().anyMatch(slot -> !worn(slot).isEmpty())) {
			toldAboutEarlier = true;
			client.player.sendSystemMessage(Component.literal(
					"[Parz] Custom armor now belongs to single armor pieces. Wear the armor your look is for and open /pz custom to move it there.")
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The look as sent to the registry. */
	public static String wire() {
		return reported.wire();
	}

	// ---- the file ------------------------------------------------------------------------------

	public static void load() {
		pieces = Map.of();
		earlier = ArmorLook.NONE;
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!json.has("pieces")) {
				earlier = lookOf(json); // the file of an earlier version
				return;
			}
			if (json.has("earlier")) {
				earlier = lookOf(json.getAsJsonObject("earlier"));
			}
			Map<String, Piece> next = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("pieces").entrySet()) {
				JsonObject piece = entry.getValue().getAsJsonObject();
				String skin = text(piece, "skin");
				Integer color = ArmorLook.rgb(text(piece, "color"));
				if (ArmorLook.validHash(skin) || color != null) {
					next.put(entry.getKey(), new Piece(text(piece, "name"), ArmorLook.validHash(skin) ? skin : null, color));
				}
			}
			pieces = Map.copyOf(next);
		} catch (Exception e) {
			Debug.error("armor config", e);
		}
	}

	private static ArmorLook lookOf(JsonObject json) {
		String skin = text(json, "skin");
		return new ArmorLook(ArmorLook.validHash(skin) ? skin : null, ArmorLook.rgb(text(json, "chest")),
				ArmorLook.rgb(text(json, "legs")), ArmorLook.rgb(text(json, "feet")));
	}

	private static void write() {
		try {
			JsonObject json = new JsonObject();
			JsonObject all = new JsonObject();
			pieces.forEach((key, piece) -> {
				JsonObject entry = new JsonObject();
				add(entry, "name", piece.name());
				add(entry, "skin", piece.skin());
				add(entry, "color", ArmorLook.hex(piece.color()));
				all.add(key, entry);
			});
			json.add("pieces", all);
			if (!earlier.isEmpty()) {
				JsonObject old = new JsonObject();
				add(old, "skin", earlier.skin());
				add(old, "chest", ArmorLook.hex(earlier.chest()));
				add(old, "legs", ArmorLook.hex(earlier.legs()));
				add(old, "feet", ArmorLook.hex(earlier.feet()));
				json.add("earlier", old);
			}
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Debug.error("armor config", e);
		}
	}

	private static String text(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
	}

	private static void add(JsonObject json, String key, String value) {
		if (value != null && !value.isEmpty()) {
			json.addProperty(key, value);
		}
	}
}
