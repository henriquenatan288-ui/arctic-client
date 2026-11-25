package com.witch.mixin;

import com.witch.module.BoatFlyModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No clip do Boat Fly: o jogo nao empurra o jogador para fora dos blocos enquanto o barco os atravessa. */
@Mixin(LocalPlayer.class)
public abstract class BoatNoclipMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$noPush(double x, double z, CallbackInfo ci) {
		if (BoatFlyModule.noclipActive) ci.cancel();
	}
}
