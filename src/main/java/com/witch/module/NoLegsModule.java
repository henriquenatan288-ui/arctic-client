package com.witch.module;

import com.witch.setting.IntSetting;

/** Tira as pernas do modelo do jogador (as duas ou so uma). Cosmetico: so voce ve. */
public class NoLegsModule extends Module {
	public static NoLegsModule instance;
	public static volatile boolean on;

	public final IntSetting legs = add(new IntSetting("Pernas (1 nenhuma, 2 so esq, 3 so dir)", 1, 3, 2, ""));
	public final IntSetting target = add(new IntSetting("Alvo (1 eu, 2 todos)", 1, 2, 1, ""));

	public NoLegsModule() {
		super("No Legs", Category.AVATAR);
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

	/** 0 = nao mexe; 1 = sem pernas; 2 = so a esquerda sobra; 3 = so a direita sobra (para essa entidade). */
	public static int modeFor(boolean self) {
		var m = instance;
		if (m == null || !on) return 0;
		if (m.target.get() == 1 && !self) return 0;
		return m.legs.get();
	}
}
