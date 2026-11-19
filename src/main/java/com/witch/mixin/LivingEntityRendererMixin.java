package com.witch.mixin;

import com.witch.render.PlayersGalaxy;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players Galaxy: troca o RenderType do corpo do jogador pelo da galaxia. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true, require = 0)
	private void witch$galaxy(LivingEntityRenderState state, boolean visible, boolean ghost, boolean glowing,
			CallbackInfoReturnable<RenderType> cir) {
		// getRenderType roda antes das camadas (armadura/elytra) deste mesmo corpo: guarda se ele e um jogador com galaxia
		PlayersGalaxy.curBody = 0;
		PlayersGalaxy.curWings = 0;
		boolean pg = com.witch.module.PlayersGalaxyModule.on;
		if (!pg && !com.witch.module.ElytraShaderModule.on) return;
		if (!(state instanceof AvatarRenderState)) {
			// armor stand, zumbi etc.: so a armadura/elytra (se a opcao for "todos")
			if (pg) PlayersGalaxy.curBody = 2;
			if (com.witch.module.ElytraShaderModule.wantsOther()) PlayersGalaxy.curWings = 2;
			return;
		}
		try {
			var mc = net.minecraft.client.Minecraft.getInstance();
			boolean self = mc.player != null && ((AvatarRenderState) state).id == mc.player.getId();
			if (com.witch.module.ElytraShaderModule.wantsPlayer(self)) PlayersGalaxy.curWings = 1;
			if (!pg || !PlayersGalaxy.wants(self)) return;
			PlayersGalaxy.curBody = 1;
			if (!visible || ghost || cir.getReturnValue() == null) return;
			RenderType t = PlayersGalaxy.type(((LivingEntityRenderer) (Object) this).getTextureLocation(state));
			if (t != null) cir.setReturnValue(t);
		} catch (Throwable ignored) {
		}
	}
}
