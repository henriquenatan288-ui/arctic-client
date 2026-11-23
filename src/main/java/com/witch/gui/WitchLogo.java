package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Marca do Arctic: a imagem do mod (estrela com maos), em 3 tamanhos de textura. */
public final class WitchLogo {
	private WitchLogo() {}

	private static final int[] TIERS = {24, 32, 48, 64, 96, 128, 192, 256};
	private static final Identifier[] TEX = new Identifier[TIERS.length];

	static {
		for (int i = 0; i < TIERS.length; i++) TEX[i] = Identifier.fromNamespaceAndPath("witch", "textures/gui/mod_" + TIERS[i] + ".png");
	}

	public static int height(int w) {
		return Math.max(2, w);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha) {
		draw(g, x, y, w, alpha, 0xFFFFFFFF);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0 || w < 4) return;
		// o GUI amostra sem filtro e 1 px de GUI = guiScale px reais: escolhe a textura mais proxima do tamanho REAL na tela
		double scale = 1.0;
		try { scale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale(); } catch (Throwable ignored) { }
		double real = w * scale;
		int best = 0;
		for (int i = 0; i < TIERS.length; i++) if (Math.abs(TIERS[i] - real) < Math.abs(TIERS[best] - real)) best = i;
		int t = TIERS[best];
		g.blit(RenderPipelines.GUI_TEXTURED, TEX[best], x, y, 0f, 0f, w, w, t, t, t, t, (a << 24) | (rgb & 0xFFFFFF));
	}
}
