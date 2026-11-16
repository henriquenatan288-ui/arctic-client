package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fundo do ClickGUI: preto com listras diagonais suaves e finas linhas vermelhas (estilo "maca da bruxa"). */
public final class GuiBackdrop {
	private GuiBackdrop() {}

	public static final int RED = 0xFFD3162B;
	public static final int RED_DEEP = 0xFF7E0C18;

	private static void band(GuiGraphicsExtractor g, int w, int h, float off, float bw, int a1, int a2) {
		float cx = w / 2f, cy = h / 2f, len = w + h;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(-0.72f);
		int o = Math.round(off), b = Math.round(bw);
		g.fill(Math.round(-len), o - 2, Math.round(len), o + b + 2, a1);
		g.fill(Math.round(-len), o, Math.round(len), o + b, a2);
		g.pose().popMatrix();
	}

	private static void line(GuiGraphicsExtractor g, int w, int h, float off, float x0, float x1, int color) {
		float cx = w / 2f, cy = h / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(-0.72f);
		int o = Math.round(off);
		// fade nas pontas
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			float f = (float) Math.sin(Math.PI * (i + 0.5f) / steps);
			int xa = Math.round(x0 + (x1 - x0) * i / steps), xb = Math.round(x0 + (x1 - x0) * (i + 1) / steps);
			g.fill(xa, o, xb, o + 1, RenderUtil.alpha(color, f));
		}
		g.pose().popMatrix();
	}

	public static void draw(GuiGraphicsExtractor g, int w, int h, float a) {
		g.fill(0, 0, w, h, RenderUtil.alpha(0xF0050507, a));
		float s = Math.max(w, h) / 600f;
		band(g, w, h, -170 * s, 60 * s, RenderUtil.alpha(0x06FFFFFF, a), RenderUtil.alpha(0x0AFFFFFF, a));
		band(g, w, h, -20 * s, 110 * s, RenderUtil.alpha(0x06FFFFFF, a), RenderUtil.alpha(0x0CFFFFFF, a));
		band(g, w, h, 190 * s, 70 * s, RenderUtil.alpha(0x05FFFFFF, a), RenderUtil.alpha(0x09FFFFFF, a));
		line(g, w, h, -250 * s, -w * 0.9f, -w * 0.1f, RenderUtil.alpha(RED, 0.55f * a));
		line(g, w, h, 300 * s, w * 0.1f, w * 0.9f, RenderUtil.alpha(RED, 0.45f * a));
		RenderUtil.glow(g, 0, h, Math.round(Math.max(w, h) * 0.35f), RenderUtil.alpha(RED, 0.10f * a));
		RenderUtil.glow(g, w, 0, Math.round(Math.max(w, h) * 0.30f), RenderUtil.alpha(RED, 0.07f * a));
	}

	/** Linha horizontal vermelha que some nas pontas. */
	public static void redRule(GuiGraphicsExtractor g, int x, int y, int w, float a) {
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			float f = (float) Math.sin(Math.PI * (i + 0.5f) / steps);
			int xa = x + w * i / steps, xb = x + w * (i + 1) / steps;
			g.fill(xa, y, xb, y + 1, RenderUtil.alpha(RED, 0.75f * f * a));
		}
	}
}
