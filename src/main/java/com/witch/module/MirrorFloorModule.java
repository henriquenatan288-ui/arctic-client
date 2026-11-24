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
public class MirrorFloorModule extends Module implements EspSource {
	private static final int LIFE_TICKS = 60;
	private final ColorSetting color = add(new ColorSetting("Color", 1));
	private final IntSetting strength = add(new IntSetting("Strength", 1, 10, 6, ""));
	private final IntSetting range = add(new IntSetting("Range", 4, 20, 12, " blocks"));
	private final IntSetting water = add(new IntSetting("Agua tambem (0/1)", 0, 1, 1, ""));
	private final IntSetting wet = add(new IntSetting("Mais forte na chuva (0/1)", 0, 1, 1, ""));

	/** coluna -> {x, y, z, validoAte(tick)} */
	private final Map<Long, double[]> tiles = new HashMap<>();
	private final java.util.Random rnd = new java.util.Random();
	private long tick;

	public MirrorFloorModule() {
		super("Mirror Floor", Category.SHADERS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onDisable() {
		tiles.clear();
	}

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
		double r = range.get();
		double r2 = (r + 2) * (r + 2);
		tiles.values().removeIf(t -> t[3] < tick || (t[0] - p.getX()) * (t[0] - p.getX()) + (t[2] - p.getZ()) * (t[2] - p.getZ()) > r2);
		Vec3 eye = p.getEyePosition();
		int tries = 28;
		for (int i = 0; i < tries; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = r * Math.sqrt(rnd.nextDouble());
			int bx = Mth.floor(p.getX() + Math.cos(ang) * dist);
			int bz = Mth.floor(p.getZ() + Math.sin(ang) * dist);
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
			// o ESP desenha atraves de paredes: so o piso que voce enxerga
			var hit = level.clip(new ClipContext(eye, new Vec3(bx + 0.5, y + 0.05, bz + 0.5), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() != HitResult.Type.MISS) { tiles.remove(key); continue; }
			tiles.put(key, new double[] {bx, y + 0.012, bz, tick + LIFE_TICKS});
		}
	}

	@Override
	public void collect(Esp esp) {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null || tiles.isEmpty()) return;
		float pt = esp.partial();
		double ex = Mth.lerp(pt, p.xOld, p.getX());
		double ey = Mth.lerp(pt, p.yOld, p.getY()) + p.getEyeHeight();
		double ez = Mth.lerp(pt, p.zOld, p.getZ());
		double k = strength.get() / 10.0;
		if (wet.get() == 1) k *= 1.0 + 0.7 * level.getRainLevel(1f);
		double rg = range.get();
		int c = FxShapes.mix(color.get() | 0xFF000000, 0xFFFFFFFF, 0.35f);
		for (double[] t : tiles.values()) {
			double x = t[0], y = t[1], z = t[2];
			double life = Math.min(1.0, (t[3] - tick) / 12.0); // some devagar quando expira
			double cx = x + 0.5 - ex, cz = z + 0.5 - ez;
			double edge = 1.0 - Mth.clamp((Math.sqrt(cx * cx + cz * cz) - rg * 0.6) / (rg * 0.4), 0.0, 1.0);
			if (edge <= 0.0 || life <= 0.0) continue;
			float a0 = corner(x, y, z, ex, ey, ez, k * edge * life);
			float a1 = corner(x + 1, y, z, ex, ey, ez, k * edge * life);
			float a2 = corner(x + 1, y, z + 1, ex, ey, ez, k * edge * life);
			float a3 = corner(x, y, z + 1, ex, ey, ez, k * edge * life);
			esp.gradQuad(x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, c, a0, a1, a2, a3);
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
