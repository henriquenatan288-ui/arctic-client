package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.SkyRenderState;

/** Ceu com cor de sua escolha e mais estrelas (usa o estado de render do ceu, sem shader). */
public class SkyModule extends Module {
	private static SkyModule instance;

	private final ColorSetting color = add(new ColorSetting("Color", 0));
	private final IntSetting mode = add(new IntSetting("Mode (1 cor, 2 plasma)", 1, 2, 2, ""));
	private final IntSetting plasmaGlow = add(new IntSetting("Plasma intensity", 20, 100, 85, "%"));
	private final IntSetting tint = add(new IntSetting("Tint", 0, 100, 55, "%"));
	private final IntSetting stars = add(new IntSetting("Stars", 0, 100, 70, "%"));

	public SkyModule() {
		super("Sky", Category.VISUALS);
		instance = this;
		color.setHsv(0.57f, 0.55f, 1f); // azul gelo (padrao do plasma)
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1f - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1f - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1f - t) + (b & 0xFF) * t);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	/** Cor escura de fundo do ceu/neblina, derivada da cor escolhida. */
	private static int plasmaBase() {
		int c = instance.color.get();
		int r = (int) (((c >> 16) & 0xFF) * 0.07f + 1), g = (int) (((c >> 8) & 0xFF) * 0.07f + 3), b = (int) ((c & 0xFF) * 0.07f + 6);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** ARGB para o shader de plasma (rgb = cor, a = intensidade), ou 0 se o modo plasma nao esta ativo. */
	public static int plasmaColor() {
		if (instance == null || !instance.isEnabled() || instance.mode.get() != 2) return 0;
		int a = Math.max(1, Math.min(255, Math.round(instance.plasmaGlow.get() * 2.55f)));
		return (a << 24) | (instance.color.get() & 0xFFFFFF);
	}

	/** Chamado pelo SkyRendererMixin depois do jogo preencher o estado do ceu. */
	public static void apply(SkyRenderState s) {
		if (instance == null || !instance.isEnabled() || s == null) return;
		if (instance.mode.get() == 2) s.skyColor = mix(s.skyColor, plasmaBase(), 0.95f);
		else s.skyColor = mix(s.skyColor, instance.color.get(), instance.tint.get() / 100f);
		float st = instance.stars.get() / 100f;
		if (st > s.starBrightness) s.starBrightness = st;
	}

	/** Tinge tambem a cor da nevoa (o horizonte e quase todo o ceu visivel): sem isso so o topo mudava de cor. */
	public static void applyFog(FogData d) {
		if (instance == null || !instance.isEnabled() || d == null || d.color == null) return;
		boolean pl = instance.mode.get() == 2;
		float t = pl ? 0.95f : instance.tint.get() / 100f;
		if (t <= 0f) return;
		int c = pl ? plasmaBase() : instance.color.get();
		d.color.x = d.color.x * (1f - t) + ((c >> 16) & 0xFF) / 255f * t;
		d.color.y = d.color.y * (1f - t) + ((c >> 8) & 0xFF) / 255f * t;
		d.color.z = d.color.z * (1f - t) + (c & 0xFF) / 255f * t;
	}
}
