package com.witch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.witch.module.WingsModule;
import com.witch.render.Wings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wings: depois de desenhar o corpo do jogador, submete as asas. */
@Mixin(LivingEntityRenderer.class)
public abstract class WingsMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("RETURN"), require = 0)
	private void witch$wings(LivingEntityRenderState state, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, CallbackInfo ci) {
		if (!WingsModule.on || !(state instanceof AvatarRenderState av) || state.isInvisible) return;
		try {
			var mc = net.minecraft.client.Minecraft.getInstance();
			boolean self = mc.player != null && av.id == mc.player.getId();
			if (!WingsModule.wants(self)) return;
			Wings.submit(av, ps, c);
		} catch (Throwable ignored) {
		}
	}
}
