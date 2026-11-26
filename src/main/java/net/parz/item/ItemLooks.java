package net.parz.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.parz.Debug;
import net.parz.armor.ArmorLook;
import net.parz.mixin.CustomDataAccessor;

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
 * change stays with exactly that item wherever it is. Everything happens on your screen only: the item itself is
 * never changed and nothing is sent anywhere.
 */
public final class ItemLooks {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-items.json");

	private static volatile Map<String, ItemLook> looks = Map.of();

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

	/** The changes of this item, or null if it has none. */
	public static ItemLook of(ItemStack stack) {
		Map<String, ItemLook> now = looks;
		if (now.isEmpty()) {
			return null;
		}
		String uuid = uuidOf(stack);
		return uuid == null ? null : now.get(uuid);
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
		write();
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, ItemLook> next = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				try {
					ItemLook look = read(entry.getValue().getAsJsonObject());
					if (!look.isEmpty()) {
						next.put(entry.getKey(), look);
					}
				} catch (RuntimeException e) {
					Debug.error("item config entry", e); // the other items are still loaded
				}
			}
			looks = Map.copyOf(next);
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
