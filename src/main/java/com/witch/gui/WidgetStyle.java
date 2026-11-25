package com.witch.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/** Estilo Arctic dos botoes/controles do jogo nas telas de pausa e de opcoes (vidro azul-marinho, contorno azul-bebe). */
public final class WidgetStyle {
	private WidgetStyle() {}

	public static boolean applies() {
		try {
			if (!com.witch.Edition.visuals()) return false;
			Screen s = Minecraft.getInstance().gui.screen();
			if (s == null) return false;
			return s instanceof PauseScreen || s.getClass().getName().startsWith("net.minecraft.client.gui.screens.options");
		} catch (Throwable t) {
			return false;
		}
	}
}
