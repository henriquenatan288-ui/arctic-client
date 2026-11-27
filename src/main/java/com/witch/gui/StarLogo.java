package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Estrela do Arctic em texturas renderizadas com antialias, uma por tamanho. O GUI amostra sem filtro,
 * entao a textura mais proxima do tamanho REAL na tela e escolhida a cada quadro: sem serrilhado nem pixel
 * mesmo durante a animacao de escala.
 */
public final class StarLogo {
	private StarLogo() {}

	private static final int[] TIERS = {24, 32, 48, 64, 96, 128, 192, 256, 384, 512};
	private static final Identifier[] TEX = new Identifier[TIERS.length];
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("witch", "textures/gui/glow_256.png");

	static {
		for (int i = 0; i < TIERS.length; i++) TEX[i] = Identifier.fromNamespaceAndPath("witch", "textures/gui/star_" + TIERS[i] + ".png");
	}

	/** Estrela centrada em (cx, cy), lado = size (em pixels de GUI). */
	public static void draw(GuiGraphicsExtractor g, float cx, float cy, float size, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0 || size < 4f) return;
		double scale = 1.0;
		try { scale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale(); } catch (Throwable ignored) { }
		double real = size * scale;
		int best = 0;
		for (int i = 0; i < TIERS.length; i++) if (Math.abs(TIERS[i] - real) < Math.abs(TIERS[best] - real)) best = i;
		int t = TIERS[best];
		int s = Math.max(2, Math.round(size));
		g.blit(RenderPipelines.GUI_TEXTURED, TEX[best], Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, 0f, s, s, t, t, t, t,
				(a << 24) | (rgb & 0xFFFFFF));
	}

	/** Halo suave (gradiente radial) atras da estrela. */
	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float size, float alpha) {
		glow(g, cx, cy, size, alpha, 0xFFFFFF);
	}

	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float size, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0) return;
		int s = Math.max(2, Math.round(size));
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, 0f, s, s, 256, 256, 256, 256,
				(a << 24) | (rgb & 0xFFFFFF));
	}
}
