package com.witch.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shader do ClickGUI Arctic (plasma suave + reflexo de vidro + brilho de borda). Desenhado como um blit
 * com pipeline proprio; os parametros vao na cor do vertice. Se o shader nao compilar, devolve false
 * e o ClickGUI usa o reflexo simples feito em codigo.
 */
public final class GuiGlassShader {
	private GuiGlassShader() {}

	private static final Identifier TEX = Identifier.fromNamespaceAndPath("witch", "textures/gui/ring.png");
	private static RenderPipeline pipe;
	private static boolean failed;

	private static RenderPipeline pipeline() {
		if (pipe != null || failed) return pipe;
		try {
			var f = RenderPipelines.class.getDeclaredField("GUI_TEXTURED_SNIPPET");
			f.setAccessible(true);
			var snippet = (RenderPipeline.Snippet) f.get(null);
			var p = RenderPipeline.builder(snippet)
					.withLocation("pipeline/witch_gui_glass")
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_gui_glass"))
					.build();
			try {
				var r = RenderPipelines.class.getDeclaredMethod("register", RenderPipeline.class);
				r.setAccessible(true);
				p = (RenderPipeline) r.invoke(null, p);
			} catch (Throwable t) {
				System.out.println("[Witch] gui glass: register falhou: " + t);
			}
			var compiled = RenderSystem.getDevice().precompilePipeline(p);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou");
			pipe = p;
			System.out.println("[WDBG] gui glass shader: OK");
		} catch (Throwable t) {
			failed = true;
			System.out.println("[WDBG] gui glass shader: FALHOU: " + t);
			t.printStackTrace();
		}
		return pipe;
	}

	/** phase 0..1 (loop), intensity 0..1. Retorna false se o shader nao esta disponivel. */
	public static boolean draw(GuiGraphicsExtractor g, int x, int y, int w, int h, float phase, float intensity) {
		RenderPipeline p = pipeline();
		if (p == null || w <= 0 || h <= 0) return false;
		try {
			int ph = Math.max(0, Math.min(65535, Math.round((phase - (float) Math.floor(phase)) * 65535f)));
			int r = ph >> 8, gg = ph & 255;
			int b = Math.max(0, Math.min(255, Math.round(intensity * 255f)));
			int a = Math.max(1, Math.min(255, Math.round((w / (float) h) * 60f)));
			int color = (a << 24) | (r << 16) | (gg << 8) | b;
			g.blit(p, TEX, x, y, 0f, 0f, w, h, 96, 96, 96, 96, color);
			return true;
		} catch (Throwable t) {
			failed = true;
			System.out.println("[WDBG] gui glass shader: erro ao desenhar: " + t);
			return false;
		}
	}
}
