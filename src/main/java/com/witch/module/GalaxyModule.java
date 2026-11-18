package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.render.GalaxyTools;
import com.witch.setting.IntSetting;

/**
 * Efeito galaxia nas ferramentas de diamante e netherite. Desligado = texturas normais do client.
 * Modo 1 (animada): textura animada pela troca de modelo, sem shader; a ferramenta nunca some.
 * Modo 2 (shader): shader proprio, camadas de estrelas em espaco de tela (precisa compilar no aparelho).
 * Itens extras (totem, maca dourada, elytra, armadura, ou todos) sempre usam o shader, em qualquer modo.
 */
public class GalaxyModule extends Module {
	public static GalaxyModule instance;
	/** Lido pelo render de itens a cada frame; mantido em dia por onEnable/onDisable. */
	public static volatile boolean on;

	public final IntSetting mode = add(new IntSetting("Modo (1 animada, 2 shader)", 1, 2, 1, ""));
	public final IntSetting scope = add(new IntSetting("Itens (1 ferramentas, 2 +totem/maca/elytra/armadura, 3 todos)", 1, 3, 2, ""));
	public final IntSetting whole = add(new IntSetting("Ferramenta (1 so lamina/cabeca, 2 inteira) - modo 2", 1, 2, 2, ""));
	public final IntSetting style = add(new IntSetting("Estilo (1 ciano 2 roxo 3 fogo 4 vazio 5 aurora 6 rosa 7 arco-iris)", 1, 7, 1, ""));
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
		int m = mode.get() * 100 + style.get() * 10 + scope.get() + whole.get() * 1000;
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
