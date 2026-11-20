package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.render.FxShapes;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/** Ao quebrar um bloco abaixo dos seus pes (por padrao): clarao, ondas com brilho, raios radiais e faiscas que saltam. */
public class BreakWaveModule extends Module implements EspSource {
	private static final double DURATION = 1.3; // segundos
	private static final int MAX_WAVES = 3;

	private final IntSetting radius = add(new IntSetting("Radius", 2, 10, 4, " blocks"));
	private final IntSetting rings = add(new IntSetting("Rings", 1, 4, 2, ""));
	private final IntSetting downOnly = add(new IntSetting("Down only (0/1)", 0, 1, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Color", 1));

	private final List<double[]> waves = new ArrayList<>(); // {x, y, z, inicioSeg}
	private BlockPos last = null;
	private int lastFeetY = 0;

	public BreakWaveModule() {
		super("Break Wave", Category.EFFECTS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onDisable() {
		waves.clear();
		last = null;
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null) return;

		if (last != null && level.getBlockState(last).isAir()) {
			boolean down = downOnly.get() == 0 || (last.getY() < lastFeetY
					&& Math.abs(last.getX() + 0.5 - p.getX()) < 1.6 && Math.abs(last.getZ() + 0.5 - p.getZ()) < 1.6);
			if (down) {
				waves.add(new double[]{last.getX() + 0.5, last.getY() + 1.02, last.getZ() + 0.5, now()});
				if (waves.size() > MAX_WAVES) waves.remove(0);
			}
			last = null;
		}
		if (mc.hitResult instanceof BlockHitResult bhr && mc.options.keyAttack.isDown()) {
			BlockPos pos = bhr.getBlockPos();
			if (!level.getBlockState(pos).isAir()) {
				last = pos.immutable();
				lastFeetY = p.blockPosition().getY();
			}
		}
	}

	private static final class RenderColor {
		static int mix(int a, int b, float t) {
			int r = Math.round(((a >> 16) & 0xFF) * (1f - t) + ((b >> 16) & 0xFF) * t);
			int g = Math.round(((a >> 8) & 0xFF) * (1f - t) + ((b >> 8) & 0xFF) * t);
			int bl = Math.round((a & 0xFF) * (1f - t) + (b & 0xFF) * t);
			return 0xFF000000 | (r << 16) | (g << 8) | bl;
		}
	}

	private static double now() {
		return System.nanoTime() / 1_000_000_000.0;
	}

	@Override
	public void collect(Esp esp) {
		double t = now();
		waves.removeIf(w -> t - w[3] > DURATION + 0.6);
		int nRings = rings.get();
		int base = color.get();
		int white = 0xFFFFFFFF;
		for (double[] w : waves) {
			double age = t - w[3];
			double prog = Math.min(1.0, age / DURATION);
			double x = w[0], y = w[1], z = w[2];
			// clarao no ponto da quebra
			float flash = (float) Math.max(0.0, 1.0 - age * 5.0);
			if (flash > 0f) FxShapes.disk(esp, x, y, z, 0.9 + 0.5 * (1.0 - flash), FxShapes.mix(base, white, 0.7f), 0.55f * flash, 0.05f * flash);
			for (int k = 0; k < nRings; k++) {
				double pr = prog - k * 0.18;
				if (pr <= 0 || pr >= 1) continue;
				double r = 0.3 + (radius.get() - 0.3) * (1.0 - Math.pow(1.0 - pr, 3.0));
				float fade = (float) Math.pow(1.0 - pr, 1.5) * (1f - 0.3f * k);
				int col = FxShapes.mix(FxShapes.mix(base, white, 0.55f), base, (float) pr);
				double yy = y + pr * 0.14;
				FxShapes.glowRing(esp, x, yy, z, r, 0.25 + 0.12 * r * 0.3, col, fade);
				if (k == 0) {
					// raios radiais saindo da onda principal, girando devagar
					FxShapes.spokes(esp, x, yy + 0.01, z, r * 0.45, r * 0.98, 0.055, 14, pr * 1.2, col, fade * 0.8f);
				}
			}
			// faiscas: pedacinhos que saltam para fora e caem
			if (age < 1.0) {
				float fa = (float) Math.pow(1.0 - age, 1.2);
				int sc = FxShapes.mix(base, white, 0.5f);
				for (int i = 0; i < 10; i++) {
					double ang = i * 2.399963 + w[3] * 7.0; // angulo "dourado" pseudo-aleatorio estavel
					double sp = 1.3 + (i % 4) * 0.55;
					double dist = sp * age * 1.1;
					double h = 0.35 + (i % 3) * 0.12;
					double yv = h * 3.2 * age - 4.2 * age * age;
					if (yv < -0.02) continue;
					double px = x + Math.cos(ang) * dist, pz = z + Math.sin(ang) * dist, py = y + 0.06 + yv;
					double s = 0.035 + 0.012 * (i % 3);
					esp.solid(px - s, py - s, pz - s, px + s, py + s, pz + s, sc, fa * 0.9f);
				}
			}
		}
	}
}
