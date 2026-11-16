package net.parz.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.parz.ParzClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

/**
 * /parz update: loads the newest release from GitHub and exchanges the mod jar in the mods folder.
 *
 * The running jar is locked on Windows, so the new version is downloaded as "parz-x.y.z.jar.new"
 * (Fabric ignores it) and swapped in when Minecraft closes. If anything fails the old jar stays.
 *
 * The repository can be private: if the public GitHub API does not answer, the GitHub CLI (gh) that
 * is logged in on this PC is used instead.
 */
public final class ModUpdater {
	private static final String REPO = "Kayaniu-xD/parz";
	private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

	private static volatile boolean running = false;
	private static Path pendingNew = null;
	private static Path pendingOld = null;
	private static Path pendingFinal = null;
	private static boolean hookRegistered = false;

	private record Release(String tag, String version, String assetName, String assetUrl) {
	}

	private static final class UpdateException extends Exception {
		UpdateException(String message) {
			super(message);
		}
	}

	private ModUpdater() {
	}

	public static void run(FabricClientCommandSource source) {
		if (running) {
			say(source, "An update is already running.");
			return;
		}
		running = true;
		say(source, "Checking GitHub for a new Parz version...");
		CompletableFuture.runAsync(() -> {
			try {
				update(source);
			} catch (UpdateException e) {
				say(source, "Update failed: " + e.getMessage());
			} catch (Exception e) {
				ParzClient.LOGGER.warn("Update failed", e);
				say(source, "Update failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
			} finally {
				running = false;
			}
		});
	}

	private static void update(FabricClientCommandSource source) throws Exception {
		ModContainer container = FabricLoader.getInstance().getModContainer(ParzClient.MOD_ID)
				.orElseThrow(() -> new UpdateException("Parz is not loaded"));
		String current = container.getMetadata().getVersion().getFriendlyString();
		List<Path> paths = container.getOrigin().getPaths();
		if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst()) || !paths.getFirst().toString().endsWith(".jar")) {
			throw new UpdateException("Parz does not run from a jar (development environment)");
		}
		Path oldJar = paths.getFirst();

		Release release = latestRelease();
		if (compareVersions(release.version(), current) <= 0) {
			say(source, "Parz is up to date (version " + current + ").");
			return;
		}
		say(source, "Downloading Parz " + release.version() + " (you have " + current + ")...");

		Path modsDir = oldJar.getParent();
		Path partFile = modsDir.resolve(release.assetName() + ".part");
		Path newFile = modsDir.resolve(release.assetName() + ".new");
		Path finalJar = modsDir.resolve(release.assetName());
		download(release, partFile);
		verifyJar(partFile);
		Files.move(partFile, newFile, StandardCopyOption.REPLACE_EXISTING);

		scheduleSwap(newFile, oldJar, finalJar);
		say(source, "Parz " + release.version() + " is downloaded. It replaces the old mod when you close Minecraft - restart the game to use it.");
	}

	// ---- GitHub ------------------------------------------------------------------------

	private static Release latestRelease() throws Exception {
		String json = null;
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		try {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(LATEST_URL))
					.timeout(Duration.ofSeconds(15))
					.header("Accept", "application/vnd.github+json")
					.header("User-Agent", "Parz-Mod")
					.GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				json = response.body();
			}
		} catch (Exception e) {
			ParzClient.LOGGER.debug("Public GitHub API not reachable", e);
		}
		if (json == null) {
			// private repository: use the GitHub CLI that is logged in on this PC
			json = runGh("api", "repos/" + REPO + "/releases/latest");
		}

		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		String tag = root.get("tag_name").getAsString();
		JsonArray assets = root.has("assets") ? root.getAsJsonArray("assets") : new JsonArray();
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			String name = asset.get("name").getAsString();
			if (name.endsWith(".jar") && !name.endsWith("-sources.jar") && !name.endsWith("-dev.jar")) {
				return new Release(tag, tag.startsWith("v") ? tag.substring(1) : tag, name,
						asset.get("browser_download_url").getAsString());
			}
		}
		throw new UpdateException("Release " + tag + " has no mod jar");
	}

	private static void download(Release release, Path target) throws Exception {
		try {
			HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(release.assetUrl()))
					.timeout(Duration.ofMinutes(2))
					.header("User-Agent", "Parz-Mod")
					.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() == 200) {
				try (InputStream in = response.body()) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			}
		} catch (Exception e) {
			ParzClient.LOGGER.debug("Public download failed", e);
		}
		// private repository: let the GitHub CLI download it
		Path tempDir = Files.createTempDirectory("parz-update");
		try {
			runGh("release", "download", release.tag(), "--repo", REPO, "--pattern", release.assetName(),
					"--dir", tempDir.toString(), "--clobber");
			Files.move(tempDir.resolve(release.assetName()), target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			try (var files = Files.list(tempDir)) {
				for (Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
			}
			Files.deleteIfExists(tempDir);
		}
	}

	/** The GitHub CLI (standard install location or PATH). */
	private static String runGh(String... args) throws Exception {
		List<String> candidates = new ArrayList<>();
		String programFiles = System.getenv("ProgramFiles");
		if (programFiles != null) {
			candidates.add(programFiles + "\\GitHub CLI\\gh.exe");
		}
		candidates.add("gh");
		Exception last = null;
		for (String exe : candidates) {
			try {
				List<String> command = new ArrayList<>();
				command.add(exe);
				command.addAll(List.of(args));
				Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
				String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
				if (process.waitFor() == 0) {
					return out;
				}
				last = new UpdateException("GitHub CLI: " + err.trim());
			} catch (java.io.IOException e) {
				last = e; // this candidate does not exist
			}
		}
		throw new UpdateException("GitHub is not reachable. The repository is private: log in with the GitHub CLI (gh auth login) or make the repository public."
				+ (last != null && last.getMessage() != null ? " (" + last.getMessage() + ")" : ""));
	}

	private static void verifyJar(Path file) throws Exception {
		if (Files.size(file) < 1024) {
			throw new UpdateException("The downloaded file is too small");
		}
		try (ZipFile zip = new ZipFile(file.toFile())) {
			if (zip.getEntry("fabric.mod.json") == null) {
				throw new UpdateException("The downloaded file is not a Fabric mod");
			}
		}
	}

	// ---- swapping the jar when the game closes -----------------------------------------

	private static synchronized void scheduleSwap(Path newFile, Path oldJar, Path finalJar) {
		pendingNew = newFile;
		pendingOld = oldJar;
		pendingFinal = finalJar;
		if (hookRegistered) {
			return;
		}
		hookRegistered = true;
		Runtime.getRuntime().addShutdownHook(new Thread(ModUpdater::swapNow, "parz-update"));
	}

	private static void swapNow() {
		try {
			if (pendingNew == null || !Files.exists(pendingNew)) {
				return;
			}
			if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
				swapWithHelperScript();
			} else {
				Files.deleteIfExists(pendingOld);
				Files.move(pendingNew, pendingFinal, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not install the Parz update", e);
		}
	}

	/** Windows keeps the running jar locked: a small script waits until Minecraft is gone. */
	private static void swapWithHelperScript() throws Exception {
		Path script = Files.createTempFile("parz-update", ".bat");
		String content = String.join("\r\n",
				"@echo off",
				"set tries=0",
				":wait",
				"ping -n 3 127.0.0.1 >nul",
				"del /f /q \"" + pendingOld + "\" >nul 2>&1",
				"if exist \"" + pendingOld + "\" (",
				"  set /a tries+=1",
				"  if %tries% lss 60 goto wait",
				"  exit /b 1",
				")",
				"move /y \"" + pendingNew + "\" \"" + pendingFinal + "\" >nul",
				"(goto) 2>nul & del \"%~f0\"",
				"");
		Files.writeString(script, content, StandardCharsets.UTF_8);
		new ProcessBuilder("cmd", "/c", "start", "Parz update", "/min", script.toString()).start();
	}

	// ---- helpers -----------------------------------------------------------------------

	/** Compares "1.0.10" with "1.0.9". */
	static int compareVersions(String a, String b) {
		String[] pa = a.split("[.\\-+]");
		String[] pb = b.split("[.\\-+]");
		for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
			int x = i < pa.length ? parse(pa[i]) : 0;
			int y = i < pb.length ? parse(pb[i]) : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		return 0;
	}

	private static int parse(String part) {
		try {
			return Integer.parseInt(part);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void say(FabricClientCommandSource source, String message) {
		Minecraft.getInstance().execute(() -> source.sendFeedback(Component.literal("[Parz] " + message)));
	}
}
