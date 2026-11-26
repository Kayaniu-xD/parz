package net.parz.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.parz.Debug;
import net.parz.armor.ArmorLook;
import net.parz.mixin.CustomDataAccessor;
import net.parz.share.LookSync;
import net.parz.share.SharedLooks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Your item changes (see {@link ItemLook}), chosen in the "Item" tab of /pz custom and saved in
 * config/parz-items.json. An item is recognised by the own ID SkyBlock gives it (the "uuid" in its data), so a
 * change stays with exactly that item wherever it is. The item itself is never changed. Your changes are shared with
 * the other Parz users (see net.parz.share), and theirs are shown to you here as well.
 */
public final class ItemLooks {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-items.json");

	private static volatile Map<String, ItemLook> looks = Map.of();
	/** The SkyBlock kind of the changed items (by their ID). */
	private static volatile Map<String, String> ids = Map.of();

	private ItemLooks() {
	}

	/** The own ID of a SkyBlock item, or null if it has none (such an item cannot be changed). */
	public static String uuidOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		// the tag is only read (copyTag would copy it many times per frame)
		String uuid = ((CustomDataAccessor) (Object) data).parz$tag().getStringOr("uuid", "");
		return uuid.isEmpty() ? null : uuid;
	}

	/** The SkyBlock kind of an item ("HYPERION"), or null. */
	public static String skyblockIdOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String id = ((CustomDataAccessor) (Object) data).parz$tag().getStringOr("id", "");
		return id.isEmpty() ? null : id;
	}

	/** The changes of this item: yours, or those another Parz user shares for it; null if there are none. */
	public static ItemLook of(ItemStack stack) {
		Map<String, ItemLook> now = looks;
		if (now.isEmpty() && !SharedLooks.hasItems()) {
			return null;
		}
		String uuid = uuidOf(stack);
		String holder = SharedLooks.renderOwner();
		if (uuid == null) {
			// in the hand of another player the game may not tell the ID: their item of the same kind
			return holder == null ? null : SharedLooks.itemOfKind(holder, skyblockIdOf(stack));
		}
		ItemLook own = now.get(uuid);
		if (own != null && holder == null) {
			return own;
		}
		if (SharedLooks.carriedByYou(uuid)) {
			return own; // an item of yours only shows your own choice
		}
		ItemLook shared = SharedLooks.item(uuid, holder);
		return shared != null ? shared : own;
	}

	/** All your changes, by the ID of the item. */
	public static Map<String, ItemLook> all() {
		return looks;
	}

	/** The SkyBlock kind of one of your changed items, or null. */
	public static String idOf(String uuid) {
		return ids.get(uuid);
	}

	/** The custom name of this item, or null. */
	public static Component nameOf(ItemStack stack) {
		ItemLook look = of(stack);
		return look == null || look.name() == null ? null : ItemName.component(look.name());
	}

	/** The changes of the item with this ID ({@link ItemLook#NONE} if there are none). */
	public static ItemLook get(String uuid) {
		return looks.getOrDefault(uuid, ItemLook.NONE);
	}

	/** Takes this as the look of the item and saves it. */
	public static synchronized void set(String uuid, ItemLook look) {
		Map<String, ItemLook> next = new HashMap<>(looks);
		if (look == null || look.isEmpty()) {
			next.remove(uuid);
		} else {
			next.put(uuid, look);
		}
		looks = Map.copyOf(next);
		rememberId(uuid);
		write();
		LookSync.changed();
	}

	/** Notes the kind of the item with this ID while you carry it. */
	private static void rememberId(String uuid) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Inventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (uuid.equals(uuidOf(inventory.getItem(i)))) {
				noteKind(uuid, inventory.getItem(i));
				return;
			}
		}
	}

	/** An item you carry: if you changed it and its kind is not known yet, it is noted (and sent with the change). */
	public static synchronized void noteKind(String uuid, ItemStack stack) {
		if (!looks.containsKey(uuid) || ids.containsKey(uuid)) {
			return;
		}
		String id = skyblockIdOf(stack);
		if (id != null) {
			Map<String, String> next = new HashMap<>(ids);
			next.put(uuid, id);
			ids = Map.copyOf(next);
			write();
			LookSync.changed();
		}
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, ItemLook> next = new HashMap<>();
			Map<String, String> nextIds = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				try {
					JsonObject item = entry.getValue().getAsJsonObject();
					ItemLook look = read(item);
					if (!look.isEmpty()) {
						next.put(entry.getKey(), look);
						if (item.has("id")) {
							nextIds.put(entry.getKey(), item.get("id").getAsString());
						}
					}
				} catch (RuntimeException e) {
					Debug.error("item config entry", e); // the other items are still loaded
				}
			}
			looks = Map.copyOf(next);
			ids = Map.copyOf(nextIds);
		} catch (Exception e) {
			Debug.error("item config", e);
		}
	}

	private static ItemLook read(JsonObject json) {
		String head = json.has("head") ? json.get("head").getAsString() : null;
		List<ItemName.Run> name = null;
		if (json.has("name")) {
			name = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("name")) {
				JsonObject run = element.getAsJsonObject();
				name.add(new ItemName.Run(run.get("text").getAsString(), new ItemName.Mark(
						run.has("color") ? run.get("color").getAsInt() : ItemName.NO_COLOR,
						run.has("format") ? run.get("format").getAsInt() : 0)));
			}
		}
		return new ItemLook(json.has("model") ? json.get("model").getAsString() : null,
				ArmorLook.validHash(head) ? head : null,
				json.has("glint") ? json.get("glint").getAsBoolean() : null,
				name);
	}

	private static void write() {
		try {
			JsonObject json = new JsonObject();
			looks.forEach((uuid, look) -> {
				JsonObject item = new JsonObject();
				if (ids.containsKey(uuid)) {
					item.addProperty("id", ids.get(uuid));
				}
				if (look.model() != null) {
					item.addProperty("model", look.model());
				}
				if (look.head() != null) {
					item.addProperty("head", look.head());
				}
				if (look.glint() != null) {
					item.addProperty("glint", look.glint());
				}
				if (look.name() != null) {
					JsonArray name = new JsonArray();
					for (ItemName.Run run : look.name()) {
						JsonObject piece = new JsonObject();
						piece.addProperty("text", run.text());
						if (run.mark().color() != ItemName.NO_COLOR) {
							piece.addProperty("color", run.mark().color());
						}
						if (run.mark().flags() != 0) {
							piece.addProperty("format", run.mark().flags());
						}
						name.add(piece);
					}
					item.add("name", name);
				}
				json.add(uuid, item);
			});
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Debug.error("item config", e);
		}
	}
}
