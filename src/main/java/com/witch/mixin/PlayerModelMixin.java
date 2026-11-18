package com.witch.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Legs: esconde as pernas do modelo no fim do setupAnim (que ja redefine a visibilidade a cada jogador). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
	private void witch$legs(AvatarRenderState state, CallbackInfo ci) {
		if (!com.witch.module.NoLegsModule.on) return;
		try {
			var mc = net.minecraft.client.Minecraft.getInstance();
			boolean self = mc.player != null && state.id == mc.player.getId();
			int mode = com.witch.module.NoLegsModule.modeFor(self);
			if (mode == 0) return;
			PlayerModel m = (PlayerModel) (Object) this;
			boolean hideL = mode == 1 || mode == 3;   // "so dir" esconde a esquerda
			boolean hideR = mode == 1 || mode == 2;
			if (hideL) { m.leftLeg.visible = false; m.leftPants.visible = false; }
			if (hideR) { m.rightLeg.visible = false; m.rightPants.visible = false; }
		} catch (Throwable ignored) {
		}
	}
}
