package com.witch.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Marca do Arctic: "A" de montanha com a barra solta (textura suave 128x115, tingivel). */
public final class WitchLogo {
	private WitchLogo() {}

	private static final Identifier TEX = Identifier.fromNamespaceAndPath("witch", "textures/gui/arctic_mark.png");

	public static int height(int w) {
		return Math.max(2, Math.round(w * 115f / 128f));
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha) {
		draw(g, x, y, w, alpha, 0xFFFFFFFF);
	}

	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, float alpha, int rgb) {
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		if (a <= 0 || w < 4) return;
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, x, y, 0f, 0f, w, height(w), 128, 115, 128, 115, (a << 24) | (rgb & 0xFFFFFF));
	}
}
