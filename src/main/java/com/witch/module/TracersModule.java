package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.IntSetting;
import com.witch.setting.OreSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Tracers baseados no Tracers.java do Meteor (linhas do centro da tela, alvo cabeca/corpo/pes, stem,
 * cor por distancia, ignore self/invisiveis, max distance). Aqui so desenham para os alvos do
 * Player ESP, Farm ESP, Storage ESP e X-Ray, que precisam estar ligados.
 */
public class TracersModule extends Module implements EspSource {
	/** Alpha padrao das cores do Meteor (127/255). */
	private static final float ALPHA = 127f / 255f;

	private final IntSetting range = add(new IntSetting("Max distance", 16, 256, 256, ""));
	private final IntSetting maxTargets = add(new IntSetting("Max targets", 10, 300, 80, ""));
	private final IntSetting height = add(new IntSetting("Height", 0, 100, 50, "%"));
	private final IntSetting thickness = add(new IntSetting("Thickness", 1, 8, 2, ""));
	private final IntSetting matchEsp = add(new IntSetting("Match ESP color (0/1)", 0, 1, 1, ""));
	private final IntSetting ignoreSelf = add(new IntSetting("Ignore self (0/1)", 0, 1, 0, ""));
	private final IntSetting showInvis = add(new IntSetting("Show invisible (0/1)", 0, 1, 1, ""));
	private final IntSetting origin = add(new IntSetting("Origem (1 mira, 2 base da tela)", 1, 2, 2, ""));
	private final IntSetting fade = add(new IntSetting("Esmaecer com a distancia (0/1)", 0, 1, 1, ""));
	private final IntSetting markers = add(new IntSetting("Marcador e distancia no alvo (0/1)", 0, 1, 1, ""));
	private final IntSetting edge = add(new IntSetting("Indicador na borda da tela (0/1)", 0, 1, 1, ""));
	private final OreSetting distColor = add(new OreSetting("Distance colors", false, 2));
	private final OreSetting stem = add(new OreSetting("Stem", true, 7));
	private final OreSetting players = add(new OreSetting("Player ESP", true, 5));
	private final OreSetting farm = add(new OreSetting("Farm ESP", true, 2));
	private final OreSetting storage = add(new OreSetting("Storage ESP", true, 3));
	private final OreSetting xray = add(new OreSetting("X-Ray", true, 1));

	public TracersModule() {
		super("Tracers", Category.VISUALS);
		instance = this;
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	/** Igual ao EntityUtils.getColorFromDistance do Meteor: perto = vermelho, meio = amarelo, longe (60+) = verde. */
	private static int distanceColor(double dist) {
		double percent = dist / 60.0;
		if (percent < 0 || percent > 1) return 0xFF00FF00;
		int r, g;
		if (percent < 0.5) {
			r = 255;
			g = (int) (255 * percent / 0.5);
		} else {
			g = 255;
			r = 255 - (int) (255 * (percent - 0.5) / 0.5);
		}
		return 0xFF000000 | (r << 16) | (g << 8);
	}

	/** Um alvo candidato: ponto da linha, pes/altura (stem), distancia e cor. */
	private static final class Tgt {
		double x, ty, z, feetY, h, d;
		int color;
		boolean entity;
	}

	/** Os mais proximos do ultimo frame, para o marcador/distancia e os indicadores de borda no HUD. */
	private volatile java.util.List<Tgt> shown = java.util.List.of();
	private static TracersModule instance;

	private float fadeOf(double d) {
		if (fade.get() == 0) return 1f;
		double max = Math.max(32.0, range.get());
		double t = Math.min(1.0, Math.max(0.0, (d - 12.0) / (max - 12.0)));
		t = t * t * (3.0 - 2.0 * t);
		return (float) (1.0 - 0.72 * t); // perto = forte, no limite = 28%
	}

	@Override
	public void collect(Esp esp) {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		var me = mc.player;
		if (level == null || me == null) return;

		float pt = esp.partial();
		double mx = Mth.lerp(pt, me.xOld, me.getX());
		double my = Mth.lerp(pt, me.yOld, me.getY());
		double mz = Mth.lerp(pt, me.zOld, me.getZ());
		double max = range.get();
		double hp = height.get() / 100.0;
		java.util.List<Tgt> all = new java.util.ArrayList<>();

		if (players.enabled() && isOn(PlayerEspModule.class)) {
			int base = matchEsp.get() == 1 ? PlayerEspModule.currentColor() : players.color();
			boolean freecam = FreecamModule.active();
			for (Player e : level.players()) {
				if (e.isSpectator()) continue;
				if (e == me && (!freecam || ignoreSelf.get() == 1)) continue;
				if (showInvis.get() == 0 && e.isInvisible()) continue;
				double x = Mth.lerp(pt, e.xOld, e.getX());
				double y = Mth.lerp(pt, e.yOld, e.getY());
				double z = Mth.lerp(pt, e.zOld, e.getZ());
				double dx = x - mx, dy = y - my, dz = z - mz;
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d > max) continue;
				Tgt t = new Tgt();
				t.x = x; t.z = z; t.feetY = y; t.h = e.getBbHeight(); t.ty = y + t.h * hp; t.d = d;
				t.color = distColor.enabled() ? distanceColor(d) : base;
				t.entity = true;
				all.add(t);
			}
		}
		if (farm.enabled() && isOn(FarmEspModule.class)) blocks(all, FarmEspModule.blocks(), farm.color(), mx, my, mz);
		if (storage.enabled() && isOn(StorageEspModule.class)) blocks(all, StorageEspModule.blocks(), storage.color(), mx, my, mz);
		if (xray.enabled() && isOn(XRayModule.class)) blocks(all, XRayModule.blocks(), xray.color(), mx, my, mz);

		// so os N mais proximos de qualquer tipo (antes a lista dos blocos vinha sem ordem e o limite cortava no escuro)
		all.sort((p, q) -> Double.compare(p.d, q.d));
		int n = Math.min(all.size(), maxTargets.get());
		boolean dc = distColor.enabled();
		float th = thickness.get() * 1.25f;
		esp.originBottom(origin.get() == 2);
		for (int i = 0; i < n; i++) {
			Tgt t = all.get(i);
			float f = fadeOf(t.d);
			float alpha = (dc ? 1f : ALPHA) * f;
			esp.tracer(t.x, t.ty, t.z, t.color, alpha, th);
			if (t.entity && stem.enabled()) esp.thinLine(t.x, t.feetY, t.z, t.x, t.feetY + t.h, t.z, t.color, alpha, th);
			if (markers.get() == 1 && i < 24) {
				double s = Math.max(0.12, t.d * 0.008);
				esp.box(t.x - s, t.ty - s, t.z - s, t.x + s, t.ty + s, t.z + s, t.color, 0.6f * f);
			}
		}
		esp.originBottom(false);
		shown = n == 0 ? java.util.List.of() : new java.util.ArrayList<>(all.subList(0, Math.min(n, 24)));
	}

	/** Distancia ao lado de cada alvo e setinha na borda da tela para os que estao fora da vista ou atras de voce. */
	public static void drawHud(net.minecraft.client.gui.GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		var list = instance.shown;
		if (list.isEmpty()) return;
		boolean labels = instance.markers.get() == 1, edge = instance.edge.get() == 1;
		if (!labels && !edge) return;
		Minecraft mc = Minecraft.getInstance();
		int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
		int edgeShown = 0;
		for (Tgt t : list) {
			float[] sc = com.witch.render.EspRenderer.toScreen(t.x, t.ty, t.z, gw, gh);
			if (sc == null) return;
			float f = instance.fadeOf(t.d);
			String txt = Math.round(t.d) + "m";
			int col = com.witch.gui.RenderUtil.alpha(t.color | 0xFF000000, Math.max(0.35f, f));
			if (sc[2] == 0f) {
				if (!labels) continue;
				g.pose().pushMatrix();
				g.pose().translate(sc[0] + 5f, sc[1] - 9f);
				g.pose().scale(0.7f, 0.7f);
				com.witch.gui.FontUtil.draw(g, txt, 0, 0, col);
				g.pose().popMatrix();
			} else if (edge && edgeShown < 8) {
				edgeShown++;
				float cx = gw / 2f, cy = gh / 2f, m = 16f;
				float dx = sc[3], dy = sc[4];
				float tx = Math.abs(dx) < 1e-4f ? 1e9f : (cx - m) / Math.abs(dx);
				float ty = Math.abs(dy) < 1e-4f ? 1e9f : (cy - m) / Math.abs(dy);
				float k = Math.min(tx, ty);
				int px = Math.round(cx + dx * k), py = Math.round(cy + dy * k);
				com.witch.gui.RenderUtil.glow(g, px, py, 9, com.witch.gui.RenderUtil.alpha(t.color | 0xFF000000, 0.35f * Math.max(0.5f, f)));
				// ponta apontando para fora da tela: quadrado girado na direcao do alvo
				g.pose().pushMatrix();
				g.pose().translate(px, py);
				g.pose().rotate((float) Math.atan2(dy, dx) + (float) Math.PI / 4f);
				g.fill(-3, -3, 3, 3, col);
				g.pose().popMatrix();
				g.pose().pushMatrix();
				g.pose().translate(px + (dx > 0 ? -22f : 8f), py - 3f);
				g.pose().scale(0.7f, 0.7f);
				com.witch.gui.FontUtil.draw(g, txt, 0, 0, col);
				g.pose().popMatrix();
			}
		}
	}

	private static boolean isOn(Class<? extends Module> c) {
		for (Module m : ModuleManager.all()) {
			if (c.isInstance(m)) return m.isEnabled();
		}
		return false;
	}

	private void blocks(java.util.List<Tgt> out, java.util.List<int[]> list, int base, double mx, double my, double mz) {
		boolean dc = distColor.enabled();
		boolean match = matchEsp.get() == 1;
		double hp = height.get() / 100.0;
		for (int[] b : list) {
			double dx = b[0] + 0.5 - mx, dy = b[1] + 0.5 - my, dz = b[2] + 0.5 - mz;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > range.get()) continue;
			Tgt t = new Tgt();
			t.x = b[0] + 0.5; t.ty = b[1] + hp; t.z = b[2] + 0.5; t.d = d;
			t.color = dc ? distanceColor(d) : (match && b.length > 3 ? b[3] : base);
			out.add(t);
		}
	}
}
