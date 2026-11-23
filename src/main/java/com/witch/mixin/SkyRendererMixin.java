package com.witch.mixin;

import com.witch.module.SkyModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Shadow
	private RenderTarget renderTarget;
	@Shadow
	private GpuBuffer topSkyBuffer;

	/** Ceu de plasma: no lugar do disco colorido do ceu. */
	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$plasma(int color, CallbackInfo ci) {
		int c = SkyModule.plasmaColor();
		if (c != 0 && com.witch.render.PlasmaSky.draw(renderTarget, topSkyBuffer, c)) {
			com.witch.render.PlasmaSky.draw(renderTarget, topSkyBuffer, c, true); // metade de baixo (abaixo do horizonte)
			ci.cancel();
		}
	}

	/** Com plasma, o disco escuro de baixo do jogo some: a metade de baixo ja e plasma. */
	@Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$noDark(CallbackInfo ci) {
		if (SkyModule.plasmaColor() != 0) ci.cancel();
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void witch$sky(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
		SkyModule.apply(state);
	}
}
