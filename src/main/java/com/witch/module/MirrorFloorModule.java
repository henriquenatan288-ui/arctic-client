package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.render.FxShapes;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Chao de espelho, etapa 1 ("brilho espelhado"): cada piso exposto ganha um reflexo do ceu com Fresnel
 * (fraco de cima, forte em angulo rasante), mais forte na chuva e na agua. Ainda NAO reflete blocos nem
 * entidades: isso exige renderizar a cena de novo e e a etapa 2.
 */
public class MirrorFloorModule extends Module implements EspSource, com.witch.render.MirrorSsr.Source {
	private static final int LIFE_TICKS = 160;
	private final ColorSetting color = add(new ColorSetting("Color", 1));
	private final IntSetting strength = add(new IntSetting("Strength", 1, 10, 6, ""));
	private final IntSetting range = add(new IntSetting("Range", 4, 20, 12, " blocks"));
	private final IntSetting water = add(new IntSetting("Agua tambem (0/1)", 0, 1, 1, ""));
	private final IntSetting entities = add(new IntSetting("Reflexo de jogadores e mobs (0/1)", 0, 1, 1, ""));
	private final IntSetting ssr = add(new IntSetting("Reflexo de tela - blocos e skins (0/1)", 0, 1, 1, ""));
	private final IntSetting wet = add(new IntSetting("Mais forte na chuva (0/1)", 0, 1, 1, ""));

	/** coluna -> {x, y, z, validoAte(tick)} */
	private final Map<Long, double[]> tiles = new HashMap<>();
	private final java.util.Random rnd = new java.util.Random();
	private long tick;

	public MirrorFloorModule() {
		super("Mirror Floor", Category.SHADERS);
		com.witch.render.MirrorSsr.source = this;
		com.witch.render.MirrorSsr.init();
	}

	@Override
	public boolean ssrWanted() {
		return isEnabled() && ssr.get() == 1;
	}

	/** Reflexo de tela ativo e funcionando: o brilho simples e as silhuetas ficam desligados. */
	private boolean ssrOn() {
		return ssr.get() == 1 && com.witch.render.MirrorSsr.working;
	}

	@Override
	public java.util.Collection<float[]> ssrTiles() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		java.util.List<float[]> out = new java.util.ArrayList<>(tiles.size());
		if (p == null || level == null) return out;
		double k = strength.get() / 10.0;
		if (wet.get() == 1) k *= 1.0 + 0.7 * level.getRainLevel(1f);
		double rg = range.get();
		for (double[] t : tiles.values()) {
			double cx = t[0] + 0.5 - p.getX(), cz = t[2] + 0.5 - p.getZ();
			double edge = 1.0 - Mth.clamp((Math.sqrt(cx * cx + cz * cz) - rg * 0.6) / (rg * 0.4), 0.0, 1.0);
			float a = (float) Mth.clamp(k * edge, 0.0, 1.0);
			if (a <= 0.01f) continue;
			out.add(new float[] {(float) t[0], (float) t[1], (float) t[2], a});
		}
		return out;
	}

	@Override
	public float[] ssrTint() {
		var level = Minecraft.getInstance().level;
		float day = level == null ? 1f : (float) (0.35 + 0.65 * (1.0 - Math.min(1.0, level.getSkyDarken() / 11.0)));
		// cor do "ceu" refletido quando o raio nao acha nada na tela (ex.: olhando para baixo): mistura azul de ceu com a cor escolhida
		int c = FxShapes.mix(color.get() | 0xFF000000, 0xFF8CB8FF, 0.55f);
		return new float[] {((c >> 16) & 0xFF) / 255f * day, ((c >> 8) & 0xFF) / 255f * day, (c & 0xFF) / 255f * day};
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onDisable() {
		tiles.clear();
	}

	private int cursor;

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null) {
			tiles.clear();
			return;
		}
		tick++;
		int r = range.get();
		double r2 = (r + 2.0) * (r + 2.0);
		tiles.values().removeIf(t -> t[3] < tick || (t[0] - p.getX()) * (t[0] - p.getX()) + (t[2] - p.getZ()) * (t[2] - p.getZ()) > r2);
		boolean ssrWorks = ssrOn();
		Vec3 eye = p.getEyePosition();
		int side = 2 * r + 1, total = side * side;
		int bx0 = Mth.floor(p.getX()) - r, bz0 = Mth.floor(p.getZ()) - r;
		int per = Math.min(total, 110); // varre a grade inteira em poucos ticks, sem aleatoriedade (nao pisca)
		for (int i = 0; i < per; i++) {
			cursor = (cursor + 1) % total;
			int bx = bx0 + cursor % side, bz = bz0 + cursor / side;
			double dx = bx + 0.5 - p.getX(), dz = bz + 0.5 - p.getZ();
			if (dx * dx + dz * dz > (double) r * r) continue;
			long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(bx, 0, bz));
			if (!level.hasChunkAt(top)) continue;
			BlockPos below = top.below();
			var bs = level.getBlockState(below);
			double y;
			var fs = bs.getFluidState();
			if (!fs.isEmpty()) {
				if (water.get() == 0) { tiles.remove(key); continue; }
				y = below.getY() + fs.getHeight(level, below);
			} else {
				var sh = bs.getCollisionShape(level, below);
				if (sh.isEmpty() || sh.max(Direction.Axis.X) - sh.min(Direction.Axis.X) < 0.99
						|| sh.max(Direction.Axis.Z) - sh.min(Direction.Axis.Z) < 0.99) { tiles.remove(key); continue; }
				y = below.getY() + sh.max(Direction.Axis.Y);
			}
			double vis = 1;
			if (!ssrWorks) {
				// brilho simples (ESP) atravessa paredes: marca se ha linha de visao em vez de apagar o piso
				var hit = level.clip(new ClipContext(eye, new Vec3(bx + 0.5, y + 0.05, bz + 0.5), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (hit.getType() != HitResult.Type.MISS) vis = 0;
			}
			// o reflexo de tela usa a profundidade da cena para esconder o que esta atras de paredes
			tiles.put(key, new double[] {bx, y + 0.012, bz, tick + LIFE_TICKS, vis});
		}
	}

	@Override
	public void collect(Esp esp) {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null || tiles.isEmpty() || ssrOn()) return;
		float pt = esp.partial();
		double ex = Mth.lerp(pt, p.xOld, p.getX());
		double ey = Mth.lerp(pt, p.yOld, p.getY()) + p.getEyeHeight();
		double ez = Mth.lerp(pt, p.zOld, p.getZ());
		double k = strength.get() / 10.0;
		if (wet.get() == 1) k *= 1.0 + 0.7 * level.getRainLevel(1f);
		double rg = range.get();
		// de noite o ceu reflete menos
		k *= 0.35 + 0.65 * (1.0 - Math.min(1.0, level.getSkyDarken() / 11.0));
		double tm = System.nanoTime() / 1.0e9;
		boolean rainy = level.getRainLevel(1f) > 0.1f;
		int c = FxShapes.mix(color.get() | 0xFF000000, 0xFFFFFFFF, 0.35f);
		for (double[] t : tiles.values()) {
			if (t[4] < 0.5) continue;
			double x = t[0], y = t[1], z = t[2];
			double life = Math.min(1.0, (t[3] - tick) / 12.0); // some devagar quando expira
			double cx = x + 0.5 - ex, cz = z + 0.5 - ez;
			double edge = 1.0 - Mth.clamp((Math.sqrt(cx * cx + cz * cz) - rg * 0.6) / (rg * 0.4), 0.0, 1.0);
			if (edge <= 0.0 || life <= 0.0) continue;
			// brilho que oscila devagar (mais agitado na chuva)
			double sh = 0.88 + 0.12 * Math.sin(tm * (rainy ? 3.2 : 1.1) + x * 1.7 + z * 2.3);
			double kk = k * edge * life * sh;
			float a0 = corner(x, y, z, ex, ey, ez, kk);
			float a1 = corner(x + 1, y, z, ex, ey, ez, kk);
			float a2 = corner(x + 1, y, z + 1, ex, ey, ez, kk);
			float a3 = corner(x, y, z + 1, ex, ey, ez, kk);
			esp.gradQuad(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, c, a0, a1, a2, a3);
		}
		if (entities.get() == 1) reflectEntities(esp, mc, ex, ey, ez, pt, k, c);
	}

	/** Reflexo de jogadores e mobs: silhueta espelhada abaixo do piso, que some com a profundidade. */
	private void reflectEntities(Esp esp, Minecraft mc, double ex, double ey, double ez, float pt, double k, int c) {
		boolean fp = mc.options.getCameraType().isFirstPerson();
		double rg = range.get();
		for (var e : mc.level.entitiesForRendering()) {
			if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || le.isInvisible() || le.isSpectator()) continue;
			if (e == mc.player && fp) continue;
			double x = Mth.lerp(pt, e.xOld, e.getX()), y = Mth.lerp(pt, e.yOld, e.getY()), z = Mth.lerp(pt, e.zOld, e.getZ());
			double dx = x - ex, dz = z - ez;
			if (dx * dx + dz * dz > rg * rg) continue;
			double[] t = tiles.get(((long) Mth.floor(x) << 32) ^ (Mth.floor(z) & 0xFFFFFFFFL));
			if (t == null) continue;
			double fy = t[1];
			double h0 = y - fy;
			if (h0 < -0.2 || h0 > 4.0) continue;
			double w = e.getBbWidth() / 2.0, h = e.getBbHeight();
			double yHi = fy - Math.max(0.0, h0), yLo = fy - Math.max(0.0, h0) - h;
			float a = (float) Mth.clamp(k * 0.55 * (1.0 - h0 / 5.0), 0.0, 0.6);
			if (a <= 0.01f) continue;
			float b = a * 0.1f;
			double x0 = x - w, x1 = x + w, z0 = z - w, z1 = z + w;
			esp.gradQuad(x0, yHi, z0, x1, yHi, z0, x1, yLo, z0, x0, yLo, z0, c, a, a, b, b);
			esp.gradQuad(x0, yHi, z1, x1, yHi, z1, x1, yLo, z1, x0, yLo, z1, c, a, a, b, b);
			esp.gradQuad(x0, yHi, z0, x0, yHi, z1, x0, yLo, z1, x0, yLo, z0, c, a, a, b, b);
			esp.gradQuad(x1, yHi, z0, x1, yHi, z1, x1, yLo, z1, x1, yLo, z0, c, a, a, b, b);
		}
	}

	/** Fresnel de Schlick: pouco reflexo olhando de cima, muito em angulo rasante. */
	private static float corner(double x, double y, double z, double ex, double ey, double ez, double k) {
		double dx = x - ex, dy = y - ey, dz = z - ez;
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1e-4) return 0f;
		double cos = Math.abs(dy) / d;
		double f = 0.05 + 0.95 * Math.pow(1.0 - cos, 4.0);
		return (float) Mth.clamp(f * k * 0.9, 0.0, 0.85);
	}
}
