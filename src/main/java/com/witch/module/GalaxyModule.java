package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.render.GalaxyTools;
import com.witch.setting.IntSetting;

/**
 * Efeito galaxia nas ferramentas de diamante e netherite. Desligado = texturas normais do client.
 * Modo 1 (animada): textura animada pela troca de modelo, sem shader; a ferramenta nunca some.
 * Modo 2 (shader): shader proprio, camadas de estrelas em espaco de tela (precisa compilar no aparelho).
 */
public class GalaxyModule extends Module {
	public static GalaxyModule instance;
	/** Lido pelo render de itens a cada frame; mantido em dia por onEnable/onDisable. */
	public static volatile boolean on;

	public final IntSetting mode = add(new IntSetting("Modo (1 animada, 2 shader)", 1, 2, 1, ""));
	private String shown;
	private int lastMode = -1;

	public GalaxyModule() {
		super("Galaxy", Category.VISUALS);
		instance = this;
	}

	@Override
	protected void onEnable() {
		on = true;
		shown = null;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		if (!on) return;
		int m = mode.get();
		if (m != lastMode) { // trocou o modo: pede um novo aviso de estado
			lastMode = m;
			shown = null;
			GalaxyTools.status = null;
		}
		String st = GalaxyTools.status;
		if (st != null && !st.equals(shown)) {
			shown = st;
			Toasts.push("Galaxy: " + st, GalaxyTools.statusOk);
		}
	}
}
