package net.parz.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.parz.Debug;
import net.parz.armor.ArmorConfig;
import net.parz.armor.ArmorLook;
import net.parz.badge.ModUsers;
import net.parz.item.ItemLook;
import net.parz.item.ItemLooks;
import net.parz.item.ItemName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Sends your item and armor piece looks to the registry and fetches those of the other Parz users. Sent are only the
 * choices themselves with the ID of the item they belong to. The registry keeps them for 30 days after they were
 * last sent, so they also show in the profile of a player who is offline.
 */
public final class LookSync {
	/** After the last change this long passes before it is sent (a name is saved with every letter). */
	private static final long QUIET_MS = 4_000;
	private static final long SEND_EVERY_MS = 10_000;
	/** Unchanged looks are sent again this often, so the registry keeps them. */
	private static final long REFRESH_MS = 12 * 60 * 60_000L;
	private static final long FETCH_EVERY_MS = 60_000;
	private static final Pattern ITEM_UUID = Pattern.compile("[0-9a-fA-F-]{32,36}");
	private static final Pattern PLAYER_UUID = Pattern.compile("[0-9a-f]{32}");

	private static volatile long changedAt = 0;
	private static long sentAt = 0;
	private static String sent = null;
	private static long fetchedAt = 0;
	/** What was fetched: by player UUID the version and the looks. */
	private static final Map<String, String> versions = new HashMap<>();
	private static final Map<String, SharedLooks.Owner> fetched = new HashMap<>();
	private static ScheduledExecutorService executor;
	private static HttpClient http;
	private static int ticks = 0;

	private LookSync() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Debug.tick("shared looks", LookSync::tick));
		executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "parz-looks");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(() -> {
			try {
				sync();
			} catch (Exception e) {
				Debug.log("looks", () -> "registry: " + e);
			}
		}, 6, 5, TimeUnit.SECONDS);
	}

	/** One of your item or armor looks changed. */
	public static void changed() {
		changedAt = System.currentTimeMillis();
	}

	/** Remembers which items you carry. */
	private static void tick(Minecraft client) {
		if (++ticks % 10 != 0 || client.player == null) {
			return;
		}
		Inventory inventory = client.player.getInventory();
		Set<String> uuids = new HashSet<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			String uuid = ItemLooks.uuidOf(inventory.getItem(i));
			if (uuid != null) {
				uuids.add(uuid);
				ItemLooks.noteKind(uuid, inventory.getItem(i));
			}
		}
		SharedLooks.carried(uuids);
	}

	/** Your looks as they are sent. */
	public static JsonObject payload() {
		JsonObject items = new JsonObject();
		ItemLooks.all().forEach((uuid, look) -> {
			if (ITEM_UUID.matcher(uuid).matches()) {
				items.add(uuid, item(look, ItemLooks.idOf(uuid)));
			}
		});
		JsonObject armor = new JsonObject();
		ArmorConfig.pieces().forEach((key, piece) -> {
			if (ITEM_UUID.matcher(key).matches()) {
				JsonObject entry = new JsonObject();
				if (piece.skin() != null) {
					entry.addProperty("head", piece.skin());
				}
				if (piece.color() != null) {
					entry.addProperty("color", ArmorLook.hex(piece.color()));
				}
				armor.add(key, entry);
			}
		});
		JsonObject looks = new JsonObject();
		looks.add("items", items);
		looks.add("armor", armor);
		return looks;
	}

	private static JsonObject item(ItemLook look, String skyblockId) {
		JsonObject json = new JsonObject();
		if (skyblockId != null) {
			json.addProperty("id", skyblockId);
		}
		if (look.model() != null) {
			json.addProperty("model", look.model());
		}
		if (look.head() != null) {
			json.addProperty("head", look.head());
		}
		if (look.glint() != null) {
			json.addProperty("glint", look.glint());
		}
		if (look.name() != null) {
			JsonArray name = new JsonArray();
			for (ItemName.Run run : look.name()) {
				JsonArray piece = new JsonArray();
				piece.add(run.text());
				piece.add(run.mark().color());
				piece.add(run.mark().flags());
				name.add(piece);
			}
			json.add("name", name);
		}
		return json;
	}

	private static void sync() throws Exception {
		Minecraft client = Minecraft.getInstance();
		String base = ModUsers.registryUrl();
		if (base.isEmpty() || client.level == null || client.getUser() == null) {
			return;
		}
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
		}
		UUID id = client.getUser().getProfileId();
		String own = id.toString().replace("-", "");
		send(base, client.getUser().getName(), own);
		if (System.currentTimeMillis() - fetchedAt >= FETCH_EVERY_MS) {
			fetch(base, own);
		}
	}

	private static void send(String base, String name, String uuid) throws Exception {
		long now = System.currentTimeMillis();
		if (now - changedAt < QUIET_MS || now - sentAt < SEND_EVERY_MS) {
			return;
		}
		JsonObject looks = payload();
		boolean empty = looks.getAsJsonObject("items").isEmpty() && looks.getAsJsonObject("armor").isEmpty();
		String text = looks.toString();
		boolean changed = sent == null ? !empty || changedAt != 0 : !text.equals(sent);
		if (!changed && (empty || now - sentAt < REFRESH_MS)) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("uuid", uuid);
		body.add("looks", looks);
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/looks"))
						.header("Content-Type", "application/json")
						.header("User-Agent", "ParzMod")
						.timeout(Duration.ofSeconds(10))
						.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
				HttpResponse.BodyHandlers.ofString());
		sentAt = now;
		if (response.statusCode() == 200) {
			sent = text;
		} else {
			Debug.log("looks", () -> "registry answered " + response.statusCode() + " to your looks");
		}
	}

	private static String get(String url) throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
						.header("User-Agent", "ParzMod")
						.timeout(Duration.ofSeconds(10)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		return response.statusCode() == 200 ? response.body() : null;
	}

	/** The list says whose looks changed; only those are fetched. */
	private static void fetch(String base, String own) throws Exception {
		String list = get(base + "/looks");
		if (list == null) {
			return;
		}
		fetchedAt = System.currentTimeMillis();
		Set<String> present = new HashSet<>();
		boolean changed = false;
		for (JsonElement element : JsonParser.parseString(list).getAsJsonObject().getAsJsonArray("players")) {
			JsonObject player = element.getAsJsonObject();
			String uuid = player.get("uuid").getAsString();
			if (uuid.equals(own) || !PLAYER_UUID.matcher(uuid).matches()) {
				continue;
			}
			present.add(uuid);
			String version = player.get("v").getAsString();
			if (version.equals(versions.get(uuid))) {
				continue;
			}
			String looks = get(base + "/looks/" + uuid);
			if (looks != null) {
				fetched.put(uuid, SharedLooks.read(player.get("name").getAsString(),
						player.has("t") ? player.get("t").getAsLong() : 0, JsonParser.parseString(looks).getAsJsonObject()));
				versions.put(uuid, version);
				changed = true;
			}
		}
		changed |= fetched.keySet().retainAll(present);
		versions.keySet().retainAll(present);
		if (changed) {
			Map<String, SharedLooks.Owner> byName = new HashMap<>();
			for (SharedLooks.Owner owner : fetched.values()) {
				byName.put(owner.name().toLowerCase(java.util.Locale.ROOT), owner);
			}
			SharedLooks.replace(byName);
			Debug.log("looks", () -> "item looks of " + byName.size() + " players");
		}
	}
}
