package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.IntSetting;
import com.witch.setting.OreSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Marca buracos de 1x1 e tuneis de 1x1 ou 1x2 (corredor de jogador) cercados de bloco solido: entrada tipica de base escondida. */
public class SusHoleFinderModule extends Module implements EspSource {
	private static final int MAX_RESULTS = 80;
	private static final int SCAN_INTERVAL_TICKS = 40;

	private final IntSetting range = add(new IntSetting("Range", 8, 32, 16, ""));
	private final IntSetting minLen = add(new IntSetting("Min length", 3, 12, 4, ""));
	private final IntSetting fill = add(new IntSetting("Fill opacity", 0, 40, 12, "%"));
	private final OreSetting shafts = add(new OreSetting("Vertical holes", true, 1));
	private final OreSetting tunnels = add(new OreSetting("Tunnels", true, 6));
	private final IntSetting curves = add(new IntSetting("Curvas, degraus e blocos ao lado (0/1)", 0, 1, 1, ""));

	private volatile List<int[]> results = List.of();
	private int cooldown = 0;

	public SusHoleFinderModule() {
		super("Sus Hole Finder", Category.VISUALS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void collect(Esp esp) {
		float fillA = fill.get() / 100f;
		for (int[] b : results) {
			if (b == null || b.length < 7) continue;
			esp.box(b[0], b[1], b[2], b[3], b[4], b[5], b[6], fillA);
		}
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	protected void onDisable() {
		results = List.of();
	}

	private static int idx(int d, int ix, int iy, int iz) {
		return (iy * d + iz) * d + ix;
	}


	/** Celula de tunel: h blocos de ar empilhados, piso e teto solidos e paredes solidas dos dois lados (eixo perpendicular). */
	private static boolean hit(byte[] g, int d, int h, boolean alongX, int ix, int iy, int iz) {
		if (ix < 1 || ix > d - 2 || iz < 1 || iz > d - 2 || iy < 1 || iy > d - 1 - h) return false;
		if (g[idx(d, ix, iy - 1, iz)] != 1 || g[idx(d, ix, iy + h, iz)] != 1) return false;
		for (int k = 0; k < h; k++) {
			if (g[idx(d, ix, iy + k, iz)] != 0) return false;
			if (alongX) {
				if (g[idx(d, ix, iy + k, iz + 1)] != 1 || g[idx(d, ix, iy + k, iz - 1)] != 1) return false;
			} else {
				if (g[idx(d, ix + 1, iy + k, iz)] != 1 || g[idx(d, ix - 1, iy + k, iz)] != 1) return false;
			}
		}
		return true;
	}

	/**
	 * Percorre tuneis ao longo de X ou Z. Um degrau de +-1 em Y entre colunas vizinhas NAO encerra o tunel
	 * (antes qualquer degrau fazia o scanner achar que era o fim). O tamanho minimo vale para a cadeia inteira.
	 */
	private static void scan(byte[] g, int d, int h, boolean alongX, int min, int col,
			int ox, int oy, int oz, List<int[]> out) {
		for (int iy = 1; iy <= d - 1 - h; iy++) {
			for (int l = 1; l <= d - 2; l++) {
				int run = 0;
				int cy = iy;
				int segStart = 0;
				List<int[]> segs = new ArrayList<>();
				for (int a = 1; a <= d - 1; a++) {
					int ix = alongX ? a : l;
					int iz = alongX ? l : a;
					int found = -1;
					if (a <= d - 2) {
						if (hit(g, d, h, alongX, ix, cy, iz)) {
							found = cy;
						} else if (run > 0) {
							int ix2 = ix;
							int iz2 = iz;
							if (hit(g, d, h, alongX, ix2, cy - 1, iz2)) found = cy - 1;
							else if (hit(g, d, h, alongX, ix2, cy + 1, iz2)) found = cy + 1;
						}
					}
					if (found >= 0) {
						if (run == 0) {
							segStart = a;
						} else if (found != cy) {
							segs.add(new int[]{segStart, a, cy});
							segStart = a;
						}
						cy = found;
						run++;
					} else {
						if (run > 0) {
							segs.add(new int[]{segStart, a, cy});
							if (run >= min) {
								for (int[] sg : segs) {
									if (alongX) {
										out.add(new int[]{ox + sg[0], oy + sg[2], oz + l, ox + sg[1], oy + sg[2] + h, oz + l + 1, col});
									} else {
										out.add(new int[]{ox + l, oy + sg[2], oz + sg[0], ox + l + 1, oy + sg[2] + h, oz + sg[1], col});
									}
								}
							}
						}
						run = 0;
						cy = iy;
						segs.clear();
					}
				}
			}
		}
	}

	/** Celula estreita: ar com pouco ar em volta (26 vizinhas) e pelo menos 3 das 6 faces solidas. */
	private static boolean narrow(byte[] g, int d, int ix, int iy, int iz) {
		if (ix < 1 || iy < 1 || iz < 1 || ix > d - 2 || iy > d - 2 || iz > d - 2) return false;
		if (g[idx(d, ix, iy, iz)] != 0) return false;
		int solid = 0;
		if (g[idx(d, ix + 1, iy, iz)] == 1) solid++;
		if (g[idx(d, ix - 1, iy, iz)] == 1) solid++;
		if (g[idx(d, ix, iy + 1, iz)] == 1) solid++;
		if (g[idx(d, ix, iy - 1, iz)] == 1) solid++;
		if (g[idx(d, ix, iy, iz + 1)] == 1) solid++;
		if (g[idx(d, ix, iy, iz - 1)] == 1) solid++;
		if (solid < 3) return false;
		int air = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if ((dx | dy | dz) != 0 && g[idx(d, ix + dx, iy + dy, iz + dz)] == 0) air++;
				}
			}
		}
		return air <= 7;
	}

	/**
	 * Tuneis com curvas: junta as celulas estreitas vizinhas (26 direcoes, entao degraus e diagonais contam) em
	 * componentes. Vale se tem comprimento minimo, e e comprida e fina (nao um bolsao de caverna).
	 * Cada componente vira caixas em linhas retas (X, Y ou Z).
	 */
	private void scanCurved(byte[] g, int d, int min, int ox, int oy, int oz, List<int[]> out) {
		int total = d * d * d;
		boolean[] nar = new boolean[total];
		for (int iy = 1; iy <= d - 2; iy++) {
			for (int iz = 1; iz <= d - 2; iz++) {
				for (int ix = 1; ix <= d - 2; ix++) {
					if (g[idx(d, ix, iy, iz)] == 0 && narrow(g, d, ix, iy, iz)) nar[idx(d, ix, iy, iz)] = true;
				}
			}
		}
		boolean[] seen = new boolean[total];
		int[] comp = new int[total]; // carimbo do componente atual por indice
		int stamp = 0;
		java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
		for (int start = 0; start < total; start++) {
			if (!nar[start] || seen[start]) continue;
			stamp++;
			List<Integer> cells = new ArrayList<>();
			q.add(start);
			seen[start] = true;
			int minX = d, maxX = 0, minY = d, maxY = 0, minZ = d, maxZ = 0;
			while (!q.isEmpty()) {
				int c = q.poll();
				cells.add(c);
				comp[c] = stamp;
				int ix = c % d, iz = (c / d) % d, iy = c / (d * d);
				minX = Math.min(minX, ix); maxX = Math.max(maxX, ix);
				minY = Math.min(minY, iy); maxY = Math.max(maxY, iy);
				minZ = Math.min(minZ, iz); maxZ = Math.max(maxZ, iz);
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							if ((dx | dy | dz) == 0) continue;
							int nx = ix + dx, ny = iy + dy, nz = iz + dz;
							if (nx < 0 || ny < 0 || nz < 0 || nx >= d || ny >= d || nz >= d) continue;
							int n = idx(d, nx, ny, nz);
							if (nar[n] && !seen[n]) { seen[n] = true; q.add(n); }
						}
					}
				}
			}
			int ex = maxX - minX + 1, ey = maxY - minY + 1, ez = maxZ - minZ + 1;
			int span = Math.max(ex, Math.max(ey, ez));
			if (cells.size() < min || span < min || cells.size() > span * 4) continue;
			boolean vertical = ey > Math.max(ex, ez);
			int col = vertical ? (shafts.enabled() ? shafts.color() : 0) : (tunnels.enabled() ? tunnels.color() : 0);
			if (col == 0) continue;
			emitRuns(cells, comp, stamp, d, ox, oy, oz, col, out);
		}
	}

	/** Cobre as celulas do componente com o menor numero de caixas retas (maior linha primeiro). */
	private static void emitRuns(List<Integer> cells, int[] comp, int stamp, int d, int ox, int oy, int oz, int col, List<int[]> out) {
		java.util.Set<Integer> done = new java.util.HashSet<>();
		int[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		for (int c : cells) {
			if (done.contains(c)) continue;
			int ix = c % d, iz = (c / d) % d, iy = c / (d * d);
			int bestAxis = 0, bestLen = 0, bestBack = 0;
			for (int a = 0; a < 3; a++) {
				int back = 0, fwd = 0;
				while (true) {
					int x = ix - axes[a][0] * (back + 1), y = iy - axes[a][1] * (back + 1), z = iz - axes[a][2] * (back + 1);
					if (x < 0 || y < 0 || z < 0 || comp[idx(d, x, y, z)] != stamp) break;
					back++;
				}
				while (true) {
					int x = ix + axes[a][0] * (fwd + 1), y = iy + axes[a][1] * (fwd + 1), z = iz + axes[a][2] * (fwd + 1);
					if (x >= d || y >= d || z >= d || comp[idx(d, x, y, z)] != stamp) break;
					fwd++;
				}
				if (back + fwd + 1 > bestLen) { bestLen = back + fwd + 1; bestAxis = a; bestBack = back; }
			}
			int sx = ix - axes[bestAxis][0] * bestBack, sy = iy - axes[bestAxis][1] * bestBack, sz = iz - axes[bestAxis][2] * bestBack;
			for (int k = 0; k < bestLen; k++) {
				done.add(idx(d, sx + axes[bestAxis][0] * k, sy + axes[bestAxis][1] * k, sz + axes[bestAxis][2] * k));
			}
			out.add(new int[]{ox + sx, oy + sy, oz + sz,
					ox + sx + 1 + axes[bestAxis][0] * (bestLen - 1), oy + sy + 1 + axes[bestAxis][1] * (bestLen - 1),
					oz + sz + 1 + axes[bestAxis][2] * (bestLen - 1), col});
		}
	}

	@Override
	public void onTick() {
		try {
			scanTick();
		} catch (Throwable t) {
			results = List.of();
			System.err.println("[Witch] Sus Hole Finder: " + t);
			t.printStackTrace();
		}
	}

	private void scanTick() {
		if (--cooldown > 0) return;
		cooldown = SCAN_INTERVAL_TICKS * (range.get() > 20 ? 2 : 1);

		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (mc.player == null || level == null) {
			results = List.of();
			return;
		}

		int r = range.get();
		int d = 2 * r + 3;
		BlockPos c = mc.player.blockPosition();
		int ox = c.getX() - r - 1;
		int oy = c.getY() - r - 1;
		int oz = c.getZ() - r - 1;

		// 0 = ar, 1 = bloco solido, 2 = outra coisa (agua, planta, vidro...)
		byte[] g = new byte[d * d * d];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int iy = 0; iy < d; iy++) {
			for (int iz = 0; iz < d; iz++) {
				for (int ix = 0; ix < d; ix++) {
					pos.set(ox + ix, oy + iy, oz + iz);
					BlockState st = level.getBlockState(pos);
					byte t = st.isAir() ? 0 : (st.isSolidRender() ? (byte) 1 : (byte) 2);
					g[idx(d, ix, iy, iz)] = t;
				}
			}
		}

		int min = minLen.get();
		List<int[]> out = new ArrayList<>();

		if (curves.get() == 1) {
			scanCurved(g, d, min, ox, oy, oz, out);
		}

		if (curves.get() == 0 && shafts.enabled()) {
			int col = shafts.color();
			for (int ix = 1; ix <= d - 2; ix++) {
				for (int iz = 1; iz <= d - 2; iz++) {
					int run = 0;
					for (int iy = 1; iy <= d - 1; iy++) {
						boolean hit = iy <= d - 2
								&& g[idx(d, ix, iy, iz)] == 0
								&& g[idx(d, ix + 1, iy, iz)] == 1 && g[idx(d, ix - 1, iy, iz)] == 1
								&& g[idx(d, ix, iy, iz + 1)] == 1 && g[idx(d, ix, iy, iz - 1)] == 1;
						if (hit) {
							run++;
						} else {
							if (run >= min) {
								out.add(new int[]{ox + ix, oy + iy - run, oz + iz, ox + ix + 1, oy + iy, oz + iz + 1, col});
							}
							run = 0;
						}
					}
				}
			}
		}

		if (curves.get() == 0 && tunnels.enabled()) {
			int col = tunnels.color();
			// 1x1 e 1x2, nos dois eixos; o tunel pode descer/subir 1 bloco e continua contando como o mesmo
			scan(g, d, 1, true, min, col, ox, oy, oz, out);
			scan(g, d, 1, false, min, col, ox, oy, oz, out);
			scan(g, d, 2, true, min, col, ox, oy, oz, out);
			scan(g, d, 2, false, min, col, ox, oy, oz, out);
		}

		java.util.Map<String, int[]> uniq = new java.util.LinkedHashMap<>();
		for (int[] b : out) uniq.put(java.util.Arrays.toString(b), b);
		out = new ArrayList<>(uniq.values());

		final int px = c.getX();
		final int py = c.getY();
		final int pz = c.getZ();
		out.sort(Comparator.comparingLong(b -> {
			long dx = (b[0] + b[3]) / 2 - px;
			long dy = (b[1] + b[4]) / 2 - py;
			long dz = (b[2] + b[5]) / 2 - pz;
			return dx * dx + dy * dy + dz * dz;
		}));
		if (out.size() > MAX_RESULTS) {
			out = new ArrayList<>(out.subList(0, MAX_RESULTS));
		}
		results = out;
	}
}