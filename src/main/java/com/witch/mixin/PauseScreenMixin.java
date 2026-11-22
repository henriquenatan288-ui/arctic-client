package com.witch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin() {
		super(null);
	}

	/** Menu de pausa (dentro do mundo): fundo desfocado do jogo + veu azul, marca e titulo, sem esconder o mundo. */
	@Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
	private void witch$pause(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		if (!com.witch.Edition.visuals()) return;
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
		com.witch.gui.WitchLogo.draw(g, 10, this.height - mw - 8, mw, 0.95f);
		com.witch.gui.Minimal.spaced(g, "ARCTIC", 46f, this.height - 34f, 0.9f, 4f, 0xFFFFFFFF, 0.9f, false);
		com.witch.gui.Minimal.spaced(g, "PAUSED", 46f, this.height - 20f, 0.6f, 3f, 0xFFB6CCEB, 0.8f, false);
	}
}
