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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.parz.Debug;
import net.parz.ParzClient;
import net.parz.badge.ModUsers;
import net.parz.mixin.CustomDataAccessor;
import net.parz.share.LookSync;
import net.parz.skyblock.SkyBlockDetector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/** A piece that was recognised by its data: the key of its choice, what kind of item it is and its SkyBlock name. */
	private record Seen(String key, Item item, String id) {
	}

	/** The piece last recognised in each slot (see {@link #wornKey}). */
	private static final Map<EquipmentSlot, Seen> seen = new EnumMap<>(EquipmentSlot.class);
	private static final Set<EquipmentSlot> loggedWithoutId = EnumSet.noneOf(EquipmentSlot.class);

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
		String uuid = dataOf(stack, "uuid");
		if (!uuid.isEmpty()) {
			return uuid;
		}
		String id = dataOf(stack, "id");
		if (!id.isEmpty()) {
			return "id:" + id;
		}
		return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	/**
	 * The key of the piece you wear in this slot, or null if you wear nothing there. The game can get a worn piece
	 * again without the data that tells which one it is (SkyBlock changes the color of animated dyes while you play);
	 * as long as the same kind of item stays in the slot, it is still the piece that was recognised there before.
	 */
	public static synchronized String wornKey(EquipmentSlot slot) {
		ItemStack stack = worn(slot);
		if (stack.isEmpty()) {
			seen.remove(slot);
			return null;
		}
		String uuid = dataOf(stack, "uuid");
		String id = dataOf(stack, "id");
		Seen last = seen.get(slot);
		if (uuid.isEmpty() && last != null && last.item() == stack.getItem() && (id.isEmpty() || id.equals(last.id()))) {
			if (!last.key().equals(keyOf(stack)) && loggedWithoutId.add(slot)) {
				// once per slot and game start, so the log tells whether this happens
				ParzClient.LOGGER.info("Armor in slot {} came without its ID, keeping {}: {}", slot, last.key(),
						stack.getComponentsPatch());
			}
			return last.key();
		}
		String key = keyOf(stack);
		if (!uuid.isEmpty() || !id.isEmpty()) {
			seen.put(slot, new Seen(key, stack.getItem(), id));
		} else {
			seen.remove(slot);
		}
		return key;
	}

	/** The key of this stack if it is one you wear right now, otherwise null. */
	public static String wornKeyOf(ItemStack stack) {
		for (EquipmentSlot slot : SLOTS) {
			if (!stack.isEmpty() && worn(slot) == stack) {
				return wornKey(slot);
			}
		}
		return null;
	}

	/** A text from the data of the item, or "". */
	private static String dataOf(ItemStack stack, String name) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : ((CustomDataAccessor) (Object) data).parz$tag().getStringOr(name, "");
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

	/** All your choices, by the key of the piece. */
	public static Map<String, Piece> pieces() {
		return pieces;
	}

	/** Your choice for the piece with this key, or null. */
	public static Piece piece(String key) {
		return pieces.get(key);
	}

	private static Piece pieceIn(EquipmentSlot slot) {
		String key = wornKey(slot);
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
		String key = wornKey(slot);
		if (key == null) {
			return false;
		}
		boolean head = slot == EquipmentSlot.HEAD;
		if (head ? skin == null : color == null) {
			map.remove(key);
		} else {
			// a piece that came without its ID may also have lost its name: keep the one saved before
			Piece before = map.get(key);
			String name = before != null && !key.equals(keyOf(worn(slot))) ? before.name() : wornName(slot);
			map.put(key, new Piece(name, head ? skin : null, head ? null : color));
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
		LookSync.changed();
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
