package net.parz.armor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.parz.Debug;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cosmetic skins of SkyBlock (helmet skins, pet skins, ...), which the Hypixel items API does not list, with the
 * pictures of the animated ones. They come from the public SkyBlock item repository of NotEnoughUpdates (MIT license),
 * kept in the resource parz/skins.json.
 *
 * <p>A choice is saved and shared as ONE texture hash, also for an animated skin: {@link #now(String)} turns that
 * hash into the picture of this moment.
 */
public final class SkinList {
	/** One tick of the game. */
	private static final long TICK_MS = 50;

	/** The pictures of an animated skin; {@code ends} is when each one is over, counted in ticks from the start. */
	private record Animation(String[] frames, int[] ends) {
		int frameAt(long tick) {
			int at = (int) (tick % ends[ends.length - 1]);
			int frame = 0;
			while (at >= ends[frame]) {
				frame++;
			}
			return frame;
		}
	}

	private static volatile List<HeadCatalog.Head> skins = null;
	private static volatile Map<String, Animation> animations = Map.of();
	/** Pictures that were asked for, and those the game has loaded. */
	private static final Set<String> asked = ConcurrentHashMap.newKeySet();
	private static final Set<String> ready = ConcurrentHashMap.newKeySet();

	private SkinList() {
	}

	/** All skins of the list. */
	public static List<HeadCatalog.Head> skins() {
		load();
		return skins;
	}

	public static boolean isAnimated(String hash) {
		load();
		return hash != null && animations.containsKey(hash);
	}

	/**
	 * The texture to draw for this hash right now: the hash itself, or the current picture of an animated skin. A
	 * picture the game has not loaded yet is not shown (it would look like a default head for a moment); the one
	 * before it stays instead.
	 */
	public static String now(String hash) {
		load();
		Animation animation = hash == null ? null : animations.get(hash);
		if (animation == null) {
			return hash;
		}
		if (asked.add(hash)) {
			for (String frame : animation.frames()) {
				request(frame);
			}
		}
		int frame = animation.frameAt(System.currentTimeMillis() / TICK_MS);
		for (int back = 0; back < animation.frames().length; back++) {
			String picture = animation.frames()[Math.floorMod(frame - back, animation.frames().length)];
			if (ready.contains(picture)) {
				return picture;
			}
		}
		return hash;
	}

	private static void request(String hash) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.playerSkinRenderCache().lookup(ArmorLook.stillProfileFor(hash))
				.thenAccept(info -> ready.add(hash)));
	}

	private static void load() {
		if (skins != null) {
			return;
		}
		synchronized (SkinList.class) {
			if (skins != null) {
				return;
			}
			List<HeadCatalog.Head> list = new ArrayList<>();
			Map<String, Animation> moving = new HashMap<>();
			try (InputStream in = SkinList.class.getResourceAsStream("/parz/skins.json")) {
				if (in != null) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement element : json.getAsJsonArray("skins")) {
						JsonArray row = element.getAsJsonArray(); // id, name, rarity, texture
						String hash = row.get(3).getAsString();
						if (ArmorLook.validHash(hash)) {
							list.add(new HeadCatalog.Head(row.get(0).getAsString(), row.get(1).getAsString(), hash, row.get(2).getAsString()));
						}
					}
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("animations").entrySet()) {
						JsonArray row = entry.getValue().getAsJsonArray(); // ticks (one for all, or one each), textures
						JsonArray pictures = row.get(1).getAsJsonArray();
						String[] frames = new String[pictures.size()];
						int[] ends = new int[frames.length];
						int end = 0;
						for (int i = 0; i < frames.length; i++) {
							frames[i] = pictures.get(i).getAsString();
							end += Math.max(1, row.get(0).isJsonArray() ? row.get(0).getAsJsonArray().get(i).getAsInt() : row.get(0).getAsInt());
							ends[i] = end;
						}
						if (frames.length > 0) {
							moving.put(entry.getKey(), new Animation(frames, ends));
						}
					}
				}
			} catch (Exception e) {
				Debug.error("skin list", e);
			}
			animations = moving;
			skins = List.copyOf(list);
		}
	}
}
