package com.witch.module;

/**
 * Efeito galaxia nas ferramentas de diamante e netherite (shader proprio, camadas de estrelas em espaco de tela).
 * Desligado = as texturas normais do client. Se o shader falhar no aparelho, o render cai sozinho para a textura normal.
 */
public class GalaxyModule extends Module {
	/** Lido pelo render de itens a cada frame; mantido em dia por onEnable/onDisable. */
	public static volatile boolean on;

	public GalaxyModule() {
		super("Galaxy", Category.VISUALS);
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
