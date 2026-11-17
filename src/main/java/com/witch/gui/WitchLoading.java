package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Tela de carregamento do Witch Client (mundo / entrando em servidor): minimalista, linha de progresso de 1px. */
public final class WitchLoading {
	private WitchLoading() {}

	private static final String[] TIPS = {
			"F9 liga e desliga a fonte Inter do client.",
			"Passe o mouse num modulo do ClickGUI para ver o que ele faz.",
			"A tecla Home devolve as janelas do ClickGUI ao lugar original.",
			"Clique direito no titulo de uma janela reseta so aquela janela.",
			"Em GUI Settings voce ajusta a transparencia do ClickGUI.",
			"Tracers: a cor da linha acompanha a cor do ESP.",
			"Sus Chunk Finder: ajuste o raio de 1 a 30 chunks.",
	};
	private static long startMs = 0L;
	private static long lastMs = 0L;

	/** Luz suave centrada em (cx, cy) (usada pelas telas de menu). */
	public static void glow(GuiGraphicsExtractor g, int cx, int cy, int r, float alpha) {
		RenderUtil.glow(g, cx, cy, r, RenderUtil.alpha(Minimal.light() ? 0xFFFFFFFF : 0xFF8C1C2A, Math.min(1f, alpha * 0.12f)));
	}

	/** Fundo das telas de menu (selecao de mundo, multiplayer, opcoes...). */
	public static void backdrop(GuiGraphicsExtractor g, int w, int h, double t) {
		Minimal.background(g, w, h, System.currentTimeMillis() / 1000.0, false);
	}

	/** progress 0..1, ou negativo = indeterminado (trecho que desliza). */
	public static void draw(GuiGraphicsExtractor g, int w, int h, String title, String sub, float progress, String hint) {
		long nowMs = System.currentTimeMillis();
		double sec = nowMs / 1000.0;
		if (nowMs - lastMs > 1500L) startMs = nowMs;
		lastMs = nowMs;
		float age = (nowMs - startMs) / 1000f;
		long elapsed = (nowMs - startMs) / 1000L;

		Minimal.background(g, w, h, sec, true);
		float in = Minimal.easeOut(age / 0.6f);
		int cx = w / 2, cy = h / 2;

		Minimal.wordmark(g, cx, cy - 34, 1.0f, 9f, in, 999f);

		// linha de progresso de 1px
		int bw = Math.min(190, w - 60);
		int bx = cx - bw / 2, by = cy - 4;
		g.fill(bx, by, bx + bw, by + 1, RenderUtil.alpha(Minimal.line(), 0.14f * in));
		if (progress >= 0f) {
			float p = Math.min(1f, progress);
			int fw = Math.max(1, Math.round(bw * p));
			g.fill(bx, by, bx + fw, by + 1, RenderUtil.alpha(Minimal.accent(), in));
			// brilho leve na ponta
			RenderUtil.glow(g, bx + fw, by, 9, RenderUtil.alpha(Minimal.accent(), 0.55f * in));
		} else {
			float ph = (float) ((sec * 0.55) % 1.0);
			int seg = bw / 3;
			int sx = bx + Math.round((bw + seg) * ph) - seg;
			int x0 = Math.max(bx, sx), x1 = Math.min(bx + bw, sx + seg);
			if (x1 - x0 > 1) {
				float mid = (x0 + x1) / 2f;
				for (int x = x0; x < x1; x += 2) {
					float f = 1f - Math.abs(x - mid) / ((x1 - x0) / 2f);
					g.fill(x, by, Math.min(x + 2, x1), by + 1, RenderUtil.alpha(Minimal.accent(), f * in));
				}
			}
		}

		Minimal.spaced(g, title.toUpperCase(), cx, by + 14, 0.8f, 3f, Minimal.fg(), in, true);
		String info = progress >= 0f ? Math.round(Math.min(1f, progress) * 100) + "%   " + elapsed + "s" : elapsed + "s";
		Minimal.spaced(g, info, cx, by + 28, 0.7f, 2f, Minimal.dim(), in, true);
		if (sub != null && !sub.isEmpty()) {
			Minimal.spaced(g, sub, cx, by + 42, 0.7f, 0.6f, Minimal.dim(), in * 0.9f, true);
		}

		if (hint == null || hint.isEmpty()) {
			hint = TIPS[(int) ((nowMs / 6000L) % TIPS.length)];
		}
		long ph = nowMs % 6000L;
		float fa = Math.min(1f, Math.min(ph, 6000L - ph) / 700f);
		Minimal.spaced(g, hint, cx, h - 30, 0.7f, 0.8f, Minimal.faint(), fa, true);
		String credit = "MADE BY BISTECK";
		Minimal.spaced(g, credit, w - 14 - Minimal.spacedWidth(credit, 0.62f, 2f), h - 16, 0.62f, 2f, Minimal.faint(), 0.7f, false);
	}
}
