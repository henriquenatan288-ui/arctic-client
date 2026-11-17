package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Visual minimalista compartilhado pelas telas de entrada, inicial e de carregamento:
 * fundo suave, letras finas espacadas, linhas de 1px e petalas leves caindo. Tudo com texturas suavizadas
 * (roundRect/glow), nada de pixel art.
 */
public final class Minimal {
	private Minimal() {}

	public static boolean light() {
		return com.witch.module.GuiSettingsModule.menuLight();
	}

	public static int top() { return light() ? 0xFFF1F1F2 : 0xFF0E0E10; }
	public static int bottom() { return light() ? 0xFFE2E2E4 : 0xFF050506; }
	public static int fg() { return light() ? 0xFF161618 : 0xFFF3EFF0; }
	public static int dim() { return light() ? 0xFF74747A : 0xFF8A8386; }
	public static int faint() { return light() ? 0xFFA9A9AE : 0xFF4A4547; }
	public static int line() { return light() ? 0xFF000000 : 0xFFFFFFFF; }
	public static int accent() { return light() ? 0xFF161618 : GuiBackdrop.RED; }

	// ---------- fundo ----------

	public static void background(GuiGraphicsExtractor g, int w, int h, double sec, boolean petals) {
		int n = 48;
		int a = top(), b = bottom();
		for (int i = 0; i < n; i++) {
			g.fill(0, h * i / n, w, h * (i + 1) / n + 1, RenderUtil.mix(a, b, i / (float) (n - 1)));
		}
		boolean lt = light();
		int gx = Math.round(w * (0.22f + 0.06f * (float) Math.sin(sec * 0.11)));
		int gy = Math.round(h * (0.30f + 0.05f * (float) Math.cos(sec * 0.09)));
		RenderUtil.glow(g, gx, gy, Math.round(h * 0.95f), RenderUtil.alpha(lt ? 0xFFFFFFFF : 0xFF8C1C2A, lt ? 0.55f : 0.10f));
		int hx = Math.round(w * (0.80f + 0.05f * (float) Math.cos(sec * 0.08)));
		int hy = Math.round(h * (0.78f + 0.04f * (float) Math.sin(sec * 0.10)));
		RenderUtil.glow(g, hx, hy, Math.round(h * 0.8f), RenderUtil.alpha(lt ? 0xFFFFFFFF : 0xFF22283A, lt ? 0.45f : 0.16f));
		if (petals) petals(g, w, h, sec, 1f);
	}

	private static final int PETALS = 20;
	private static final float[] PX = new float[PETALS], PY = new float[PETALS], PS = new float[PETALS],
			PW = new float[PETALS], PR = new float[PETALS], PZ = new float[PETALS], PF = new float[PETALS];

	static {
		java.util.Random r = new java.util.Random(26);
		for (int i = 0; i < PETALS; i++) {
			PX[i] = r.nextFloat();
			PY[i] = r.nextFloat();
			PZ[i] = r.nextFloat();             // profundidade: 0 longe, 1 perto
			PS[i] = 0.016f + 0.030f * PZ[i];   // velocidade de queda (fracao da altura por segundo)
			PW[i] = r.nextFloat() * 6.28f;     // fase do balanco
			PR[i] = (r.nextFloat() - 0.5f) * 1.2f; // giro
			PF[i] = r.nextFloat() * 6.28f;     // fase do bater
		}
	}

	/** Petalas pequenas caindo devagar (as mais proximas maiores e mais claras, as mais longe sumindo). */
	public static void petals(GuiGraphicsExtractor g, int w, int h, double sec, float alpha) {
		boolean lt = light();
		int col = lt ? 0xFF9A9AA0 : 0xFFF2D8DC;
		for (int i = 0; i < PETALS; i++) {
			float z = PZ[i];
			float fy = (float) (((PY[i] + sec * PS[i]) % 1.0 + 1.0) % 1.0);
			float x = PX[i] * w + (float) Math.sin(sec * (0.35 + 0.25 * z) + PW[i]) * (14f + 22f * z) - (float) (sec * 4.0 * z) % w;
			x = ((x % w) + w) % w;
			float y = fy * (h + 24) - 12;
			float edge = Math.min(1f, Math.min(fy, 1f - fy) * 8f); // some suave no topo/embaixo
			float al = (0.10f + 0.26f * z) * edge * alpha * (lt ? 1.15f : 1f);
			float s = 2.2f + 3.6f * z;
			float flutter = 0.45f + 0.55f * Math.abs((float) Math.cos(sec * 0.9 + PF[i]));
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().rotate((float) (PW[i] + sec * PR[i]));
			g.pose().scale(1f, flutter);
			int iw = Math.max(3, Math.round(s * 2f)), ih = Math.max(2, Math.round(s));
			RenderUtil.roundRect(g, -iw / 2, -ih / 2, iw, ih, ih / 2, RenderUtil.alpha(col, al));
			g.pose().popMatrix();
		}
	}

	// ---------- texto ----------

	public static float spacedWidth(String s, float scale, float spacing) {
		float t = 0f;
		for (int i = 0; i < s.length(); i++) t += FontUtil.width(String.valueOf(s.charAt(i))) * scale + (i > 0 ? spacing : 0f);
		return t;
	}

	/**
	 * Texto de letras finas espacadas. cx = centro (center=true) ou borda esquerda. reveal: quantas letras ja
	 * apareceram (1 letra por unidade, com fade); passe um valor grande para tudo visivel.
	 */
	public static void spaced(GuiGraphicsExtractor g, String s, float cx, float y, float scale, float spacing, int color,
			float alpha, boolean center, float reveal) {
		float x = center ? cx - spacedWidth(s, scale, spacing) / 2f : cx;
		for (int i = 0; i < s.length(); i++) {
			String c = String.valueOf(s.charAt(i));
			float la = Mth.clamp(reveal - i, 0f, 1f) * alpha;
			float cw = FontUtil.width(c) * scale;
			if (la > 0.01f && !c.equals(" ")) {
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				g.pose().scale(scale, scale);
				FontUtil.draw(g, c, 0, 0, RenderUtil.alpha(color, la));
				g.pose().popMatrix();
			}
			x += cw + spacing;
		}
	}

	public static void spaced(GuiGraphicsExtractor g, String s, float cx, float y, float scale, float spacing, int color,
			float alpha, boolean center) {
		spaced(g, s, cx, y, scale, spacing, color, alpha, center, 999f);
	}

	/** Marca "W I T C H" centrada em (cx, cy). */
	public static void wordmark(GuiGraphicsExtractor g, float cx, float cy, float scale, float spacing, float alpha, float reveal) {
		float y = cy - FontUtil.height() * scale / 2f;
		spaced(g, "WITCH", cx, y, scale, spacing, fg(), alpha, true, reveal);
	}

	/** Linha de 1px centrada, com as pontas esmaecendo. */
	public static void hairline(GuiGraphicsExtractor g, int cx, int y, int w, float alpha) {
		if (w < 2 || alpha <= 0.01f) return;
		int seg = Math.max(1, w / 10);
		for (int i = 0; i < 10; i++) {
			float f = 1f - Math.abs(i - 4.5f) / 5f;
			int x0 = cx - w / 2 + i * w / 10;
			int x1 = cx - w / 2 + (i + 1) * w / 10;
			g.fill(x0, y, x1, y + 1, RenderUtil.alpha(line(), 0.28f * alpha * (0.25f + 0.75f * f)));
		}
	}

	public static float easeOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	public static float easeInOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}
}
