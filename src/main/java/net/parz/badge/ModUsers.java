package net.parz.badge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.parz.Debug;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Who else uses Parz. Hypixel passes nothing between clients, so every Parz client reports its own account to a
 * small registry (see server/) and reads the list of the others. Shared: the Minecraft name and UUID of the
 * account, nothing else. Without a registry address only your own account has the badge.
 */
public final class ModUsers {
	/** The address of the registry (the Cloudflare Worker of server/), without a slash at the end. */
	private static final String REGISTRY_URL = "https://parz-registry.parzmod.workers.dev";
	private static final Path OVERRIDE = FabricLoader.getInstance().getConfigDir().resolve("parz-registry.txt");
	private static final long REGISTER_EVERY_MS = 3 * 60_000L;

	private static volatile Set<String> online = Set.of();
	private static long lastRegister = 0;
	private static HttpClient http;

	private ModUsers() {
	}

	private static ScheduledExecutorService executor;
	/** How the other users want their name to look (key: lower case name). */
	private static volatile java.util.Map<String, java.util.List<BadgeFormat.Run>> formats = java.util.Map.of();
	/** The armor color other users chose (key: lower case name, value: 0xRRGGBB). Only present if they set one. */
	private static volatile java.util.Map<String, Integer> armorColors = java.util.Map.of();

	public static void register() {
		executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "parz-registry");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(ModUsers::safeRefresh, 5, 60, TimeUnit.SECONDS);
	}

	private static void safeRefresh() {
		try {
			refresh();
		} catch (Exception e) {
			Debug.log("badge", () -> "registry: " + e);
		}
	}

	/** You changed how your name looks: it is sent to the registry at once and the tab list is drawn again. */
	public static void formatChanged() {
		version++;
		lastRegister = 0;
		if (executor != null) {
			executor.execute(ModUsers::safeRefresh);
		}
	}

	/** You changed your armor color: it is sent to the registry at once. */
	public static void armorColorChanged() {
		version++;
		lastRegister = 0;
		if (executor != null) {
			executor.execute(ModUsers::safeRefresh);
		}
	}

	/**
	 * The armor color a Parz user wants to show (0xRRGGBB), or null if they did not set one: your own choice for
	 * you, the one the registry reported for someone else, or null for everyone not on Parz.
	 */
	public static Integer armorColorOf(String name) {
		Minecraft client = Minecraft.getInstance();
		if (name != null && client.getUser() != null && client.getUser().getName().equalsIgnoreCase(name)) {
			return ArmorColorConfig.color();
		}
		return name == null ? null : armorColors.get(name.toLowerCase(Locale.ROOT));
	}

	/** How a player wants the name to look: your own format for you, the one of the registry for the others, or the default. */
	public static java.util.List<BadgeFormat.Run> formatOf(String name) {
		Minecraft client = Minecraft.getInstance();
		if (name != null && client.getUser() != null && client.getUser().getName().equalsIgnoreCase(name)) {
			return BadgeConfig.runs();
		}
		java.util.List<BadgeFormat.Run> runs = name == null ? null : formats.get(name.toLowerCase(Locale.ROOT));
		return runs != null ? runs : BadgeConfig.defaultRuns();
	}

	/** Accounts that always have the badge (the developer), also when the registry is not reachable. */
	private static final String[] ALWAYS = {"pay2loze"};
	private static volatile int version = 0;

	/** Changes every time the list of users changes (so cached names are made again). */
	public static int version() {
		return version;
	}

	/** True for you and for every player that reported to the registry. */
	public static boolean isUser(String name) {
		if (name == null) {
			return false;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		for (String always : ALWAYS) {
			if (always.equals(lower)) {
				return true;
			}
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getUser() != null && client.getUser().getName().equalsIgnoreCase(name)) {
			return true;
		}
		return online.contains(lower);
	}

	/** All names that get the badge (lower case): you, the developer and the users of the registry. */
	public static Set<String> names() {
		Set<String> names = new HashSet<>(online);
		for (String always : ALWAYS) {
			names.add(always);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getUser() != null) {
			names.add(client.getUser().getName().toLowerCase(Locale.ROOT));
		}
		return names;
	}

	private static String url() {
		try {
			if (Files.exists(OVERRIDE)) {
				String text = Files.readString(OVERRIDE, StandardCharsets.UTF_8).trim();
				if (!text.isEmpty()) {
					return text.replaceAll("/+$", "");
				}
			}
		} catch (Exception ignored) {
			// the built-in address is used
		}
		return REGISTRY_URL.replaceAll("/+$", "");
	}

	private static void refresh() throws Exception {
		Minecraft client = Minecraft.getInstance();
		String base = url();
		if (base.isEmpty() || client.level == null || client.getUser() == null) {
			return;
		}
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
		}
		long now = System.currentTimeMillis();
		if (now - lastRegister >= REGISTER_EVERY_MS) {
			UUID id = client.getUser().getProfileId();
			JsonObject registration = new JsonObject();
			registration.addProperty("name", client.getUser().getName());
			registration.addProperty("uuid", id.toString().replace("-", ""));
			registration.addProperty("format", BadgeConfig.wire());
			registration.addProperty("armorColor", ArmorColorConfig.wire());
			String json = registration.toString();
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/register"))
							.header("Content-Type", "application/json")
							.header("User-Agent", "ParzMod")
							.timeout(Duration.ofSeconds(10))
							.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
					HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				lastRegister = now;
			}
		}
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/users"))
						.header("User-Agent", "ParzMod")
						.timeout(Duration.ofSeconds(10)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			return;
		}
		Set<String> names = new HashSet<>();
		JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
		for (JsonElement element : body.getAsJsonArray("users")) {
			names.add(element.getAsString().toLowerCase(Locale.ROOT));
		}
		java.util.Map<String, java.util.List<BadgeFormat.Run>> newFormats = new java.util.HashMap<>();
		if (body.has("formats") && body.get("formats").isJsonObject()) {
			for (java.util.Map.Entry<String, JsonElement> entry : body.getAsJsonObject("formats").entrySet()) {
				java.util.List<BadgeFormat.Run> runs = BadgeFormat.fromWire(entry.getValue().getAsString());
				if (runs != null) {
					newFormats.put(entry.getKey().toLowerCase(Locale.ROOT), runs);
				}
			}
		}
		java.util.Map<String, Integer> newArmorColors = new java.util.HashMap<>();
		if (body.has("armorColors") && body.get("armorColors").isJsonObject()) {
			for (java.util.Map.Entry<String, JsonElement> entry : body.getAsJsonObject("armorColors").entrySet()) {
				try {
					newArmorColors.put(entry.getKey().toLowerCase(Locale.ROOT), Integer.parseInt(entry.getValue().getAsString(), 16));
				} catch (RuntimeException ignored) {
					// an invalid entry from the registry is skipped
				}
			}
		}
		if (!names.equals(online) || !newFormats.equals(formats) || !newArmorColors.equals(armorColors)) {
			version++;
		}
		online = names;
		formats = newFormats;
		armorColors = newArmorColors;
		Debug.log("badge", () -> "Parz users online: " + names.size());
	}
}
