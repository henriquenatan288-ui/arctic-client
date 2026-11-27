package com.witch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Tela inicial de carregamento do jogo (logo da Mojang): mantem a logo, troca o fundo vermelho pelo grafite do Witch,
 * a barra por uma fina e adiciona a marca do client. Usa so a fonte padrao (a Inter ainda nao carregou nessa etapa).
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private long fadeOutStart;

	@Shadow
	private float currentProgress;

	private long witch$t0 = 0L;

	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 0)
	private int witch$bg(IntSupplier original) {
		return com.witch.Edition.visuals() ? 0xFF0A0A0C : original.getAsInt();
	}

	/** Some a logo da Mojang (as duas metades): no lugar dela o Witch escreve o proprio nome. */
	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
			require = 0)
	private void witch$noMojang(GuiGraphicsExtractor g, com.mojang.blaze3d.pipeline.RenderPipeline pipe,
			net.minecraft.resources.Identifier tex, int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th, int color) {
		if (com.witch.Edition.visuals()) return;
		g.blit(pipe, tex, x, y, u, v, w, h, rw, rh, tw, th, color);
	}

	@Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$bar(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY, float alpha, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
		ci.cancel();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void witch$brand(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
		float fo = fadeOutStart > -1L ? (Util.getMillis() - fadeOutStart) / 1000.0f : -1.0f;
		float a = fo >= 1.0f ? 1.0f - Mth.clamp(fo - 1.0f, 0.0f, 1.0f) : 1.0f;
		if (a <= 0.02f) return;
		var mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		long now = Util.getMillis();
		if (witch$t0 == 0L) witch$t0 = now;
		float exit = fo > 0f ? Mth.clamp(fo, 0f, 1f) : 0f;
		com.witch.gui.LoadingAnim.draw(g, w, h, (now - witch$t0) / 1000f, currentProgress, a, exit, now / 1000.0);
	}
}
