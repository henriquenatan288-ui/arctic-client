package com.witch.module;

import com.witch.setting.IntSetting;

/** Golpe/quebrar em primeira pessoa basico: o item desce e sobe em linha reta, sem puxar para o lado nem girar. */
public class StraightSwingModule extends Module {
	public static StraightSwingModule instance;
	public static volatile boolean on;

	public final IntSetting dist = add(new IntSetting("Distancia (1 curto .. 10 longo)", 1, 10, 5, ""));
	public final IntSetting tilt = add(new IntSetting("Inclinacao para frente (0 so reto .. 40 graus)", 0, 40, 0, ""));

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
