package com.witch.mixin;

import com.witch.module.BoatFlyModule;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No clip do Boat Fly no seu proprio mundo (singleplayer): o servidor integrado desfaz qualquer movimento do barco que
 * termine dentro de bloco ("vehicle moved wrongly"), o que travava na parede e no chao. Com o No clip valendo,
 * aceita a posicao enviada pelo cliente. So existe no servidor integrado; em servidor de terceiros nada muda.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerBoatNoclipMixin {
	@Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$noclip(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		if (!BoatFlyModule.noclipActive) return;
		var player = ((ServerGamePacketListenerImpl) (Object) this).player;
		var v = player.getRootVehicle();
		if (!(v instanceof AbstractBoat) || player.getControlledVehicle() != v) return;
		var p = packet.position();
		if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return;
		v.noPhysics = true;
		v.setNoGravity(true);
		v.setPos(p.x, p.y, p.z);
		v.setYRot(packet.yRot());
		v.setXRot(packet.xRot());
		player.level().getChunkSource().move(player);
		ci.cancel();
	}
}
