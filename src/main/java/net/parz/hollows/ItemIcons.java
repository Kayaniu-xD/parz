package net.parz.hollows;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.parz.Debug;
import net.parz.ParzClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The real SkyBlock look of an item. The list of all SkyBlock items comes from the public Hypixel API (no key
 * needed): for every item it says which model of the SkyBlock resource pack it uses (the pack the server sends
 * to you) or the texture of its head. The list is cached in config/parz-hypixel-items.json.
 */
final class ItemIcons {
	private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("parz-hypixel-items.json");
	private static final long MAX_AGE_MS = 3L * 24 * 3600 * 1000;

	/** What the API knows about an item: material, model of the resource pack, head texture, glint. */
	private record Entry(String material, String model, String skin, boolean glowing) {
	}

	private static volatile Map<String, Entry> items = Map.of();
	private static boolean started = false;

	private ItemIcons() {
	}

	/** Loads the cached list and, when it is old or missing, a fresh one (in the background). */
	static synchronized void load() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(() -> {
			try {
				if (Files.exists(CACHE)) {
					items = parse(JsonParser.parseString(Files.readString(CACHE, StandardCharsets.UTF_8)).getAsJsonArray());
					Debug.log("nucleus", () -> "item icons loaded from the cache: " + items.size() + " items");
				}
				boolean old = !Files.exists(CACHE) || System.currentTimeMillis() - Files.getLastModifiedTime(CACHE).toMillis() > MAX_AGE_MS;
				if (old) {
					download();
				}
			} catch (Exception e) {
				Debug.error("item icons", e);
			}
		}, "parz-item-icons");
		thread.setDaemon(true);
		thread.start();
	}

	private static void download() throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(60)).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			throw new java.io.IOException("Items API returned status " + response.statusCode());
		}
		JsonArray all = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("items");
		// only what is needed: name, material, model, head texture, glint
		JsonArray small = new JsonArray();
		for (JsonElement element : all) {
			JsonObject item = element.getAsJsonObject();
			JsonObject out = new JsonObject();
			out.addProperty("n", string(item, "name"));
			out.addProperty("m", string(item, "material"));
			if (item.has("item_model")) {
				out.addProperty("i", string(item, "item_model"));
			}
			if (item.has("skin") && item.get("skin").isJsonObject() && item.getAsJsonObject("skin").has("value")) {
				out.addProperty("s", item.getAsJsonObject("skin").get("value").getAsString());
			}
			if (item.has("glowing") && item.get("glowing").getAsBoolean()) {
				out.addProperty("g", true);
			}
			small.add(out);
		}
		Files.writeString(CACHE, small.toString(), StandardCharsets.UTF_8);
		items = parse(small);
		Debug.log("nucleus", () -> "item icons downloaded: " + items.size() + " items");
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
	}

	private static Map<String, Entry> parse(JsonArray array) {
		Map<String, Entry> map = new HashMap<>();
		for (JsonElement element : array) {
			JsonObject o = element.getAsJsonObject();
			String name = string(o, "n").toLowerCase(Locale.ROOT);
			if (name.isEmpty()) {
				continue;
			}
			Entry entry = new Entry(string(o, "m"), string(o, "i"), string(o, "s"), o.has("g"));
			Entry old = map.get(name);
			// several items can have the same name: the one with a real model is the one meant
			if (old == null || (old.model().isEmpty() && !entry.model().isEmpty())) {
				map.put(name, entry);
			}
		}
		return map;
	}

	/** The item as SkyBlock shows it, or null if it is not known (or its texture is not loaded in the game). */
	static ItemStack stackFor(String name) {
		Entry entry = items.get(name.toLowerCase(Locale.ROOT).trim());
		if (entry == null) {
			return null;
		}
		try {
			ItemStack stack = build(entry);
			if (stack != null && entry.glowing()) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return stack;
		} catch (RuntimeException e) {
			Debug.error("item icon " + name, e);
			return null;
		}
	}

	private static ItemStack build(Entry entry) {
		if (!entry.model().isEmpty()) {
			// "hypixel_skyblock:item/uncategorized/divans_alloy": a model of the resource pack of the server
			Identifier model = Identifier.parse(entry.model());
			if (!modelExists(model)) {
				return null;
			}
			Item base = material(entry.material());
			ItemStack stack = new ItemStack(base == null ? Items.PAPER : base);
			stack.set(DataComponents.ITEM_MODEL, model);
			return stack;
		}
		if (!entry.skin().isEmpty()) {
			ImmutableMultimap<String, Property> properties = ImmutableMultimap.of("textures", new Property("textures", entry.skin()));
			GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(entry.skin().getBytes(StandardCharsets.UTF_8)), "parz", new PropertyMap(properties));
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			return stack;
		}
		return null;
	}

	/** A plain vanilla material of the item list (DIAMOND, PAPER, ...), null for old names that no longer exist. */
	private static Item material(String name) {
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(name.toLowerCase(Locale.ROOT)));
		return item.orElse(null);
	}

	/** A model that is not loaded gives the same (missing) model as a name that surely does not exist. */
	private static boolean modelExists(Identifier model) {
		var manager = Minecraft.getInstance().getModelManager();
		return manager.getItemModel(model) != manager.getItemModel(Identifier.fromNamespaceAndPath("parz", "does_not_exist"));
	}
}
