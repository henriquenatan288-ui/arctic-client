package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;

/** Ajustes do proprio ClickGUI (opacidade global e tooltips). Fica na lista como um modulo qualquer. */
public class GuiSettingsModule extends Module {
	private static GuiSettingsModule instance;

	private final ColorSetting accent = add(new ColorSetting("Accent", 7));
	private final IntSetting opacity = add(new IntSetting("Window opacity", 0, 100, 12, "%"));
	private final IntSetting menuOpacity = add(new IntSetting("Menu opacity", 5, 100, 100, "%"));
	private final IntSetting menuTheme = add(new IntSetting("Tema do menu (1 escuro, 2 claro)", 1, 2, 1, ""));
	private final IntSetting style = add(new IntSetting("Estilo do ClickGUI (1 abas e barras, 2 janelas, 3 Arctic)", 1, 3, 3, ""));
	private final IntSetting tooltips = add(new IntSetting("Tooltips (0/1)", 0, 1, 1, ""));
	private final IntSetting guiShader = add(new IntSetting("Shader do ClickGUI (0/1)", 0, 1, 1, ""));
	private final IntSetting glass = add(new IntSetting("Liquid glass (0/1)", 0, 1, 0, ""));
	private final IntSetting sounds = add(new IntSetting("Module sounds (0/1)", 0, 1, 1, ""));
	private final IntSetting soundStyle = add(new IntSetting("Sound style (1 soft, 2 digital, 3 crystal)", 1, 3, 1, ""));
	private final IntSetting soundVolume = add(new IntSetting("Sound volume", 1, 100, 60, "%"));

	public GuiSettingsModule() {
		super("GUI Settings", Category.UTILITY);
		instance = this;
	}

	/** 0.1 a 1.0: opacidade do fundo das janelas. */
	public static float opacity() {
		return instance == null ? 0.12f : instance.opacity.get() / 100f;
	}

	/** 0.05 a 1.0: opacidade da moldura e dos botoes do ClickGUI (texto e icones nao mudam). */
	public static float menuOpacity() {
		return instance == null ? 1f : instance.menuOpacity.get() / 100f;
	}

	/** Cor de destaque (padrao branco). */
	public static int accent() {
		return instance == null ? 0xFFFFFFFF : instance.accent.get();
	}

	/** true = telas de inicio/carregamento no tema claro. */
	public static boolean menuLight() {
		return instance != null && instance.menuTheme.get() == 2;
	}

	public static int style() {
		return instance == null ? 3 : instance.style.get();
	}

	/** true = ClickGUI em janelas por categoria. */
	public static boolean windowStyle() {
		return instance == null || instance.style.get() == 2;
	}

	public static boolean glow() {
		return false; // linhas brilhantes removidas
	}

	public static boolean tooltips() {
		return instance == null || instance.tooltips.get() == 1;
	}

	public static boolean guiShader() {
		return instance == null || instance.guiShader.get() == 1;
	}

	public static boolean glass() {
		return instance != null && instance.glass.get() == 1;
	}

	public static boolean sounds() {
		return instance == null || instance.sounds.get() == 1;
	}

	public static int soundStyle() {
		return instance == null ? 1 : instance.soundStyle.get();
	}

	public static float soundVolume() {
		return instance == null ? 0.6f : instance.soundVolume.get() / 100f;
	}
}
