package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;

/**
 * Contorno do bloco que voce esta olhando, na cor do tema (ou outra). Troca so a cor e a espessura da linha do jogo,
 * entao continua respeitando paredes (nao aparece atras de blocos).
 */
public class BlockOutlineModule extends Module {
	private static BlockOutlineModule instance;

	private final IntSetting mode = add(new IntSetting("Cor (1 tema 2 escolhida 3 arco-iris 4 tema pulsando)", 1, 4, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Cor escolhida (modo 2)", 1));
	private final IntSetting width = add(new IntSetting("Espessura", 1, 10, 4, ""));
	private final IntSetting alpha = add(new IntSetting("Opacidade", 2, 10, 10, ""));
	private final IntSetting speed = add(new IntSetting("Velocidade (modos 3/4)", 1, 10, 4, ""));

	public BlockOutlineModule() {
		super("Block Outline", Category.EFFECTS);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** Cor ARGB do contorno neste instante. */
	public static int argb() {
		var m = instance;
		int base;
		double t = System.currentTimeMillis() / 1000.0 * (0.15 + m.speed.get() * 0.1);
		float a = m.alpha.get() / 10f;
		switch (m.mode.get()) {
			case 2 -> base = m.color.get();
			case 3 -> base = ColorSetting.hsvToRgb((float) (t % 1.0), 0.75f, 1f);
			case 4 -> {
				base = com.witch.gui.Theme.accent();
				a *= 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(t * 6.0));
			}
			default -> base = com.witch.gui.Theme.accent();
		}
		return (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24) | (base & 0xFFFFFF);
	}

	public static float lineWidth() {
		return 1f + instance.width.get() * 0.7f;
	}
}
