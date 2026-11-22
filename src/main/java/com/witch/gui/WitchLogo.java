package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Marca do Arctic: um "A" de montanha (dois tracos e uma barra), desenhado em codigo, sem textura. Funciona em qualquer tamanho. */
public final class WitchLogo {
	private WitchLogo() {}

	public static int height(int w) {
		return Math.max(2, Math.round(w * 0.82f));
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha) {
		draw(g, x, y, w, alpha, 0xFFFFFFFF);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0 || w < 4) return;
		int h = height(w);
		int col = (a << 24) | (rgb & 0xFFFFFF);
		float cx = x + w / 2f;
		float stroke = Math.max(1.5f, w * 0.17f);
		int barY0 = Math.round(h * 0.66f), barY1 = barY0 + Math.max(1, Math.round(h * 0.09f));
		for (int r = 0; r < h; r++) {
			float t = (r + 0.5f) / h;
			float hw = t * w / 2f; // meia largura externa naquela linha
			float inner = Math.max(0f, hw - stroke);
			int yy = y + r;
			if (inner <= 0.5f) {
				g.fill(Math.round(cx - hw), yy, Math.round(cx + hw), yy + 1, col);
			} else {
				g.fill(Math.round(cx - hw), yy, Math.round(cx - inner), yy + 1, col);
				g.fill(Math.round(cx + inner), yy, Math.round(cx + hw), yy + 1, col);
				if (r >= barY0 && r < barY1) g.fill(Math.round(cx - inner), yy, Math.round(cx + inner), yy + 1, col);
			}
		}
	}
}
