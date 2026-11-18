package net.parz.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.parz.ParzClient;
import net.parz.config.ParzConfigDefinition;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * /pz update: loads the newest release from GitHub and exchanges the mod jar in the mods folder.
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

	/** Minecraft version of jars without "-mc<version>" in their name (only releases up to 1.0.13 have those). */
	private static final String PLAIN_ASSET_MINECRAFT = "26.2";

	public enum Status {
		IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR
	}

	private static volatile Status status = Status.IDLE;
	private static volatile String message = "";
	private static volatile Release latest = null;
	private static final AtomicBoolean busy = new AtomicBoolean(false);
	private static String announcedVersion = "";
	private static int ticksInWorld = 0;

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

	// ---- state for the menu ------------------------------------------------------------

	public static Status status() {
		return status;
	}

	/** Version of the newest GitHub release (empty until checked). */
	public static String latestVersion() {
		Release release = latest;
		return release != null ? release.version() : "";
	}

	/** Error text when the status is ERROR. */
	public static String message() {
		return message;
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(ParzClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	// ---- entry points ------------------------------------------------------------------

	/** Checks for a new version when the game starts (if enabled in /pz > Update). */
	public static void checkOnStartup() {
		cleanupLeftovers();
		if (ParzConfigDefinition.UPDATE_CHECK.isEnabled()) {
			check();
		}
	}

	/** Looks for a new version in the background (no download). */
	public static void check() {
		if (!busy.compareAndSet(false, true)) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				doCheck();
			} finally {
				busy.set(false);
			}
		});
	}

	/** Downloads the newest version (checks first if needed). Feedback goes to the given chat consumer. */
	public static void install(Consumer<Component> chat) {
		if (!busy.compareAndSet(false, true)) {
			tell(chat, "An update check or download is already running.");
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				if (status != Status.AVAILABLE || latest == null) {
					tell(chat, "Checking GitHub for a new Parz version...");
					doCheck();
				}
				if (status == Status.UP_TO_DATE) {
					tell(chat, "Parz is up to date (version " + currentVersion() + ").");
				} else if (status == Status.ERROR) {
					tell(chat, "Update failed: " + message);
				} else if (status == Status.AVAILABLE) {
					doInstall(latest, chat);
				}
			} finally {
				busy.set(false);
			}
		});
	}

	/** /pz update */
	public static void run(FabricClientCommandSource source) {
		install(component -> source.sendFeedback(component));
	}

	/** Chat output for actions started from the menu. */
	public static void toChat(Component component) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(component);
		}
	}

	/** Shows "new version available" in the chat a few seconds after you joined a world. */
	public static void tick(Minecraft client) {
		if (client.player == null) {
			ticksInWorld = 0;
			return;
		}
		ticksInWorld++;
		Release release = latest;
		if (ticksInWorld < 100 || status != Status.AVAILABLE || release == null
				|| release.version().equals(announcedVersion) || !ParzConfigDefinition.UPDATE_CHAT.isEnabled()) {
			return;
		}
		announcedVersion = release.version();
		Component text = Component.literal("[Parz] New version " + release.version() + " is available (you have "
						+ currentVersion() + "). ")
				.append(Component.literal("[Click to update]").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("pz update"))));
		client.player.sendSystemMessage(text);
	}

	// ---- check / install ---------------------------------------------------------------

	private static void doCheck() {
		status = Status.CHECKING;
		try {
			Release release = latestRelease();
			latest = release;
			status = compareVersions(release.version(), currentVersion()) > 0 ? Status.AVAILABLE : Status.UP_TO_DATE;
		} catch (UpdateException e) {
			message = e.getMessage();
			status = Status.ERROR;
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Update check failed", e);
			message = "Could not reach GitHub";
			status = Status.ERROR;
		}
	}

	private static void doInstall(Release release, Consumer<Component> chat) {
		status = Status.DOWNLOADING;
		try {
			ModContainer container = FabricLoader.getInstance().getModContainer(ParzClient.MOD_ID)
					.orElseThrow(() -> new UpdateException("Parz is not loaded"));
			List<Path> paths = container.getOrigin().getPaths();
			if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst()) || !paths.getFirst().toString().endsWith(".jar")) {
				throw new UpdateException("Parz does not run from a jar (development environment)");
			}
			Path oldJar = paths.getFirst();
			tell(chat, "Downloading Parz " + release.version() + " (you have " + currentVersion() + ")...");

			Path modsDir = oldJar.getParent();
			Path partFile = modsDir.resolve(release.assetName() + ".part");
			Path newFile = modsDir.resolve(release.assetName() + ".new");
			Path finalJar = modsDir.resolve(release.assetName());
			download(release, partFile);
			verifyJar(partFile);
			Files.move(partFile, newFile, StandardCopyOption.REPLACE_EXISTING);

			scheduleSwap(newFile, oldJar, finalJar);
			status = Status.READY;
			tell(chat, "Parz " + release.version() + " is downloaded. It replaces the old mod when you close Minecraft - restart the game to use it.");
		} catch (UpdateException e) {
			message = e.getMessage();
			status = Status.ERROR;
			tell(chat, "Update failed: " + e.getMessage());
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Update failed", e);
			message = e.getClass().getSimpleName() + " " + e.getMessage();
			status = Status.ERROR;
			tell(chat, "Update failed: " + message);
		}
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
		// A release holds one jar per Minecraft version: parz-1.0.14-mc26.2.jar, parz-1.0.14-mc26.3.jar.
		// Pick the one for the Minecraft that is running (old releases have a plain parz-x.jar = 26.2).
		JsonObject matching = pickAsset(assets, runningMinecraft());
		if (matching == null) {
			throw new UpdateException("Release " + tag + " has no jar for Minecraft " + runningMinecraft());
		}
		return new Release(tag, tag.startsWith("v") ? tag.substring(1) : tag, matching.get("name").getAsString(),
				matching.get("browser_download_url").getAsString());
	}

	/** The jar of a release for this Minecraft version, or null. */
	static JsonObject pickAsset(JsonArray assets, String minecraft) {
		JsonObject plain = null;
		JsonObject matching = null;
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			String name = asset.get("name").getAsString();
			if (!name.endsWith(".jar") || name.endsWith("-sources.jar") || name.endsWith("-dev.jar")) {
				continue;
			}
			if (name.endsWith("-mc" + minecraft + ".jar")) {
				matching = asset;
			} else if (!name.contains("-mc")) {
				plain = asset;
			}
		}
		if (matching == null && plain != null && minecraft.equals(PLAIN_ASSET_MINECRAFT)) {
			matching = plain;
		}
		return matching;
	}

	/** The Minecraft version that is running, e.g. "26.2". */
	private static String runningMinecraft() {
		try {
			return FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString())
					.orElse(PLAIN_ASSET_MINECRAFT);
		} catch (Throwable t) {
			return PLAIN_ASSET_MINECRAFT;
		}
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

	/**
	 * Windows keeps the running jar locked, so a small helper program (SwapHelper, a class of this mod) waits until
	 * Minecraft is gone and then swaps the files. It runs with javaw.exe (no console window). The class is copied to
	 * a temp folder first: it must not run from the mod jar, that would lock the jar that has to be deleted.
	 * (Minecraft's bundled Java has no compiler, so a source file cannot be started with it.)
	 */
	private static void swapWithHelperScript() throws Exception {
		Path dir = Files.createTempDirectory("parz-swap");
		Path cls = dir.resolve("net").resolve("parz").resolve("update").resolve("SwapHelper.class");
		Files.createDirectories(cls.getParent());
		try (InputStream in = ModUpdater.class.getResourceAsStream("SwapHelper.class")) {
			if (in == null) {
				throw new UpdateException("SwapHelper is missing in the mod jar");
			}
			Files.copy(in, cls, StandardCopyOption.REPLACE_EXISTING);
		}

		Path javaHome = Path.of(System.getProperty("java.home"));
		Path javaw = javaHome.resolve("bin").resolve("javaw.exe");
		String executable = Files.exists(javaw) ? javaw.toString() : javaHome.resolve("bin").resolve("java.exe").toString();
		new ProcessBuilder(executable, "-cp", dir.toString(), "net.parz.update.SwapHelper", pendingOld.toString(),
				pendingNew.toString(), pendingFinal.toString(), dir.toString())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
	}

	/**
	 * Called when the game starts: removes leftovers of earlier updates in the mods folder (half downloaded
	 * ".part" files, ".new" files that are not newer than this version, older Parz jars). A newer ".new" file whose
	 * swap did not happen is scheduled again, so it replaces the mod when you close the game.
	 */
	public static void cleanupLeftovers() {
		try {
			Path running = FabricLoader.getInstance().getModContainer(ParzClient.MOD_ID)
					.map(c -> c.getOrigin().getPaths()).filter(p -> p.size() == 1).map(List::getFirst).orElse(null);
			if (running == null || !Files.isRegularFile(running) || !running.toString().endsWith(".jar")) {
				return;
			}
			Path modsDir = running.getParent();
			String current = currentVersion();
			String minecraft = runningMinecraft();
			Pattern name = Pattern.compile("^parz-(\\d+(?:\\.\\d+)*)(?:-mc([\\d.]+))?\\.jar(\\.new|\\.part)?$");
			try (var files = Files.list(modsDir)) {
				for (Path file : files.toList()) {
					Matcher m = name.matcher(file.getFileName().toString());
					if (!m.matches() || file.equals(running)) {
						continue;
					}
					String version = m.group(1);
					String fileMinecraft = m.group(2) != null ? m.group(2) : PLAIN_ASSET_MINECRAFT;
					String suffix = m.group(3);
					int newer = compareVersions(version, current);
					if (".part".equals(suffix)) {
						Files.deleteIfExists(file);
					} else if (".new".equals(suffix)) {
						if (newer > 0 && fileMinecraft.equals(minecraft) && isValidJar(file)) {
							String jarName = file.getFileName().toString();
							scheduleSwap(file, running, modsDir.resolve(jarName.substring(0, jarName.length() - ".new".length())));
							ParzClient.LOGGER.info("Parz {} is waiting to be installed when the game closes", version);
						} else {
							Files.deleteIfExists(file);
						}
					} else if (newer < 0 && fileMinecraft.equals(minecraft)) {
						Files.deleteIfExists(file); // an old Parz jar that was not removed
					}
				}
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not clean up old Parz files", e);
		}
	}

	private static boolean isValidJar(Path file) {
		try {
			verifyJar(file);
			return true;
		} catch (Exception e) {
			return false;
		}
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

	private static void tell(Consumer<Component> chat, String text) {
		// green, only failures are red
		ChatFormatting color = text.startsWith("Update failed") ? ChatFormatting.RED : ChatFormatting.GREEN;
		Minecraft.getInstance().execute(() -> chat.accept(Component.literal("[Parz] " + text).withStyle(color)));
	}
}
