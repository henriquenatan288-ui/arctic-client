package com.witch.module;

import com.witch.gui.Toasts;
import com.witch.render.GlowItems;
import com.witch.setting.ColorSetting;

/** Linha brilhante em volta dos itens (na mao, no chao e em quadros). */
public class GlowItemsModule extends Module {
	public static GlowItemsModule instance;
	public static volatile boolean on;

	public final ColorSetting color = add(new ColorSetting("Color", 1));
	private String shown;

	public GlowItemsModule() {
		super("Glow Items", Category.VISUALS);
		instance = this;
	}

	@Override
	protected void onEnable() {
		on = true;
		shown = null;
		GlowItems.status = null;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		if (!on) return;
		String st = GlowItems.status;
		if (st != null && !st.equals(shown)) {
			shown = st;
			Toasts.push("Glow Items: " + st, GlowItems.statusOk);
		}
	}
}
