package net.parz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.parz.badge.ModUsers;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
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
	/**
	 * SkyHanni's compact tab list draws a line only as a player line (head + the text 10 pixels further right) when it can read
	 * "[level] name" in it. A line with a badge cannot be read, so it is drawn at the left edge, over the place of the head.
	 * With SkyHanni installed the badge line therefore starts with the 10 pixels the heads take, and the normal tab list
	 * (which has its own head) draws the line without them. Key: the line with the gap, value: the line without.
	 */
	private static final Map<Component, Component> WITHOUT_GAP = Collections.synchronizedMap(new WeakHashMap<>());
	private static Boolean skyHanni = null;

	private static Component withGap(Component line) {
		if (skyHanni == null) {
			skyHanni = FabricLoader.getInstance().isModLoaded("skyhanni");
		}
		if (!skyHanni) {
			return line;
		}
		// two bold spaces: 2 x 5 pixels
		Component gapped = Component.empty().append(Component.literal("  ").withStyle(ChatFormatting.BOLD)).append(line);
		WITHOUT_GAP.put(gapped, line);
		return gapped;
	}

	/** The normal tab list draws the line without the gap (it has the head there). */
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void parz$withoutGap(GuiGraphicsExtractor graphics, Font font, Component name, int x, int y, int color, Operation<Void> original) {
		Component plain = WITHOUT_GAP.get(name);
		original.call(graphics, font, plain != null ? plain : name, x, y, color);
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
					cir.setReturnValue(cached.result());
				}
				return;
			}
			// a real profile name first, then the text of the line
			Component changed = PlayerBadge.apply(info.getProfile().name(), shown, true);
			if (changed == shown) {
				changed = PlayerBadge.applyInText(shown);
			}
			Component result = changed == shown ? null : withGap(changed);
			CACHE.put(info, new Cached(displayName, version, result));
			if (result != null) {
				cir.setReturnValue(result);
			}
		} catch (RuntimeException ignored) {
			// the name stays as it is
		}
	}
}
