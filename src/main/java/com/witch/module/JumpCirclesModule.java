package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.render.FxShapes;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Onda de luz que se abre no chao quando voce pula: anel com brilho, halo, tracos de runa girando e eco interno. */
public class JumpCirclesModule extends Module implements EspSource {
	private static final long LIFE_MS = 1100L;
	private final ColorSetting color = add(new ColorSetting("Color", 0));
	private final IntSetting size = add(new IntSetting("Size", 1, 6, 3, ""));

	private final List<double[]> rings = new ArrayList<>();
	private boolean wasGround = true;

	public JumpCirclesModule() {
		super("Jump Circles", Category.EFFECTS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onDisable() {
		rings.clear();
	}

	@Override
	public void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		boolean ground = p.onGround();
		if (wasGround && !ground && p.getDeltaMovement().y > 0.2 && !p.isInWater() && !p.getAbilities().flying) {
			rings.add(new double[] {p.getX(), p.getY(), p.getZ(), System.currentTimeMillis()});
			if (rings.size() > 12) rings.remove(0);
		}
		wasGround = ground;
	}

	@Override
	public void collect(Esp esp) {
		long now = System.currentTimeMillis();
		rings.removeIf(r -> now - (long) r[3] > LIFE_MS);
		int c = color.get();
		for (double[] r : rings) {
			double k = (now - (long) r[3]) / (double) LIFE_MS;
			double e = 1.0 - Math.pow(1.0 - k, 3.0);
			float a = (float) Math.pow(1.0 - k, 1.3);
			double rad = 0.35 + e * size.get() * 1.6;
			double x = r[0], y = r[1] + 0.04, z = r[2];
			float flash = (float) Math.max(0.0, 1.0 - k * 4.0); // clarao no inicio
			FxShapes.disk(esp, x, y, z, rad, c, 0f, 0.28f * a + 0.35f * flash);
			FxShapes.glowRing(esp, x, y + 0.01, z, rad, 0.18 + 0.05 * size.get(), c, a);
			// eco interno, atrasado
			double ke = Math.max(0.0, k - 0.12) / 0.88;
			if (ke > 0.0) {
				double e2 = 1.0 - Math.pow(1.0 - ke, 3.0);
				FxShapes.glowRing(esp, x, y + 0.02, z, 0.25 + e2 * size.get() * 0.95, 0.12, c, (float) Math.pow(1.0 - ke, 1.5) * 0.7f);
			}
			// tracos de runa girando um pouco alem do anel
			FxShapes.dashes(esp, x, y + 0.03, z, rad + 0.22 + 0.04 * size.get(), 0.035, 12, k * 2.4, 0.42, FxShapes.mix(c, 0xFFFFFFFF, 0.35f), a * 0.85f);
		}
	}
}
