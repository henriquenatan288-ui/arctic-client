package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Marca do Arctic: a imagem do mod (estrela com maos), em 3 tamanhos de textura. */
public final class WitchLogo {
	private WitchLogo() {}

	private static final Identifier T64 = Identifier.fromNamespaceAndPath("witch", "textures/gui/mod_64.png");
	private static final Identifier T128 = Identifier.fromNamespaceAndPath("witch", "textures/gui/mod_128.png");
	private static final Identifier T256 = Identifier.fromNamespaceAndPath("witch", "textures/gui/mod_256.png");

	public static int height(int w) {
		return Math.max(2, w);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha) {
		draw(g, x, y, w, alpha, 0xFFFFFFFF);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0 || w < 4) return;
		// o GUI amostra sem filtro: usa o tamanho de textura mais proximo (so reduz, nunca estica) para nao pixelar
		int t = w <= 80 ? 64 : w <= 160 ? 128 : 256;
		Identifier id = t == 64 ? T64 : t == 128 ? T128 : T256;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, w, t, t, t, t, (a << 24) | (rgb & 0xFFFFFF));
	}
}
