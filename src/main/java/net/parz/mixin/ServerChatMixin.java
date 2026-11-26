package net.parz.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.parz.dashboard.CollectionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the chat lines of the server the moment they arrive. Other mods hide some of them and show their own version
 * instead (for example the summary at the end of a dungeon run): a hidden message never reaches the chat events.
 * The early priority puts this before mods that stop the packet at the same place.
 */
@Mixin(value = Connection.class, priority = 100)
public abstract class ServerChatMixin {
	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void parz$serverChat(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof ClientboundSystemChatPacket chat && !chat.overlay()) {
			// this is the network thread: the game thread does the reading
			Minecraft.getInstance().execute(() -> CollectionTracker.onServerMessage(chat.content()));
		}
	}
}
