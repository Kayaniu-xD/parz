package net.parz.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.parz.dungeon.DragonMarkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the dragon arrow stack indicator the two packets it needs: the spawn particles and the dragon itself. */
@Mixin(ClientPacketListener.class)
public abstract class DragonPacketsMixin {
	@Inject(method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V", at = @At("HEAD"))
	private void parz$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		try {
			DragonMarkers.onParticles(packet);
		} catch (Throwable t) {
			net.parz.Debug.error("dragon particles", t);
		}
	}

	@Inject(method = "handleAddEntity(Lnet/minecraft/network/protocol/game/ClientboundAddEntityPacket;)V", at = @At("HEAD"))
	private void parz$entity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		try {
			DragonMarkers.onEntity(packet);
		} catch (Throwable t) {
			net.parz.Debug.error("dragon entity", t);
		}
	}
}
