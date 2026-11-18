package com.witch.module;

/** Preset: liga o filtro (preset sombrio) e a poeira ambiente de uma vez (e desliga ao desativar). */
public class WitchThemeModule extends Module {
	private static final String[] PARTS = {"Filter", "Ambient Dust"};

	public WitchThemeModule() {
		super("Witch Theme", Category.VISUALS);
	}

	private static void set(boolean on) {
		for (Module m : ModuleManager.all()) {
			for (String n : PARTS) {
				if (m.name.equals(n)) m.setEnabled(on);
			}
		}
	}

	@Override
	protected void onEnable() {
		if (FilterModule.instance != null) FilterModule.instance.use(4, 2); // sombrio, medio
		set(true);
	}

	@Override
	protected void onDisable() {
		set(false);
	}
}
