package net.parz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.parz.badge.ModUsers;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The badge in the tab list. Only the DRAWING of the tab list is changed: getNameForDisplay itself still gives the
 * real name, because other mods read it (dungeon maps find you and your team in it; with a changed name they showed
 * you a second time, as a team mate). The entries of the SkyBlock tab list are not real players (their profile names
 * are made up, the text "[550] Pay2Loze" is only the display name), so the name is looked for in the text. The result
 * is remembered per entry, because the tab list asks for every name in every frame.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {
	/** What was made of the display name of an entry (result null = the name stays as it is). */
	private record Cached(Component displayName, int version, Component result) {
	}

	private static final Map<PlayerInfo, Cached> CACHE = new WeakHashMap<>();

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
	private Component parz$badge(PlayerTabOverlay overlay, PlayerInfo info, Operation<Component> original) {
		Component shown = original.call(overlay, info);
		try {
			Component displayName = info.getTabListDisplayName();
			int version = ModUsers.version();
			Cached cached = CACHE.get(info);
			if (cached == null || cached.displayName() != displayName || cached.version() != version) {
				// a real profile name first, then the text of the line
				Component changed = PlayerBadge.apply(info.getProfile().name(), shown, true);
				if (changed == shown) {
					changed = PlayerBadge.applyInText(shown);
				}
				cached = new Cached(displayName, version, changed == shown ? null : changed);
				CACHE.put(info, cached);
			}
			return cached.result() != null ? cached.result() : shown;
		} catch (RuntimeException ignored) {
			return shown; // the name stays as it is
		}
	}
}
