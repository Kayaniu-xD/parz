package net.parz.hollows;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.parz.Debug;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The pictures of the items come from the Hypixel SkyBlock wiki (hypixelskyblock.minecraft.wiki), the same icons
 * that are shown on its pages, for example on the Crystal Nucleus page. They do not depend on any resource pack.
 * Each picture is downloaded once (in the background) and kept in config/parz-icons/.
 */
public final class ItemIcons {
	private static final String HOST = "hypixelskyblock.minecraft.wiki";
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("parz-icons");
	/** The size the pictures are saved in (the wiki has 160x160 and 300x300). */
	private static final int SIZE = 64;

	private enum Status { LOADING, READY, MISSING }

	private static final Map<String, Status> STATUS = new ConcurrentHashMap<>();
	/** Only used on the render thread. */
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "parz-item-icons");
		thread.setDaemon(true);
		return thread;
	});
	private static HttpClient http;

	private ItemIcons() {
	}

	/** Starts the download of the pictures (if they are not saved yet). */
	public static void prefetch(Collection<String> names) {
		for (String name : names) {
			request(name);
		}
	}

	private static void request(String name) {
		String key = key(name);
		if (STATUS.putIfAbsent(key, Status.LOADING) == null) {
			POOL.execute(() -> {
				try {
					STATUS.put(key, fetch(name) ? Status.READY : Status.MISSING);
				} catch (Exception e) {
					STATUS.put(key, Status.MISSING);
					Debug.log("nucleus", () -> "no wiki picture for " + name + ": " + e);
				}
			});
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT).trim();
	}

	private static Path file(String name) {
		return DIR.resolve(key(name).replaceAll("[^a-z0-9]+", "_") + ".png");
	}

	/** Items whose picture has another name on the wiki than the item itself. */
	private static final Map<String, String> ALIASES = Map.of(
			"enchanted coal block", "Enchanted Block of Coal",
			"thunder shard", "Thunder Head");

	private static boolean fetch(String name) throws Exception {
		Path target = file(name);
		if (Files.exists(target)) {
			return true;
		}
		java.util.List<String> names = new java.util.ArrayList<>();
		names.add(name.trim());
		String alias = ALIASES.get(key(name));
		if (alias != null) {
			names.add(alias);
		}
		for (String candidate : names) {
			if (save(target, candidate, name)) {
				return true;
			}
		}
		// not found by its name: ask the search of the wiki for a picture with this name
		String found = search(name.trim());
		return found != null && save(target, found, name);
	}

	/** Downloads "<base>.png" or "<base>.gif" from the wiki and saves it (small, as png). */
	private static boolean save(Path target, String base, String name) throws Exception {
		String fileBase = base.replace(' ', '_');
		for (String extension : new String[]{".png", ".gif"}) {
			byte[] data = download(fileBase + extension);
			if (data == null) {
				continue;
			}
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(data)); // a GIF gives its first picture
			if (image == null) {
				continue;
			}
			BufferedImage small = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.drawImage(image.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH), 0, 0, null);
			g.dispose();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(small, "png", out);
			Files.createDirectories(DIR);
			Files.write(target, out.toByteArray());
			Debug.log("nucleus", () -> "wiki picture saved: " + name + " (" + fileBase + extension + ")");
			return true;
		}
		return false;
	}

	/** The file name (without ".png") of a picture of the wiki that is called like the item, or null. */
	private static String search(String name) throws Exception {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
		}
		URI uri = new URI("https", HOST, "/api.php", "action=query&list=search&srnamespace=6&format=json&srlimit=5&srsearch="
				+ java.net.URLEncoder.encode(name, StandardCharsets.UTF_8), null);
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri)
						.header("User-Agent", "ParzMod (Minecraft client mod, item pictures)")
						.timeout(Duration.ofSeconds(20)).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			return null;
		}
		var hits = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject()
				.getAsJsonObject("query").getAsJsonArray("search");
		String wanted = key(name);
		for (var hit : hits) {
			String title = hit.getAsJsonObject().get("title").getAsString(); // "File:Name.png"
			String bare = title.substring(title.indexOf(':') + 1);
			int dot = bare.lastIndexOf('.');
			String withoutExtension = dot > 0 ? bare.substring(0, dot) : bare;
			if (key(withoutExtension).equals(wanted)) {
				return withoutExtension;
			}
		}
		return null;
	}

	private static byte[] download(String fileName) throws Exception {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
		}
		URI uri = new URI("https", HOST, "/images/" + fileName, null);
		HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri)
						.header("User-Agent", "ParzMod (Minecraft client mod, item pictures)")
						.timeout(Duration.ofSeconds(20)).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		return response.statusCode() == 200 ? response.body() : null;
	}

	/**
	 * The texture of the item picture, or null if it is not there (yet): it is then downloaded and the next
	 * frames show it. Call this on the render thread.
	 */
	public static Identifier textureFor(String name) {
		String key = key(name);
		Identifier known = TEXTURES.get(key);
		if (known != null) {
			return known;
		}
		Status status = STATUS.get(key);
		if (status == null) {
			request(name);
			return null;
		}
		if (status != Status.READY) {
			return null;
		}
		try (InputStream in = Files.newInputStream(file(name))) {
			NativeImage image = NativeImage.read(in);
			Identifier id = Identifier.fromNamespaceAndPath("parz", "icon/" + key.replaceAll("[^a-z0-9]+", "_"));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "parz item icon", image));
			TEXTURES.put(key, id);
			return id;
		} catch (Exception e) {
			STATUS.put(key, Status.MISSING);
			Debug.error("item picture " + name, e);
			return null;
		}
	}

	public static int size() {
		return SIZE;
	}
}
