package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.render.PlayersGalaxy;
import com.witch.setting.IntSetting;

/**
 * Shader de galaxia so na elytra: nas costas (voce, outros jogadores, armor stands e mobs) e o item (mao, chao, hotbar).
 * Funciona sozinho: nao precisa do Players Galaxy nem do Galaxy ligados, e tem estilo proprio.
 */
public class ElytraShaderModule extends Module {
	public static ElytraShaderModule instance;
	public static volatile boolean on;

	public final IntSetting target = add(new IntSetting("Alvo (1 eu, 2 jogadores, 3 todos: armor stand, mobs)", 1, 3, 3, ""));
	public final IntSetting style = add(new IntSetting("Estilo (1 ciano 2 roxo 3 fogo 4 vazio 5 aurora 6 rosa 7 arco-iris 8 plasma)", 1, 8, 1, ""));
	public final IntSetting relief = add(new IntSetting("Relevo das asas (0 nenhum, 1 leve, 2 medio, 3 forte)", 0, 3, 1, ""));
	public final IntSetting item = add(new IntSetting("Item na mao, chao e hotbar (1 normal, 2 shader)", 1, 2, 2, ""));
	private String shown;

	public ElytraShaderModule() {
		super("Elytra Shader", Category.VISUALS);
		instance = this;
	}

	/** Corpo de jogador (isSelf = voce) com elytra shader? */
	public static boolean wantsPlayer(boolean isSelf) {
		var m = instance;
		if (m == null || !on) return false;
		int t = m.target.get();
		return t == 3 || t == 2 || (t == 1 && isSelf);
	}

	/** Armor stand, mob etc. com elytra shader? */
	public static boolean wantsOther() {
		var m = instance;
		return m != null && on && m.target.get() == 3;
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
			Toasts.push("Elytra Shader: " + st, PlayersGalaxy.statusOk);
		}
	}
}
