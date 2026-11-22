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

	public static void register() {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "parz-registry");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(() -> {
			try {
				refresh();
			} catch (Exception e) {
				Debug.log("badge", () -> "registry: " + e);
			}
		}, 5, 60, TimeUnit.SECONDS);
	}

	/** True for you and for every player that reported to the registry. */
	public static boolean isUser(String name) {
		if (name == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getUser() != null && client.getUser().getName().equalsIgnoreCase(name)) {
			return true;
		}
		return online.contains(name.toLowerCase(Locale.ROOT));
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
			String json = "{\"name\":\"" + client.getUser().getName() + "\",\"uuid\":\"" + id.toString().replace("-", "") + "\"}";
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
		online = names;
		Debug.log("badge", () -> "Parz users online: " + names.size());
	}
}
