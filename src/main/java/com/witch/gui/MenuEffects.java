package com.witch.gui;

import com.witch.module.CardParticlesModule;
import com.witch.module.Module;
import com.witch.module.ModuleManager;
import com.witch.module.ParticlesModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Efeitos dos modulos Particles 3D / Particles 2D ligados, mostrados em volta do personagem na tela inicial. */
public final class MenuEffects {
	private MenuEffects() {}

	private static <T extends Module> T on(String name, Class<T> c) {
		try {
			Module m = ModuleManager.byName(name);
			return m != null && m.isEnabled() && c.isInstance(m) ? c.cast(m) : null;
		} catch (Throwable t) {
			return null;
		}
	}

	/** Particles 3D: pontos orbitando o personagem (front=false: parte de tras, antes do modelo; front=true: a da frente). */
	public static void orbit(GuiGraphicsExtractor g, int cx, int cy, int mw, int mh, double sec, boolean front, float alpha) {
		ParticlesModule pm = on("Particles 3D", ParticlesModule.class);
		if (pm == null) return;
		int n = 6 + pm.menuAmount() * 3;
		double spd = pm.menuSpeed() * 0.4;
		float R = mw * 0.80f;
		int trail = Math.min(6, pm.menuTrail());
		float sz = 1.5f + pm.menuSize() * 0.45f;
		for (int i = 0; i < n; i++) {
			int col = pm.menuColor(i, n, sec);
			for (int k = trail; k >= 0; k--) {
				double ang = (sec - k * 0.035) * spd + i * (Math.PI * 2.0 / n);
				double dep = Math.sin(ang);
				if ((dep >= 0) != front) continue;
				float x = cx + (float) Math.cos(ang) * R;
				float y = cy + (float) dep * R * 0.28f + (float) Math.sin(sec * 1.3 + i * 2.1) * mh * 0.12f;
				float sc = 0.75f + 0.25f * (float) dep;
				float a = alpha * (k == 0 ? 0.95f : 0.35f * (1f - k / (float) (trail + 1)));
				int d = Math.max(2, Math.round(sz * sc * (k == 0 ? 1f : 0.7f)));
				if (k == 0) RenderUtil.glow(g, Math.round(x), Math.round(y), d * 4, RenderUtil.alpha(col, a * 0.5f));
				RenderUtil.roundRect(g, Math.round(x) - d / 2, Math.round(y) - d / 2, d, d, d / 2, RenderUtil.alpha(col, a));
			}
		}
	}

	/** Particles 2D: formas (cartas, coracoes, flocos...) subindo e sumindo em volta do personagem. */
	public static void floaters(GuiGraphicsExtractor g, int cx, int groundY, int mw, int mh, double sec, float alpha) {
		CardParticlesModule cm = on("Particles 2D", CardParticlesModule.class);
		if (cm == null) return;
		int n = Math.max(4, Math.min(14, cm.menuAmount() + 3));
		int base = 12 + cm.menuSize() * 3;
		var pipe = RenderPipelines.GUI_TEXTURED;
		for (int i = 0; i < n; i++) {
			double ph = (sec * 0.16 + i / (double) n) % 1.0;
			float a = (float) Math.sin(ph * Math.PI) * alpha;
			if (a <= 0.02f) continue;
			float x = cx + (float) Math.sin(sec * 0.9 + i * 1.7) * mw * 0.62f;
			float y = groundY - (float) ph * mh * 1.05f;
			int s = Math.round(base * (0.8f + 0.4f * (float) Math.sin(i * 2.3)));
			int col = RenderUtil.alpha(cm.menuColor(i, n, sec), a);
			Identifier id = Identifier.fromNamespaceAndPath("witch", "textures/gui/mp_" + cm.menuSprite(i) + ".png");
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().rotate((float) (Math.sin(sec * 0.7 + i) * 0.5));
			g.blit(pipe, id, -s / 2, -s / 2, 0f, 0f, s, s, 32, 32, 32, 32, col);
			g.pose().popMatrix();
		}
	}
}
