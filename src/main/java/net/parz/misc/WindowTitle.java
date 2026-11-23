package net.parz.misc;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.parz.ParzClient;
import net.parz.config.ParzConfigDefinition;

import java.lang.reflect.Method;

/**
 * Keeps the title of the game window the way Minecraft itself makes it, also when another mod or client renames the
 * window (for example to "Nebula Client 2.5.10"). Two settings: the vanilla title ("Minecraft 26.2 - Multiplayer ...")
 * and the title of a modded game ("Minecraft* 26.2 - Multiplayer ...", the star is what Minecraft adds when mods like
 * Fabric are installed). With both off nothing is touched.
 *
 * <p>The title is built here, in the same way as Minecraft does it, and not taken from Minecraft's own method: a client
 * can replace that method, and then the "right" title would be its title. It is put on the window several times per
 * second, whoever changed it, and also whenever something sets a title through the Window of Minecraft.
 */
public final class WindowTitle {
	/** The title is put again every this many ticks (a tick is 1/20 s). */
	private static final int PERIOD = 2;
	private static int ticks = 0;
	private static Method rawSetter = null;
	private static boolean rawChecked = false;
	private static String lastLogged = "";

	private WindowTitle() {
	}

	public static boolean vanilla() {
		return ParzConfigDefinition.TITLE_VANILLA.isEnabled();
	}

	public static boolean active() {
		return vanilla() || ParzConfigDefinition.TITLE_FABRIC.isEnabled();
	}

	/** The title of the game as vanilla Minecraft makes it: "Minecraft", a star for a modded game, the version and where you are. */
	public static String build(Minecraft client) {
		StringBuilder title = new StringBuilder("Minecraft");
		if (!vanilla()) {
			title.append('*');
		}
		title.append(' ').append(SharedConstants.getCurrentVersion().name());
		if (client.getConnection() != null && client.getConnection().getConnection().isConnected()) {
			title.append(" - ");
			IntegratedServer local = client.getSingleplayerServer();
			ServerData server = client.getCurrentServer();
			if (local != null && !local.isPublished()) {
				title.append(I18n.get("title.singleplayer"));
			} else if (server != null && server.isRealm()) {
				title.append(I18n.get("title.multiplayer.realms"));
			} else if (local != null || (server != null && server.isLan())) {
				title.append(I18n.get("title.multiplayer.lan"));
			} else {
				title.append(I18n.get("title.multiplayer.other"));
			}
		}
		return title.toString();
	}

	/** The title Minecraft is about to use (or any other mod sets): replaced by the right one while a setting is on. */
	public static String adjust(String title) {
		if (!active()) {
			return title;
		}
		Minecraft client = Minecraft.getInstance();
		return client == null ? title : build(client);
	}

	/** Several times per second the right title is put on the window again, whoever changed it. */
	public static void tick(Minecraft client) {
		if (!active()) {
			return;
		}
		if (++ticks % PERIOD != 0) {
			return;
		}
		if (client.getWindow() == null) {
			return;
		}
		String title = build(client);
		client.getWindow().setTitle(title);
		setRaw(client.getWindow().handle(), title);
		if (ticks % 200 == 0 && !title.equals(lastLogged)) {
			lastLogged = title;
			ParzClient.LOGGER.info("[window title] forcing \"{}\"", title);
		}
	}

	/**
	 * The same, straight on the window library (GLFW in Minecraft 26.2, SDL in 26.3; looked up by name, so one source builds
	 * for both): a client that replaces the setTitle method of the Minecraft Window would otherwise win.
	 */
	private static void setRaw(long handle, String title) {
		if (!rawChecked) {
			rawChecked = true;
			String[][] candidates = {{"org.lwjgl.glfw.GLFW", "glfwSetWindowTitle"}, {"org.lwjgl.sdl.SDLVideo", "SDL_SetWindowTitle"}};
			for (String[] candidate : candidates) {
				try {
					rawSetter = Class.forName(candidate[0]).getMethod(candidate[1], long.class, CharSequence.class);
					break;
				} catch (ReflectiveOperationException | LinkageError ignored) {
					// not this library
				}
			}
		}
		if (rawSetter != null) {
			try {
				rawSetter.invoke(null, handle, title);
			} catch (ReflectiveOperationException | RuntimeException e) {
				rawSetter = null;
			}
		}
	}
}
