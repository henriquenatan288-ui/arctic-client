package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Icones vetoriais simples (triangulos, barras, setas) desenhados com fills: nitidos em qualquer escala, sem texturas. */
public final class Icons {
	private Icons() {}

	/** Triangulo apontando para a direita (ou esquerda) dentro de (x, y, w, h). */
	public static void tri(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean right, int color) {
		for (int r = 0; r < h; r++) {
			float k = 1f - Math.abs(2f * (r + 0.5f) / h - 1f);
			int ww = Math.max(1, Math.round(w * k));
			if (right) g.fill(x, y + r, x + ww, y + r + 1, color);
			else g.fill(x + w - ww, y + r, x + w, y + r + 1, color);
		}
	}

	/** Tamanho s centrado em (cx, cy). */
	public static void play(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		tri(g, cx - s / 3, cy - s / 2, s * 4 / 5, s, true, color);
	}

	public static void pause(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int bw = Math.max(2, s / 3);
		g.fill(cx - bw - 1, cy - s / 2, cx - 1, cy + s / 2, color);
		g.fill(cx + 1, cy - s / 2, cx + bw + 1, cy + s / 2, color);
	}

	public static void stop(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int h = s * 3 / 5;
		g.fill(cx - h, cy - h, cx + h, cy + h, color);
	}

	public static void next(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int t = Math.max(3, s * 2 / 3);
		tri(g, cx - s / 2, cy - s / 2, t, s, true, color);
		g.fill(cx + s / 2 - 1, cy - s / 2, cx + s / 2 + 1, cy + s / 2, color);
	}

	public static void prev(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int t = Math.max(3, s * 2 / 3);
		tri(g, cx + s / 2 - t, cy - s / 2, t, s, false, color);
		g.fill(cx - s / 2 - 1, cy - s / 2, cx - s / 2 + 1, cy + s / 2, color);
	}

	private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= n; i++) {
			int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	/** Duas setas que se cruzam. */
	public static void shuffle(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int h = s / 2, w = s;
		line(g, cx - w / 2, cy - h / 2, cx + w / 2 - 2, cy + h / 2, color);
		line(g, cx - w / 2, cy + h / 2, cx + w / 2 - 2, cy - h / 2, color);
		tri(g, cx + w / 2 - 3, cy - h / 2 - 2, 4, 5, true, color);
		tri(g, cx + w / 2 - 3, cy + h / 2 - 2, 4, 5, true, color);
	}

	/** Laco com seta. */
	public static void repeat(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int w = s, h = s * 2 / 3;
		RenderUtil.roundRing(g, cx - w / 2, cy - h / 2, w, h, Math.max(2, h / 2), color);
		tri(g, cx + w / 2 - 4, cy - h / 2 - 2, 5, 5, true, color);
	}

	/** Alto-falante com ondas (v = 0..1). */
	public static void speaker(GuiGraphicsExtractor g, int cx, int cy, int s, float v, int color) {
		g.fill(cx - s / 2, cy - s / 5, cx - s / 6, cy + s / 5 + 1, color);
		tri(g, cx - s / 2 + s / 3 - 1, cy - s / 2, s / 2, s, false, color);
		if (v > 0.05f) line(g, cx + s / 2, cy - s / 5, cx + s / 2, cy + s / 5, color);
		if (v > 0.5f) line(g, cx + s / 2 + 2, cy - s / 3, cx + s / 2 + 2, cy + s / 3, color);
	}

	/** Nota musical (colcheia). */
	public static void note(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
		int hr = Math.max(2, s / 4);
		RenderUtil.roundRect(g, cx - s / 3 - hr, cy + s / 4 - hr / 2, hr * 2 + 1, hr + 2, hr, color);
		g.fill(cx - s / 3 + hr - 1, cy - s / 2, cx - s / 3 + hr + 1, cy + s / 4 + 1, color);
		g.fill(cx - s / 3 + hr + 1, cy - s / 2, cx + s / 3 + hr, cy - s / 2 + 2, color);
		g.fill(cx + s / 3 + hr - 1, cy - s / 2 + 2, cx + s / 3 + hr + 1, cy + s / 8, color);
	}

	/** Barras de equalizador animadas dentro de (x, y, w, h). */
	public static void equalizer(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean playing, int color) {
		double t = System.nanoTime() / 1.0e9;
		int n = 4;
		int bw = Math.max(2, (w - (n - 1)) / n);
		for (int i = 0; i < n; i++) {
			double k = playing ? 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * (4.5 + i * 1.9) + i * 1.7)) : 0.25;
			int bh = Math.max(2, (int) (h * k));
			g.fill(x + i * (bw + 1), y + h - bh, x + i * (bw + 1) + bw, y + h, color);
		}
	}
}
