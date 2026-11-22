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
			net.minecraft.resources.Identifier tex, int x, int y, float u, float v, int w, int h, int rw, int rh, int tw, int th) {
		if (com.witch.Edition.visuals()) return;
		g.blit(pipe, tex, x, y, u, v, w, h, rw, rh, tw, th);
	}

	@Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$bar(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY, float alpha, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
		int cy = (minY + maxY) / 2;
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		com.witch.gui.RenderUtil.roundRect(g, minX, cy - 1, maxX - minX, 3, 1, ((a * 40 / 255) << 24) | 0xFFFFFF);
		int fw = Math.max(3, Math.round((maxX - minX) * Mth.clamp(currentProgress, 0f, 1f)));
		com.witch.gui.RenderUtil.roundRect(g, minX, cy - 1, fw, 3, 1, (a << 24) | 0xFFFFFF);
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
		int ai = (int) (a * 255f) << 24;
		long now = Util.getMillis();
		if (witch$t0 == 0L) witch$t0 = now;
		float t = Mth.clamp((now - witch$t0) / 900f, 0f, 1f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);
		com.witch.gui.Minimal.petals(g, w, h, now / 1000.0, a * ease);
		// nome do client no centro, letra por letra
		String name = "ARCTIC";
		float sc = Math.max(2f, Math.min(5f, h / 110f));
		float ty = h * 0.56f - com.witch.gui.FontUtil.height() * sc / 2f;
		int mw = Math.round(Math.max(48f, Math.min(160f, h * 0.30f)));
		int my0 = Math.round(h * 0.30f - mw / 2f + (1f - ease) * 6f);
		com.witch.gui.WitchLogo.draw(g, (w - mw) / 2, my0, mw, a * ease);
		com.witch.gui.Minimal.spaced(g, name, w / 2f, ty, sc, sc * 2.5f, 0xFFFFFFFF, a, true, (now - witch$t0) / 140f);
	}
}
