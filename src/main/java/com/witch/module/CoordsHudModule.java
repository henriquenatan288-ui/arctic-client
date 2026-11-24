package com.witch.module;

import com.witch.gui.FontUtil;
import com.witch.gui.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Coordenadas, direcao e velocidade no canto inferior esquerdo. */
public class CoordsHudModule extends Module {
	private static CoordsHudModule instance;
	private static final String[] DIR = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	public CoordsHudModule() {
		super("Coords HUD", Category.UTILITY);
		instance = this;
	}

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null) return;
		String dir = DIR[Math.floorMod(Math.round(p.getYRot() / 45f), 8)];
		var v = p.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
		String a = p.getBlockX() + "  " + p.getBlockY() + "  " + p.getBlockZ();
		String b = dir + "  " + String.format("%.1f", speed) + " b/s";
		int sh = mc.getWindow().getGuiScaledHeight();
		int h = com.witch.gui.Hud.rowH();
		int gap = 9;
		int bw = com.witch.gui.Hud.tw(a) + gap + com.witch.gui.Hud.tw(b) + 12;
		int x0 = 5 + com.witch.gui.HudLayout.dx("coords");
		int y = sh - 4 - h + com.witch.gui.HudLayout.dy("coords");
		int ty = y + (h - com.witch.gui.Hud.th()) / 2;
		com.witch.gui.Hud.pill(g, x0, y, bw, h, 1f);
		com.witch.gui.Hud.text(g, a, x0 + 6, ty, com.witch.gui.Hud.WHITE);
		com.witch.gui.Hud.text(g, b, x0 + 6 + com.witch.gui.Hud.tw(a) + gap, ty, com.witch.gui.Hud.DIM);
		com.witch.gui.HudLayout.report("coords", x0, y, bw, h);
	}
}
