package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.render.PlayersGalaxy;
import com.witch.setting.IntSetting;

/** Jogadores (voce e os outros) cobertos pelo shader de galaxia, no estilo do modo 2 do Galaxy. */
public class PlayersGalaxyModule extends Module {
	public static PlayersGalaxyModule instance;
	public static volatile boolean on;

	public final IntSetting target = add(new IntSetting("Alvo (1 eu, 2 outros, 3 todos)", 1, 3, 3, ""));
	public final IntSetting style = add(new IntSetting("Estilo (1 ciano 2 roxo 3 fogo 4 vazio 5 aurora 6 rosa 7 arco-iris 8 plasma)", 1, 8, 1, ""));
	public final IntSetting relief = add(new IntSetting("Relevo da skin (0 nenhum, 1 leve, 2 medio, 3 forte)", 0, 3, 1, ""));
	public final IntSetting armor = add(new IntSetting("Armadura (1 normal, 2 so jogadores, 3 todos: armor stand, mobs)", 1, 3, 3, ""));
	private String shown;

	public PlayersGalaxyModule() {
		super("Players Galaxy", Category.VISUALS);
		instance = this;
	}

	@Override
	protected void onEnable() {
		on = true;
		shown = null;
		PlayersGalaxy.status = null;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		if (!on) return;
		String st = PlayersGalaxy.status;
		if (st != null && !st.equals(shown)) {
			shown = st;
			Toasts.push("Players Galaxy: " + st, PlayersGalaxy.statusOk);
		}
	}
}
