package net.parz.misc;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.parz.config.ParzConfigDefinition;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps the title of the game window the way Minecraft itself makes it, also when another mod or client renames the
 * window (for example to "Nebula Client 2.5.10"). Two settings: the vanilla title ("Minecraft 26.2 - Multiplayer ...")
 * and the title of a modded game ("Minecraft* 26.2 - Multiplayer ...", the star is what Minecraft adds when mods like
 * Fabric are installed). With both off nothing is touched.
 *
 * <p>Two ways to rename a window are covered: through Minecraft's own Window.setTitle (the mixin of this feature changes
 * the text) and by writing to the window directly (the tick below notices the wrong title and puts the right one back).
 */
public final class WindowTitle {
	/** The title Minecraft made last (already without the star in vanilla mode). */
	private static volatile String expected = null;
	private static int ticks = 0;

	private WindowTitle() {
	}

	public static boolean vanilla() {
		return ParzConfigDefinition.TITLE_VANILLA.isEnabled();
	}

	public static boolean active() {
		return vanilla() || ParzConfigDefinition.TITLE_FABRIC.isEnabled();
	}

	/** Called with the title Minecraft just made; returns the title that is really used and remembers it. */
	public static String adjustMade(String title) {
		if (!active() || title == null) {
			return title;
		}
		String result = vanilla() && title.startsWith("Minecraft*") ? "Minecraft" + title.substring("Minecraft*".length()) : title;
		expected = result;
		return result;
	}

	/** Called for every title that is about to be set on the window: another mod's title is replaced. */
	public static String adjustSet(String title) {
		String right = expected;
		if (!active() || right == null || right.equals(title)) {
			return title;
		}
		return right;
	}

	/** Every few ticks: the title of the window must be the right one, whoever set it. */
	public static void tick(Minecraft client) {
		if (!active()) {
			expected = null;
			return;
		}
		if (++ticks % 5 != 0) {
			return;
		}
		Window window = client.getWindow();
		if (window == null) {
			return;
		}
		String current = GLFW.glfwGetWindowTitle(window.handle());
		if (expected == null || !expected.equals(current)) {
			client.updateTitle();
		}
	}
}
