package net.parz.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.parz.badge.ModUsers;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The badge in the tab list. The entries of the SkyBlock tab list are not real players (their profile names are
 * made up, the text "[550] Pay2Loze" is only the display name), so the name is looked for in the text. The result
 * is remembered per entry, because the tab list asks for every name in every frame.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {
	/** What was made of the display name of an entry (result null = the name stays as it is). */
	private record Cached(Component displayName, int version, Component result) {
	}

	private static final Map<PlayerInfo, Cached> CACHE = new WeakHashMap<>();

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void parz$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		try {
			Component shown = cir.getReturnValue();
			Component displayName = info.getTabListDisplayName();
			int version = ModUsers.version();
			Cached cached = CACHE.get(info);
			if (cached != null && cached.displayName() == displayName && cached.version() == version) {
				if (cached.result() != null) {
					cir.setReturnValue(cached.result());
				}
				return;
			}
			// a real profile name first, then the text of the line
			Component changed = PlayerBadge.apply(info.getProfile().name(), shown, true);
			if (changed == shown) {
				changed = PlayerBadge.applyInText(shown);
			}
			Component result = changed == shown ? null : changed;
			CACHE.put(info, new Cached(displayName, version, result));
			if (result != null) {
				cir.setReturnValue(result);
			}
		} catch (RuntimeException ignored) {
			// the name stays as it is
		}
	}
}
