package net.parz.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.parz.armor.ArmorLook;
import net.parz.item.ItemLook;
import net.parz.item.ItemName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What other Parz users chose for their items and armor pieces, as the registry reports it (see {@link LookSync}).
 * Everything of it was written by other people, so every value is checked before it is used. Items and pieces are
 * recognised by the own ID SkyBlock gives them, so a look is also found in menus and profile viewers.
 */
public final class SharedLooks {
	/** One item of a player; skyblockId (may be null) finds it when the game does not tell its own ID. */
	public record Item(String skyblockId, ItemLook look) {
	}

	/** One armor piece of a player: a head (helmets) or a color. */
	public record Piece(String skin, Integer color) {
	}

	/** Everything of one player; updated = when the player sent it. */
	public record Owner(String name, long updated, Map<String, Item> items, Map<String, Piece> armor) {
	}

	private static final int MAX_ENTRIES = 200;
	private static final int MAX_NAME = 120;
	private static final Pattern ITEM_UUID = Pattern.compile("[0-9a-fA-F-]{32,36}");
	private static final Pattern SKYBLOCK_ID = Pattern.compile("[A-Za-z0-9_:;\\-]{1,64}");
	private static final Pattern MODEL = Pattern.compile("[a-z0-9_.\\-]{1,64}:[a-z0-9_./\\-]{1,128}");

	/** By lower case player name. */
	private static volatile Map<String, Owner> owners = Map.of();
	/** All items and pieces by their ID; if two players have one (it was traded), the newer entry wins. */
	private static volatile Map<String, ItemLook> items = Map.of();
	private static volatile Map<String, Piece> armor = Map.of();
	/** The IDs of the items you carry: only your own choices apply to them. */
	private static volatile Set<String> carried = Set.of();
	/** While an item is drawn in the hand of another player: that player. */
	private static final ThreadLocal<String> RENDER_OWNER = new ThreadLocal<>();

	private SharedLooks() {
	}

	static synchronized void replace(Map<String, Owner> next) {
		Map<String, ItemLook> allItems = new HashMap<>();
		Map<String, Piece> allArmor = new HashMap<>();
		next.values().stream().sorted(Comparator.comparingLong(Owner::updated)).forEach(owner -> {
			owner.items().forEach((uuid, item) -> allItems.put(uuid, item.look()));
			allArmor.putAll(owner.armor());
		});
		owners = Map.copyOf(next);
		items = Map.copyOf(allItems);
		armor = Map.copyOf(allArmor);
	}

	static void carried(Set<String> uuids) {
		carried = Set.copyOf(uuids);
	}

	public static boolean carriedByYou(String uuid) {
		return carried.contains(uuid);
	}

	public static boolean hasItems() {
		return !items.isEmpty();
	}

	/** True if this player shares looks (then a piece of theirs without an entry really looks normal). */
	public static boolean knows(String playerName) {
		return playerName != null && owners.containsKey(playerName.toLowerCase(Locale.ROOT));
	}

	/** The look of the item with this ID, preferably the one of the player who holds it; null if nobody shares one. */
	public static ItemLook item(String uuid, String holder) {
		if (holder != null) {
			Owner owner = owners.get(holder.toLowerCase(Locale.ROOT));
			if (owner != null) {
				Item item = owner.items().get(uuid);
				if (item != null) {
					return item.look();
				}
			}
		}
		return items.get(uuid);
	}

	/** For an item without a known ID in the hand of a player: their look for an item of this SkyBlock kind. */
	public static ItemLook itemOfKind(String holder, String skyblockId) {
		Owner owner = holder == null || skyblockId == null ? null : owners.get(holder.toLowerCase(Locale.ROOT));
		if (owner == null) {
			return null;
		}
		for (Item item : owner.items().values()) {
			if (skyblockId.equals(item.skyblockId())) {
				return item.look();
			}
		}
		return null;
	}

	public static Piece piece(String uuid) {
		return uuid == null ? null : armor.get(uuid);
	}

	public static String renderOwner() {
		return RENDER_OWNER.get();
	}

	public static void renderOwner(String name) {
		if (name == null) {
			RENDER_OWNER.remove();
		} else {
			RENDER_OWNER.set(name);
		}
	}

	// ---- reading what the registry sends -------------------------------------------------------

	/** The looks of one player; entries that are not valid are left out. */
	static Owner read(String playerName, long updated, JsonObject looks) {
		Map<String, Item> readItems = new HashMap<>();
		Map<String, Piece> readArmor = new HashMap<>();
		if (looks.has("items") && looks.get("items").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : looks.getAsJsonObject("items").entrySet()) {
				try {
					if (readItems.size() < MAX_ENTRIES && ITEM_UUID.matcher(entry.getKey()).matches()) {
						Item item = item(entry.getValue().getAsJsonObject());
						if (item != null) {
							readItems.put(entry.getKey(), item);
						}
					}
				} catch (RuntimeException ignored) {
					// an entry that is not valid is skipped
				}
			}
		}
		if (looks.has("armor") && looks.get("armor").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : looks.getAsJsonObject("armor").entrySet()) {
				try {
					if (readArmor.size() < MAX_ENTRIES && ITEM_UUID.matcher(entry.getKey()).matches()) {
						JsonObject piece = entry.getValue().getAsJsonObject();
						String skin = piece.has("head") ? piece.get("head").getAsString() : null;
						Integer color = piece.has("color") ? ArmorLook.rgb(piece.get("color").getAsString()) : null;
						if (ArmorLook.validHash(skin) || color != null) {
							readArmor.put(entry.getKey(), new Piece(ArmorLook.validHash(skin) ? skin : null, color));
						}
					}
				} catch (RuntimeException ignored) {
					// an entry that is not valid is skipped
				}
			}
		}
		return new Owner(playerName, updated, Map.copyOf(readItems), Map.copyOf(readArmor));
	}

	private static Item item(JsonObject json) {
		String id = json.has("id") ? json.get("id").getAsString() : null;
		String model = json.has("model") ? json.get("model").getAsString() : null;
		String head = json.has("head") ? json.get("head").getAsString() : null;
		Boolean glint = json.has("glint") ? json.get("glint").getAsBoolean() : null;
		List<ItemName.Run> name = json.has("name") ? name(json.getAsJsonArray("name")) : null;
		ItemLook look = new ItemLook(model != null && MODEL.matcher(model).matches() ? model : null,
				ArmorLook.validHash(head) ? head : null, glint, name);
		return look.isEmpty() ? null : new Item(id != null && SKYBLOCK_ID.matcher(id).matches() ? id : null, look);
	}

	/** A name as [[text, color, formats], ...]: no control characters or color codes, limited in length. */
	private static List<ItemName.Run> name(JsonArray json) {
		List<ItemName.Run> runs = new ArrayList<>();
		int length = 0;
		for (JsonElement element : json) {
			JsonArray run = element.getAsJsonArray();
			String text = run.get(0).getAsString().replaceAll("[\\p{Cc}§]", "");
			int color = run.get(1).getAsInt();
			int flags = run.get(2).getAsInt() & 31;
			text = text.substring(0, Math.min(text.length(), MAX_NAME - length));
			length += text.length();
			if (!text.isEmpty()) {
				runs.add(new ItemName.Run(text, new ItemName.Mark(color < 0 ? ItemName.NO_COLOR : color & 0xFFFFFF, flags)));
			}
		}
		return runs.isEmpty() ? null : List.copyOf(runs);
	}
}
