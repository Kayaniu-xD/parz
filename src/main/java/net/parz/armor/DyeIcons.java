package net.parz.armor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.parz.Debug;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * How the dye items of SkyBlock look: every dye is a player head with its own texture and a name in its rarity color. The data is
 * the one of the public SkyBlock item repository (resource parz/dye_icons.json), by the name of the dye ("Aurora Dye").
 */
public final class DyeIcons {
	private record Icon(String hash, ChatFormatting color) {
	}

	private static volatile Map<String, Icon> icons = null;

	private DyeIcons() {
	}

	private static Map<String, Icon> icons() {
		Map<String, Icon> map = icons;
		if (map == null) {
			synchronized (DyeIcons.class) {
				if (icons == null) {
					icons = load();
				}
				map = icons;
			}
		}
		return map;
	}

	private static Map<String, Icon> load() {
		Map<String, Icon> map = new HashMap<>();
		try (InputStream in = DyeIcons.class.getResourceAsStream("/parz/dye_icons.json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String name : json.keySet()) {
					JsonObject entry = json.getAsJsonObject(name);
					ChatFormatting color = ChatFormatting.getByCode(entry.get("c").getAsString().charAt(0));
					map.put(name, new Icon(entry.get("h").getAsString(), color == null ? ChatFormatting.WHITE : color));
				}
			}
		} catch (Exception e) {
			Debug.error("dye icons", e);
		}
		return map;
	}

	/** The texture of the head of this dye, or null. */
	public static String hash(String dyeName) {
		Icon icon = icons().get(dyeName);
		return icon == null ? null : icon.hash();
	}

	/** The color of the name of this dye (its rarity color). */
	public static ChatFormatting color(String dyeName) {
		Icon icon = icons().get(dyeName);
		return icon == null ? ChatFormatting.WHITE : icon.color();
	}
}
