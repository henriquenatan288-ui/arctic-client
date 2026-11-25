package com.witch.mixin;

import com.witch.module.BoatFlyModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No clip do Boat Fly: sem a tela escura de "dentro do bloco". */
@Mixin(ScreenEffectRenderer.class)
public abstract class BoatNoclipOverlayMixin {
	@Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true, require = 0)
	private static void witch$noDark(Player player, CallbackInfoReturnable<BlockState> cir) {
		if (BoatFlyModule.noclipActive) cir.setReturnValue(null);
	}
}
