package com.witch.gui;

import com.witch.module.Module;
import net.minecraft.client.gui.screens.Screen;

/** Escolhe o estilo do ClickGUI (GUI Settings): 1 = Witch (abas e barras), 2 = janelas por categoria. */
public final class ClickGui {
	private ClickGui() {}

	public static Screen create() {
		int st = com.witch.module.GuiSettingsModule.style();
		return st == 3 ? new ArcticGuiScreen() : st == 2 ? new MeteorGuiScreen() : new ClickGuiScreen();
	}

	public static Screen create(Module m) {
		int st = com.witch.module.GuiSettingsModule.style();
		return st == 3 ? new ArcticGuiScreen(m) : st == 2 ? new MeteorGuiScreen(m) : new ClickGuiScreen(m);
	}

	public static boolean isClickGui(Screen s) {
		return s instanceof ClickGuiScreen || s instanceof MeteorGuiScreen || s instanceof ArcticGuiScreen;
	}
}
