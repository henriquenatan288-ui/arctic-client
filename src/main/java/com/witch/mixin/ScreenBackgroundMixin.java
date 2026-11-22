package com.witch.mixin;

import com.witch.gui.WitchLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fundo do Witch (grafite, linhas brilhantes, logo) nas telas de menu: seletor de mundo, criar mundo, multiplayer e opcoes.
 * extractBackground e declarado na Screen (as subclasses nao o redefinem), por isso o mixin fica nela e filtra pela tela.
 * So fora de um mundo: no menu de pausa continua o fundo desfocado do jogo.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$bg(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
		Object self = this;
		if (!(self instanceof SelectWorldScreen || self instanceof CreateWorldScreen
				|| self instanceof JoinMultiplayerScreen || self instanceof OptionsScreen)) return;
		if (Minecraft.getInstance().level != null) return;
		double t = System.nanoTime() / 1_000_000_000.0;
		WitchLoading.backdrop(g, this.width, this.height, t);
		if (!(self instanceof CreateWorldScreen)) { // la a marca cobriria as abas
			com.witch.gui.Minimal.wordmark(g, 44f, 14f, 0.62f, 5f, 0.9f, 999f);
			com.witch.gui.WitchLogo.draw(g, this.width - 34, 5, 26, 0.9f);
		}
		ci.cancel();
	}

	/** Menu de pausa (dentro do mundo): fundo desfocado do jogo + veu azul, marca e titulo, sem esconder o mundo. */
	@Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
	private void witch$pause(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
		Object self = this;
		if (!(self instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
		if (Minecraft.getInstance().level == null) return;
		int n = 24;
		for (int i = 0; i < n; i++) {
			float f = i / (float) (n - 1);
			g.fill(0, this.height * i / n, this.width, this.height * (i + 1) / n + 1,
					com.witch.gui.RenderUtil.alpha(com.witch.gui.RenderUtil.mix(0xFF1A3570, 0xFF050A18, f), 0.30f + 0.20f * f));
		}
		double t = System.nanoTime() / 1_000_000_000.0;
		com.witch.gui.Minimal.petals(g, this.width, this.height, t, 0.8f);
		int mw = 30;
		com.witch.gui.WitchLogo.draw(g, 10, 8, mw, 0.95f);
		com.witch.gui.Minimal.spaced(g, "ARCTIC", 46f, 12f, 0.9f, 4f, 0xFFFFFFFF, 0.9f, false);
		com.witch.gui.Minimal.spaced(g, "PAUSED", 46f, 26f, 0.6f, 3f, 0xFFB6CCEB, 0.8f, false);
	}
}
