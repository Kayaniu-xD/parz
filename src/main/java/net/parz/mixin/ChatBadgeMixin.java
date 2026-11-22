package net.parz.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.parz.badge.PlayerBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The names of Parz users in the chat. This is the last step before a message is shown (all chat messages go
 * through it), so every other mod has already read the original text and nothing that parses the chat is confused.
 */
@Mixin(ChatComponent.class)
public abstract class ChatBadgeMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component parz$names(Component message) {
		try {
			return PlayerBadge.applyInChat(message);
		} catch (RuntimeException e) {
			return message;
		}
	}
}
