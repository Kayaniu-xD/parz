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

/**
 * A picture (a character with a transparent background) that stands left of the book of every Parz menu. It has
 * the same height as the book. The picture is read from config/parz-mascot.png, or from assets/parz/mascot.png
 * inside the mod; without a picture nothing is drawn.
 */
public final class Mascot {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("parz", "mascot");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("parz-mascot.png");

	private static boolean loaded = false;
	private static boolean available = false;
	private static int imageW = 1;
	private static int imageH = 1;

	private Mascot() {
	}

	private static void load() {
		loaded = true;
		try {
			NativeImage image = null;
			if (Files.isRegularFile(FILE)) {
				try (InputStream in = Files.newInputStream(FILE)) {
					image = NativeImage.read(in);
				}
			} else {
				try (InputStream in = Mascot.class.getResourceAsStream("/assets/parz/mascot.png")) {
					if (in != null) {
						image = NativeImage.read(in);
					}
				}
			}
			if (image == null) {
				return;
			}
			imageW = image.getWidth();
			imageH = image.getHeight();
			Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "parz mascot", image));
			available = true;
		} catch (Exception e) {
			ParzClient.LOGGER.warn("Could not load the Parz menu picture", e);
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
		if (!available || px <= 4) {
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f, w, h, imageW, imageH, imageW, imageH);
	}
}
