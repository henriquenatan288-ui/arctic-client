package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Animacao da tela de carregamento do Arctic: aurora e estrelas ao fundo, a estrela acende (faiscas, onda de choque, raios),
 * anel de marcas que enche com o progresso real, faiscas orbitando, nome letra por letra e saida com zoom.
 * Fica numa classe so para o teste visual poder desenhar em qualquer instante.
 */
public final class LoadingAnim {
	private LoadingAnim() {}

	private static float shown;
	private static double lastSec = -1;

	/** Teste visual: fixa o progresso suavizado e reinicia o relogio interno. */
	public static void snap(float p) { shown = p; lastSec = -1; }

	private static float hash(int i, int k) {
		int h = i * 374761393 + k * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}

	private static float ease3(float t) { t = Mth.clamp(t, 0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t); }

	/** ti = segundos desde o inicio; progress 0..1 (do jogo); a = opacidade geral; exit = 0..1 durante a saida; sec = relogio continuo. */
	public static void draw(GuiGraphicsExtractor g, int w, int h, float ti, float progress, float a, float exit, double sec) {
		if (a <= 0.01f) return;
		// progresso suavizado (o do jogo anda aos saltos)
		double dt = lastSec < 0 ? 0.016 : Math.min(0.1, Math.max(0, sec - lastSec));
		lastSec = sec;
		shown += (Mth.clamp(progress, 0f, 1f) - shown) * (float) (1 - Math.exp(-dt * 5.0));
		if (exit > 0) shown = 1f;
		int A = (int) (a * 255f);

		// ---- fundo: degrade azul-noite
		g.fillGradient(0, 0, w, h, (A << 24) | 0x05070F, (A << 24) | 0x0B1530);
		float fadeIn = ease3(ti / 0.8f);
		// aurora: 3 manchas grandes passeando
		int[] au = {0x2F6BFF, 0x7A4DFF, 0x20C9D8};
		for (int i = 0; i < 3; i++) {
			float ax = w * (0.5f + 0.34f * (float) Math.sin(sec * 0.11 + i * 2.1));
			float ay = h * (0.42f + 0.24f * (float) Math.cos(sec * 0.09 + i * 1.7));
			StarLogo.glow(g, ax, ay, Math.max(w, h) * (0.85f + 0.15f * i), a * fadeIn * 0.17f, au[i]);
		}
		// estrelas ao fundo (cintilam e derivam devagar) e neve caindo
		float gs = (float) Math.max(1.0, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
		for (int i = 0; i < 90; i++) {
			float x = (hash(i, 1) * w + (float) sec * (1.5f + hash(i, 4) * 3f)) % w;
			float y = hash(i, 2) * h;
			float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(sec * (0.8 + hash(i, 3) * 2.2) + i));
			int sz = hash(i, 5) > 0.85f ? 2 : 1;
			int c = ((int) (a * fadeIn * tw * (sz == 2 ? 200 : 140))) << 24 | (hash(i, 6) > 0.6f ? 0xBFD8FF : 0xFFFFFF);
			g.fill((int) x, (int) y, (int) x + sz, (int) y + sz, c);
		}
		for (int i = 0; i < 38; i++) {
			float sp = 10f + hash(i, 11) * 18f;
			float y = (hash(i, 12) * h + (float) sec * sp) % h;
			float x = hash(i, 13) * w + 9f * (float) Math.sin(sec * 0.7 + i * 1.3);
			int c = ((int) (a * fadeIn * (0.25f + hash(i, 14) * 0.35f) * 255f)) << 24 | 0xE6F0FF;
			g.fill((int) x, (int) y, (int) x + 1 + (hash(i, 15) > 0.7f ? 1 : 0), (int) y + 1 + (hash(i, 15) > 0.7f ? 1 : 0), c);
		}

		// ---- estrela
		float mw = Math.max(56f, Math.min(190f, h * 0.34f));
		float cx = w / 2f, cy = h * 0.40f;
		float k = Mth.clamp((ti - 0.35f) / 1.1f, 0f, 1f);
		float back = 1f + 2.2f * (float) Math.pow(k - 1f, 3) + 1.2f * (float) Math.pow(k - 1f, 2);
		float sz = mw * (0.2f + 0.8f * back) * (1f + 0.025f * (float) Math.sin(sec * 1.7));
		sz *= 1f + exit * 0.55f;
		float star = ease3((ti - 0.25f) / 0.5f);

		// ignicao: ponto de luz que cresce antes da estrela
		float ig = ease3(ti / 0.4f);
		StarLogo.glow(g, cx, cy, mw * (0.25f + 1.4f * ig), a * (1f - 0.5f * star) * 0.9f * (1f - exit), 0xFFFFFF);

		// halo
		float pulse = 0.55f + 0.15f * (float) Math.sin(sec * 1.9);
		StarLogo.glow(g, cx, cy, sz * 3.6f, a * star * pulse, 0x6FA8FF);
		StarLogo.glow(g, cx, cy, sz * 1.9f, a * star * 0.45f, 0xFFFFFF);

		// raios girando devagar (8 longos + 8 curtos)
		float rayA = a * star * (0.55f + 0.2f * (float) Math.sin(sec * 2.0)) * (1f - exit * 0.5f);
		for (int i = 0; i < 16; i++) {
			boolean longRay = i % 2 == 0;
			float ang = (float) (sec * 0.12 + i * Math.PI / 8);
			float len = sz * (longRay ? 0.95f + 0.2f * (float) Math.sin(sec * 1.6 + i) : 0.5f + 0.1f * (float) Math.sin(sec * 2.3 + i));
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate(ang);
			int a0 = (int) (rayA * (longRay ? 200f : 120f));
			int y0 = -(int) (sz * 0.42f + len), y1 = -(int) (sz * 0.42f);
			g.fillGradient(-1, y0, 1, y1, 0x00BFD8FF, (a0 << 24) | 0xBFD8FF);
			g.pose().popMatrix();
		}

		// onda de choque na ignicao (e na saida)
		float sw = (ti - 0.3f) / 1.3f;
		if (sw > 0f && sw < 1f) ring(g, cx, cy, sz * (0.5f + 2.6f * ease3(sw)), a * (1f - sw) * 0.55f, 0xCFE2FF);
		if (exit > 0f) ring(g, cx, cy, sz * (1f + 2.2f * exit), a * (1f - exit) * 0.6f, 0xCFE2FF);

		// estrela com separacao RGB que fecha na entrada e treme de leve
		float jolt = ((long) (sec * 1000) % 2600L) < 110L ? 1f : 0f;
		float split = 10f * (1f - star) * (1f - star) + 2.0f * jolt + 0.7f;
		StarLogo.draw(g, cx - split, cy, sz, a * star * 0.85f, 0x3A8DFF);
		StarLogo.draw(g, cx + split, cy + jolt, sz, a * star * 0.6f, 0xFF3F6A);
		StarLogo.draw(g, cx + jolt * 3f, cy, sz, a * star, 0xFFFFFF);

		// ---- anel de marcas (progresso real) + faiscas orbitando
		float ringIn = ease3((ti - 0.9f) / 0.7f);
		if (ringIn > 0.01f) {
			float R = sz * 0.74f * (0.85f + 0.15f * ringIn);
			int n = 60;
			float p = shown * n;
			for (int i = 0; i < n; i++) {
				float lit = Mth.clamp(p - i, 0f, 1f);
				float head = Math.max(0f, 1f - Math.abs(p - i - 0.5f) * 0.6f);
				float ta = a * ringIn * (0.16f + 0.84f * lit) + a * ringIn * head * 0.5f * (shown < 0.999f ? 1f : 0f);
				int col = lit > 0.5f ? 0xFFFFFF : 0x7FA6E8;
				float ang = (float) (-Math.PI / 2 + i * Math.PI * 2 / n);
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate(ang + (float) Math.PI / 2);
				int tl = (i % 5 == 0) ? 6 : 3;
				g.fill(-1, -(int) R - tl, 1, -(int) R, (Mth.clamp((int) (ta * 255f), 0, 255) << 24) | col);
				g.pose().popMatrix();
			}
			for (int s = 0; s < 4; s++) {
				for (int tr = 0; tr < 7; tr++) {
					float ang = (float) (sec * 1.5 + s * Math.PI / 2 - tr * 0.07);
					float rr = R * 1.18f + 5f * (float) Math.sin(sec * 2 + s);
					StarLogo.glow(g, cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, (16f - tr * 1.6f) * (gs > 2 ? 1f : 1.1f), a * ringIn * (1f - tr / 7f) * 0.85f, tr == 0 ? 0xFFFFFF : 0x9CC4FF);
				}
			}
		}

		// ---- nome e porcentagem
		float sc = Math.max(2f, Math.min(5f, h / 110f));
		float ty = h * 0.66f - FontUtil.height() * sc / 2f;
		Minimal.spaced(g, "ARCTIC", w / 2f, ty, sc, sc * 2.5f, 0xFFFFFFFF, a, true, ti * 1000f / 140f);
		float pa = a * ease3((ti - 1.4f) / 0.6f);
		if (pa > 0.01f && exit <= 0f) {
			String pct = Math.round(shown * 100f) + "%";
			Minimal.spaced(g, pct, w / 2f, ty + FontUtil.height() * sc + 12f, Math.max(1.2f, sc * 0.4f), 2f, 0xFF9CB8E6, pa * 0.9f, false, 99f);
		}
	}

	/** Anel fino (circulo) centrado, diametro d. */
	private static void ring(GuiGraphicsExtractor g, float cx, float cy, float d, float alpha, int rgb) {
		int ai = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (ai <= 0) return;
		int r = Math.max(2, Math.round(d / 2f));
		RenderUtil.roundRing(g, Math.round(cx) - r, Math.round(cy) - r, r * 2, r * 2, r, (ai << 24) | (rgb & 0xFFFFFF));
	}
}
