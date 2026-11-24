package com.witch.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Avisos pequenos no canto superior direito quando um modulo liga/desliga. */
public final class Toasts {
	private Toasts() {}

	private record Toast(String name, boolean on, long at) {}

	private static final List<Toast> LIST = new ArrayList<>();
	private static final long LIFE = 1900L;
	private static final long IN = 220L;
	private static final long OUT = 350L;

	public static void push(String name, boolean on) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) return; // carregando config / fora do mundo
		LIST.removeIf(t -> t.name.equals(name));
		LIST.add(new Toast(name, on, System.currentTimeMillis()));
		while (LIST.size() > 4) LIST.remove(0);
	}

	public static void draw(GuiGraphicsExtractor g) {
		if (LIST.isEmpty()) return;
		long now = System.currentTimeMillis();
		LIST.removeIf(t -> now - t.at > LIFE);
		int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int y = Hud.rightTop();
		int bh = Hud.rowH();
		for (int i = LIST.size() - 1; i >= 0; i--) {
			Toast t = LIST.get(i);
			long age = now - t.at;
			float in = Math.min(1f, age / (float) IN);
			float out = age > LIFE - OUT ? (LIFE - age) / (float) OUT : 1f;
			float e = 1f - (1f - in) * (1f - in);
			String state = t.on ? "ON" : "OFF";
			int bw = Hud.tw(t.name) + 6 + Hud.tw(state) + 18;
			int x = sw - 5 - bw + Math.round((1f - e) * (bw + 8));
			float a = out;
			Hud.pill(g, x, y, bw, bh, a);
			Hud.dot(g, x + 6, y + bh / 2, RenderUtil.alpha(t.on ? Theme.accent() | 0xFF000000 : 0xFF5A5A60, a));
			int ty = y + (bh - Hud.th()) / 2;
			Hud.text(g, t.name, x + 12, ty, RenderUtil.alpha(Hud.WHITE, a));
			Hud.text(g, state, x + 12 + Hud.tw(t.name) + 6, ty, RenderUtil.alpha(t.on ? 0xFFB5B9C0 : 0xFF6A6A70, a));
			y += bh + 2;
		}
	}
}
