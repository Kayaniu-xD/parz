package net.parz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.parz.badge.ModUsers;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	/** The lines with a badge (they are drawn in the middle of their cell, see parz$center). */
	private static final Set<Component> BADGED = Collections.newSetFromMap(new WeakHashMap<>());

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	public abstract Component getNameForDisplay(PlayerInfo info);

	@Shadow
	private List<PlayerInfo> getPlayerInfos() {
		throw new AssertionError();
	}

	/** Width of the widest name of the list = the width every cell reserves for the name. */
	@Unique
	private int parz$widestName = 0;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"))
	private void parz$measure(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		try {
			int widest = 0;
			for (PlayerInfo info : getPlayerInfos()) {
				widest = Math.max(widest, minecraft.font.width(getNameForDisplay(info)));
			}
			parz$widestName = widest;
		} catch (RuntimeException e) {
			parz$widestName = 0;
		}
	}

	/** A line with a badge is drawn in the middle of the space of its cell instead of at the left edge. */
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void parz$center(GuiGraphicsExtractor graphics, Font font, Component name, int x, int y, int color, Operation<Void> original) {
		int shift = 0;
		if (BADGED.contains(name)) {
			shift = Math.max(0, (parz$widestName - font.width(name)) / 2);
		}
		original.call(graphics, font, name, x + shift, y, color);
	}

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void parz$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		try {
			Component shown = cir.getReturnValue();
			Component displayName = info.getTabListDisplayName();
			int version = ModUsers.version();
			Cached cached = CACHE.get(info);
			if (cached != null && cached.displayName() == displayName && cached.version() == version) {
				if (cached.result() != null) {
					BADGED.add(cached.result());
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
				BADGED.add(result);
				cir.setReturnValue(result);
			}
		} catch (RuntimeException ignored) {
			// the name stays as it is
		}
	}
}
