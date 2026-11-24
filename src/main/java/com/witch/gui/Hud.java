package com.witch.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Estilo unico e minimalista dos widgets da tela: texto pequeno, pilula escura sem contorno,
 * pontinho de cor. Tambem organiza os alertas (pilhas no centro e no canto direito superior).
 */
public final class Hud {
	private Hud() {}

	/** Escala do texto dos widgets. */
	public static final float S = 0.78f;
	public static final int DIM = 0xFF8C8C92;
	public static final int WHITE = 0xFFF2F2F4;

	private static int alertY;
	private static int rightY;

	/** Chamado uma vez por quadro, antes de desenhar os widgets. */
	public static void begin() {
		Minecraft mc = Minecraft.getInstance();
		int sh = mc.getWindow().getGuiScaledHeight();
		alertY = sh / 2 - 62;
		rightY = 6;
	}

	public static int th() {
		return Math.round(FontUtil.height() * S);
	}

	public static int tw(String s) {
		return Math.round(FontUtil.width(s) * S);
	}

	/** Altura padrao de uma linha-pilula de uma linha de texto. */
	public static int rowH() {
		return th() + 5;
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate((float) x, (float) y);
		g.pose().scale(S, S);
		FontUtil.draw(g, s, 0, 0, color);
		g.pose().popMatrix();
	}

	/** Fundo padrao: escuro translucido, sem contorno, cantos suaves. */
	public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, float a) {
		RenderUtil.roundRect(g, x, y, w, h, Math.min(h / 2, 4), RenderUtil.alpha(0xFF08080A, 0.58f * a));
	}

	public static void dot(GuiGraphicsExtractor g, int x, int cy, int color) {
		RenderUtil.roundRect(g, x, cy - 1, 3, 3, 1, color);
	}

	/** Pilula de uma linha com ponto de cor, centralizada no topo do bloco de alertas (empilha para baixo). */
	public static void alert(GuiGraphicsExtractor g, String text, int accent, float a) {
		int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int h = rowH();
		int w = tw(text) + 17;
		int x = (sw - w) / 2;
		pill(g, x, alertY, w, h, a);
		dot(g, x + 6, alertY + h / 2, RenderUtil.alpha(accent, a));
		text(g, text, x + 12, alertY + (h - th()) / 2, RenderUtil.alpha(WHITE, a));
		alertY += h + 2;
	}

	/** Reserva espaco no canto direito superior (abaixo dos avisos de modulo) e devolve o y livre. */
	public static int takeRight(int h) {
		int y = rightY;
		rightY += h + 3;
		return y;
	}

	public static int rightTop() {
		return rightY;
	}
}
