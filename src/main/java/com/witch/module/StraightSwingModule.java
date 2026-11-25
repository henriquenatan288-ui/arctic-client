package com.witch.module;

import com.witch.setting.IntSetting;

/** Golpe/quebrar em primeira pessoa: o tapa normal (desce e volta), sem a curva lateral da mao e da ferramenta. */
public class StraightSwingModule extends Module {
	public static StraightSwingModule instance;
	public static volatile boolean on;

	public final IntSetting angle = add(new IntSetting("Forca do tapa (graus, 80 = normal)", 30, 110, 80, ""));

	public StraightSwingModule() {
		super("Straight Swing", Category.AVATAR);
		instance = this;
	}

	@Override
	protected void onEnable() {
		on = true;
	}

	@Override
	protected void onDisable() {
		on = false;
	}
}
