package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.render.FxShapes;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Chuva melhor: ondas que se abrem no chao (e na agua) onde as gotas caem, so em lugares a ceu aberto e que voce enxerga.
 * Complemento do Mirror Floor: depois as ondas passam a distorcer o reflexo.
 */
public class BetterRainModule extends Module implements EspSource {
	private static final long LIFE_MS = 900L;
	private static final int MAX = 130;

	private final ColorSetting color = add(new ColorSetting("Color", 1));
	private final IntSetting density = add(new IntSetting("Density", 1, 10, 4, ""));
	private final IntSetting size = add(new IntSetting("Size", 1, 10, 5, ""));
	private final IntSetting range = add(new IntSetting("Range", 6, 32, 18, " blocks"));

	/** x, y, z, nascimento (ms), raio maximo */
	private final List<double[]> ripples = new ArrayList<>();
	private final java.util.Random rnd = new java.util.Random();

	public BetterRainModule() {
		super("Better Rain", Category.SHADERS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onDisable() {
		ripples.clear();
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null) {
			ripples.clear();
			return;
		}
		long now = System.currentTimeMillis();
		ripples.removeIf(r -> now - (long) r[3] > LIFE_MS);
		float rain = level.getRainLevel(1f);
		if (rain < 0.05f) return;
		double want = density.get() * 1.5 * rain * (1.0 + 0.6 * level.getThunderLevel(1f));
		int n = (int) want + (rnd.nextDouble() < want - (int) want ? 1 : 0);
		Vec3 eye = p.getEyePosition();
		double rmax = 0.16 + size.get() * 0.05;
		for (int i = 0; i < n && ripples.size() < MAX; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = range.get() * Math.sqrt(rnd.nextDouble());
			double x = p.getX() + Math.cos(ang) * dist;
			double z = p.getZ() + Math.sin(ang) * dist;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
			if (!level.hasChunkAt(top) || !level.isRainingAt(top)) continue;
			BlockPos below = top.below();
			var bs = level.getBlockState(below);
			double y;
			var fs = bs.getFluidState();
			if (!fs.isEmpty()) {
				y = below.getY() + fs.getHeight(level, below);
			} else {
				var sh = bs.getCollisionShape(level, below);
				y = sh.isEmpty() ? top.getY() : below.getY() + sh.max(Direction.Axis.Y);
			}
			Vec3 target = new Vec3(x, y + 0.05, z);
			// o ESP desenha atraves de paredes: so cria onda onde ha linha de visao
			var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() != HitResult.Type.MISS) continue;
			ripples.add(new double[] {x, y + 0.02, z, now, rmax * (0.7 + rnd.nextDouble() * 0.6)});
		}
	}

	/** Anel fino com degrade para os dois lados (poucos segmentos: sao muitas ondas ao mesmo tempo). */
	private static void ring(Esp esp, double cx, double y, double cz, double r, double w, int argb, float a, int n) {
		if (a <= 0.004f) return;
		double r0 = Math.max(0.01, r - w), r1 = r + w;
		for (int i = 0; i < n; i++) {
			double t0 = i * Math.PI * 2 / n, t1 = (i + 1) * Math.PI * 2 / n;
			double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
			esp.gradQuad(cx + c0 * r0, y, cz + s0 * r0, cx + c1 * r0, y, cz + s1 * r0,
					cx + c1 * r, y, cz + s1 * r, cx + c0 * r, y, cz + s0 * r,
					argb, 0f, 0f, a, a);
			esp.gradQuad(cx + c0 * r, y, cz + s0 * r, cx + c1 * r, y, cz + s1 * r,
					cx + c1 * r1, y, cz + s1 * r1, cx + c0 * r1, y, cz + s0 * r1,
					argb, a, a, 0f, 0f);
		}
	}

	@Override
	public void collect(Esp esp) {
		var p = Minecraft.getInstance().player;
		if (p == null || ripples.isEmpty()) return;
		long now = System.currentTimeMillis();
		int c = FxShapes.mix(color.get() | 0xFF000000, 0xFFFFFFFF, 0.55f);
		double px = p.getX(), pz = p.getZ();
		double rg = range.get();
		for (double[] r : ripples) {
			double k = (now - (long) r[3]) / (double) LIFE_MS;
			if (k < 0 || k > 1) continue;
			double dx = r[0] - px, dz = r[2] - pz;
			double d = Math.sqrt(dx * dx + dz * dz);
			double fade = 1.0 - Math.min(1.0, Math.max(0.0, (d - rg * 0.65) / (rg * 0.35)));
			if (fade <= 0.0) continue;
			double e = 1.0 - (1.0 - k) * (1.0 - k);
			float a = (float) (Math.pow(1.0 - k, 1.4) * 0.8 * fade);
			int n = d > 10 ? 12 : 18;
			ring(esp, r[0], r[1], r[2], r[4] * e, 0.022 + 0.018 * (1.0 - k), c, a, n);
			if (d < 9 && k > 0.2) { // eco interno so perto de voce
				double ke = (k - 0.2) / 0.8;
				double e2 = 1.0 - (1.0 - ke) * (1.0 - ke);
				ring(esp, r[0], r[1] + 0.003, r[2], r[4] * 0.55 * e2, 0.016, c, (float) (Math.pow(1.0 - ke, 1.5) * 0.5 * fade), n);
			}
		}
	}
}
