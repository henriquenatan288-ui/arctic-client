package com.witch.module;

import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Frame Fix: contra o "engasgo" em que o FPS parece cair para ~20 e a taxa de quadros fica irregular
 * (visto em alguns launchers de celular e PCs). Hipotese principal: VSync ligado em tela que nao bate com o
 * tempo de quadro faz o jogo pular para a metade/um terco da taxa. O modulo desliga o VSync e trava um
 * limite de FPS estavel. Devolve os valores originais ao desligar.
 */
public class FrameFixModule extends Module {
	public final IntSetting vsyncOff = add(new IntSetting("VSync desligado (0/1)", 0, 1, 1, ""));
	public final IntSetting cap = add(new IntSetting("Limite de FPS (1 60 2 90 3 120 4 144 5 240 6 livre)", 1, 6, 3, ""));

	private static final int[] CAPS = {60, 90, 120, 144, 240, 260};

	private boolean saved;
	private boolean origVsync;
	private int origCap;
	private int tick;

	public FrameFixModule() {
		super("Frame Fix", Category.UTILITY);
	}

	@Override
	public void onTick() {
		Options o = Minecraft.getInstance().options;
		if (o == null) return;
		if (!saved) {
			origVsync = o.enableVsync().get();
			origCap = o.framerateLimit().get();
			saved = true;
			tick = 0;
		}
		if (tick++ % 20 != 0) return; // reaplica a cada segundo, caso outra coisa mude
		if (vsyncOff.get() == 1 && o.enableVsync().get()) o.enableVsync().set(false);
		int want = CAPS[Math.max(0, Math.min(CAPS.length - 1, cap.get() - 1))];
		if (o.framerateLimit().get() != want) o.framerateLimit().set(want);
	}

	@Override
	protected void onDisable() {
		Options o = Minecraft.getInstance().options;
		if (o != null && saved) {
			o.enableVsync().set(origVsync);
			o.framerateLimit().set(origCap);
		}
		saved = false;
	}
}
