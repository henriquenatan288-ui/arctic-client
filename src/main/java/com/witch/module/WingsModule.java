package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;

/** Asas nas costas do avatar: penas em leque que batem devagar (mais rapido andando/voando). Cosmetico. */
public class WingsModule extends Module {
	public static WingsModule instance;
	public static volatile boolean on;

	public final IntSetting target = add(new IntSetting("Alvo (1 eu, 2 todos os jogadores)", 1, 2, 1, ""));
	public final IntSetting look = add(new IntSetting("Visual (1 galaxy, 2 cor)", 1, 2, 2, ""));
	public final IntSetting style = add(new IntSetting("Estilo da galaxy (1 ciano 2 roxo 3 fogo 4 vazio 5 aurora 6 rosa 7 arco-iris 8 plasma)", 1, 8, 8, ""));
	public final ColorSetting color = add(new ColorSetting("Cor", 7));
	public final IntSetting size = add(new IntSetting("Tamanho", 1, 10, 5, ""));
	public final IntSetting flap = add(new IntSetting("Bater (0 parado .. 10 rapido)", 0, 10, 4, ""));
	public final IntSetting feathers = add(new IntSetting("Penas por camada", 3, 9, 6, ""));
	public final IntSetting layers = add(new IntSetting("Camadas", 1, 3, 2, ""));

	public WingsModule() {
		super("Wings", Category.AVATAR);
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

	public static boolean wants(boolean isSelf) {
		var m = instance;
		return m != null && on && (m.target.get() == 2 || isSelf);
	}
}
