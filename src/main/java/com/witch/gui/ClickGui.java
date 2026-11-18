package com.witch.gui;

import com.witch.module.Module;
import net.minecraft.client.gui.screens.Screen;

/** Escolhe o estilo do ClickGUI (GUI Settings): 1 = Witch (abas e barras), 2 = janelas por categoria. */
public final class ClickGui {
	private ClickGui() {}

	public static Screen create() {
		return com.witch.module.GuiSettingsModule.windowStyle() ? new MeteorGuiScreen() : new ClickGuiScreen();
	}

	public static Screen create(Module m) {
		return com.witch.module.GuiSettingsModule.windowStyle() ? new MeteorGuiScreen(m) : new ClickGuiScreen(m);
	}

	public static boolean isClickGui(Screen s) {
		return s instanceof ClickGuiScreen || s instanceof MeteorGuiScreen;
	}
}
