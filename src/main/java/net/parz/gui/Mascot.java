package net.parz.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.parz.ParzClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A picture or a short animation (a character) that stands left of the book of every Parz menu. It has the same
 * height as the book. A green background (green screen) is removed when the picture is loaded.
 * <p>
 * Where the picture comes from, first one that exists:
 * - config/parz-mascot/ : a folder with the frames of an animation as PNG files (played in the order of their
 *   names, at the speed in an optional text file fps.txt, 12 frames per second if there is none)
 * - config/parz-mascot.png : one picture
 * Without any of them nothing is drawn.
 */
public final class Mascot {
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve("parz-mascot");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-mascot.png");
	private static final int DEFAULT_FPS = 12;

	private static boolean loaded = false;
	private static final List<Identifier> FRAMES = new ArrayList<>();
	private static int imageW = 1;
	private static int imageH = 1;
	private static double fps = DEFAULT_FPS;

	private Mascot() {
	}

	private static void load() {
		loaded = true;
		try {
			List<Path> files = new ArrayList<>();
			if (Files.isDirectory(FOLDER)) {
				try (Stream<Path> list = Files.list(FOLDER)) {
					list.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().forEach(files::add);
				}
				Path fpsFile = FOLDER.resolve("fps.txt");
				if (Files.isRegularFile(fpsFile)) {
					try {
						fps = Math.max(1, Math.min(60, Double.parseDouble(Files.readString(fpsFile).trim())));
					} catch (NumberFormatException ignored) {
						// keep the default speed
					}
				}
			}
			if (files.isEmpty() && Files.isRegularFile(FILE)) {
				files.add(FILE);
			}
			for (Path file : files) {
				try (InputStream in = Files.newInputStream(file)) {
					NativeImage image = NativeImage.read(in);
					removeGreen(image);
					imageW = image.getWidth();
					imageH = image.getHeight();
					Identifier id = Identifier.fromNamespaceAndPath("parz", "mascot/" + FRAMES.size());
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "parz mascot", image));
					FRAMES.add(id);
				}
			}
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not load the Parz menu picture", e);
		}
	}

	private static boolean isGreen(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return g > 90 && g > r * 1.35 && g > b * 1.35;
	}

	/**
	 * Makes a green background transparent. It only happens if the picture has a green screen (its corner is
	 * green and not transparent); a picture that is already transparent is left as it is. Pixels at the edge
	 * of the character lose their green tint.
	 */
	private static void removeGreen(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int corner = image.getPixel(0, 0);
		if ((corner >>> 24) < 255 || !isGreen(corner)) {
			return;
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = image.getPixel(x, y);
				if ((p >>> 24) == 0) {
					continue;
				}
				int r = (p >> 16) & 0xFF;
				int g = (p >> 8) & 0xFF;
				int b = p & 0xFF;
				if (isGreen(p)) {
					// how green it is decides how transparent: pure green gone, a slight tint stays mostly
					double excess = g - Math.max(r, b);
					double alpha = 1.0 - Math.min(1.0, excess / 70.0);
					if (alpha < 0.12) {
						image.setPixel(x, y, 0);
						continue;
					}
					g = Math.max(r, b); // no green fringe on the edge
					image.setPixel(x, y, ((int) Math.round(alpha * 255) << 24) | (r << 16) | (g << 8) | b);
				} else if (g > Math.max(r, b) + 12) {
					// a faint green tint: take it away, keep the pixel
					image.setPixel(x, y, (p & 0xFF000000) | (r << 16) | (Math.max(r, b) + 6 << 8) | b);
				}
			}
		}
	}

	/**
	 * Draws the picture left of the book (the book is at px, py and ph high). The picture is as high as the
	 * book; if the screen is too narrow for that it gets smaller, so it never leaves the screen.
	 */
	public static void draw(GuiGraphicsExtractor graphics, int px, int py, int ph) {
		if (!loaded) {
			load();
		}
		if (FRAMES.isEmpty() || px <= 4) {
			return;
		}
		int h = ph;
		int w = Math.max(1, Math.round(h * imageW / (float) imageH));
		if (w > px) {
			w = px;
			h = Math.max(1, Math.round(w * imageH / (float) imageW));
		}
		int x = px - w;
		int y = py + ph - h; // standing on the bottom edge of the book
		int frame = (int) ((System.currentTimeMillis() / 1000.0 * fps) % FRAMES.size());
		graphics.blit(RenderPipelines.GUI_TEXTURED, FRAMES.get(frame), x, y, 0f, 0f, w, h, imageW, imageH, imageW, imageH);
	}
}
