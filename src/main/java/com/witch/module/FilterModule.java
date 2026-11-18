package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Filtro de cor de verdade: usa o sistema de post effect do jogo (o mesmo do creeper/blur), entao o grading
 * (tinta, saturacao, contraste, vinheta) e aplicado na imagem do mundo e o HUD continua intacto.
 * Os efeitos sao gerados por tools/gen_filter_effects.py (8 presets x 4 intensidades).
 */
public class FilterModule extends Module {
	public static FilterModule instance;

	public final IntSetting preset = add(new IntSetting("Preset (1 quente 2 frio 3 neon 4 sombrio 5 por do sol 6 menta 7 cinema 8 p&b)", 1, 8, 5, ""));
	public final IntSetting intensity = add(new IntSetting("Intensidade (1 leve ... 4 forte)", 1, 4, 3, ""));

	private Identifier applied;
	private boolean warned;

	public FilterModule() {
		super("Filter", Category.VISUALS);
		instance = this;
	}

	private static boolean ours(Identifier id) {
		return id != null && "witch".equals(id.getNamespace());
	}

	private static void apply(Identifier id) throws ReflectiveOperationException {
		var m = net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("setPostEffect", Identifier.class);
		m.setAccessible(true);
		m.invoke(Minecraft.getInstance().gameRenderer, id);
	}

	@Override
	public void onTick() {
		var gr = Minecraft.getInstance().gameRenderer;
		if (gr == null) return;
		Identifier cur = gr.currentPostEffect();
		if (cur != null && !ours(cur)) return; // visao de creeper/aranha: deixa o jogo mandar
		Identifier want = Identifier.fromNamespaceAndPath("witch", "filter_" + preset.get() + "_" + intensity.get());
		if (want.equals(cur) && want.equals(applied)) return;
		try {
			apply(want);
			applied = want;
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				Toasts.push("Filter: nao consegui ativar o efeito", false);
				System.out.println("[Witch] filter: " + t);
			}
		}
	}

	@Override
	protected void onDisable() {
		applied = null;
		try {
			var gr = Minecraft.getInstance().gameRenderer;
			if (gr != null && ours(gr.currentPostEffect())) gr.clearPostEffect();
		} catch (Throwable ignored) {
		}
	}

	/** Usado pelo Witch Theme: liga o filtro ja no preset escolhido. */
	public void use(int p, int level) {
		preset.set(p);
		intensity.set(level);
	}
}
