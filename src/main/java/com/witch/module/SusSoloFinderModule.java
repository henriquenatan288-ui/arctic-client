package com.witch.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.witch.gui.FontUtil;
import com.witch.gui.RenderUtil;
import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.IntSetting;
import com.witch.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sus Solo Finder: feito pra usar voando. Escaneia a faixa perto da superficie de cada chunk (nao depende da sua altura),
 * apita ao achar uma suspeita e desenha uma placa neon no solo da chunk pra voce ver de longe.
 * Cor = "chance de base" estimada pela pontuacao dos blocos: vermelho 25%, amarelo 50%, verde 75%, branco 81%+.
 * A chance e uma heuristica (peso dos blocos), nao uma probabilidade real.
 */
public class SusSoloFinderModule extends Module implements EspSource {
	private static final long SCAN_BUDGET_NS = 4_000_000L;
	private static final int MAX_SCANS_PER_TICK = 16;
	private static final long RESCAN_QUIET = 2400L;
	private static final long RESCAN_ACTIVE = 600L;
	private final java.util.Map<Long, Long> lastScan = new java.util.HashMap<>();
	private static final long ALERT_DISPLAY_MS = 2800L;
	/** score/(score+K): K=40 -> 25% ~ 13 pts, 50% = 40, 75% = 120, 81% ~ 170. */
	private static final double K = 40.0;

	private static final int RED = 0xFFFF1F4B;
	private static final int YELLOW = 0xFFFFF01F;
	private static final int GREEN = 0xFF39FF14;
	private static final int WHITE = 0xFFFFFFFF;

	private static SusSoloFinderModule instance;

	private final IntSetting radius = add(new IntSetting("Chunk radius (so ate onde o servidor manda chunks)", 1, 64, 16, ""));
	private final IntSetting minY = add(new IntSetting("Y minimo (bases ate esta camada)", -64, 200, -53, ""));
	private final IntSetting above = add(new IntSetting("Acima da superficie", 0, 200, 60, ""));
	private final IntSetting minChance = add(new IntSetting("Min chance", 25, 81, 25, "%"));
	private final IntSetting maxAlerts = add(new IntSetting("Max alerts", 1, 20, 6, ""));
	private final IntSetting fill = add(new IntSetting("Fill opacity", 5, 60, 25, "%"));
	private final IntSetting autoWp = add(new IntSetting("Auto waypoint (0/1)", 0, 1, 0, ""));
	private final IntSetting plateOn = add(new IntSetting("Imagem na placa (0/1) - pasta 'placas' do jogo", 0, 1, 0, ""));
	private final IntSetting plateRes = add(new IntSetting("Resolucao da imagem (1 16x16, 2 32x32)", 1, 2, 1, ""));
	private final IntSetting plateTint = add(new IntSetting("Cor da chance sobre a imagem", 0, 50, 15, "%"));
	private final KeybindSetting reloadKey = add(new KeybindSetting("Reload chunks"));

	private final Map<Block, Integer> weights = SusChunkFinderModule.buildWeights();
	private final Set<Block> trialBlocks = SusChunkFinderModule.buildTrialBlocks();
	/** cx, cz, groundY, chance */
	private final Map<Long, int[]> found = new HashMap<>();
	private final Set<Long> seen = new HashSet<>();
	/** Chunks de estrutura natural (cidade antiga, fortaleza, trial) e vizinhas: nao viram base. */
	private final Set<Long> naturalZone = new HashSet<>();
	private volatile List<int[]> view = List.of();
	private final Set<Block> villageMarkers = SusChunkFinderModule.buildVillageMarkers();
	private boolean lastInteresting;
	/** Imagem da placa reduzida (n*n ARGB), carregada em segundo plano. */
	private volatile int[] plate;
	private volatile int plateN;
	private volatile boolean plateLoading;
	private long plateStamp = -1;
	private int plateCheck = 0;
	private boolean plateWarned;

	private int[][] offsets = null;
	private int offsetsRadius = -1;
	private int cursor = 0;
	private int cooldown = 0;
	private int alertCount = 0;
	private volatile String alertText = null;
	private volatile long alertUntil = 0L;

	public SusSoloFinderModule() {
		super("Sus Solo Finder", Category.VISUALS);
		instance = this;
	}

	static int colorFor(int chance) {
		if (chance >= 81) return WHITE;
		if (chance >= 75) return GREEN;
		if (chance >= 50) return YELLOW;
		return RED;
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void collect(Esp esp) {
		float fa = fill.get() / 100f;
		int[] img = plateOn.get() == 1 ? plate : null;
		int n = plateN;
		var pl = Minecraft.getInstance().player;
		// so as placas mais proximas levam a imagem (cada uma vira ate n*n quadrados); as outras ficam como caixa
		int budget = n >= 32 ? 3 : 8;
		List<int[]> vs = view;
		if (img != null && pl != null && vs.size() > 1) {
			vs = new ArrayList<>(vs);
			double px = pl.getX(), pz = pl.getZ();
			vs.sort((p, q) -> Double.compare(Math.hypot(p[0] * 16 + 8 - px, p[1] * 16 + 8 - pz), Math.hypot(q[0] * 16 + 8 - px, q[1] * 16 + 8 - pz)));
		}
		int used = 0;
		for (int[] v : vs) {
			double x0 = v[0] * 16.0;
			double z0 = v[1] * 16.0;
			int gy = v[2];
			int argb = colorFor(v[3]);
			if (img != null && used < budget) {
				used++;
				drawImage(esp, img, n, x0, gy, z0, argb, fa);
				esp.box(x0, gy, z0, x0 + 16, gy + 0.25, z0 + 16, argb, 0f); // contorno na cor da chance
			} else {
				esp.box(x0, gy, z0, x0 + 16, gy + 0.25, z0 + 16, argb, fa);
			}
		}
	}

	/** Imagem como grade de quadrados coloridos no chao; a cor da chance entra so de leve por cima. */
	private void drawImage(Esp esp, int[] img, int n, double x0, double gy, double z0, int chanceArgb, float fa) {
		double cs = 16.0 / n;
		float t = plateTint.get() / 100f;
		float base = Math.min(1f, fa * 3f);
		int cr = (chanceArgb >> 16) & 255, cg = (chanceArgb >> 8) & 255, cb = chanceArgb & 255;
		double y = gy + 0.06;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int c = img[j * n + i];
				int a = c >>> 24;
				if (a < 10) continue;
				int r = Math.round(((c >> 16) & 255) * (1f - t) + cr * t);
				int g = Math.round(((c >> 8) & 255) * (1f - t) + cg * t);
				int b = Math.round((c & 255) * (1f - t) + cb * t);
				float al = a / 255f * base;
				double ax = x0 + i * cs, az = z0 + j * cs;
				esp.gradQuad(ax, y, az, ax + cs, y, az, ax + cs, y, az + cs, ax, y, az + cs,
						0xFF000000 | (r << 16) | (g << 8) | b, al, al, al, al);
			}
		}
	}

	private void refreshPlate() {
		if (plateOn.get() == 0 || plateLoading) return;
		java.nio.file.Path dir = com.witch.config.Folders.get("placas");
		java.nio.file.Path f = PlateImage.find(dir);
		int n = plateRes.get() == 2 ? 32 : 16;
		long stamp = -2;
		try { if (f != null) stamp = java.nio.file.Files.getLastModifiedTime(f).toMillis() * 4 + n; } catch (Throwable ignored) { }
		if (stamp == plateStamp) return;
		plateStamp = stamp;
		if (f == null) {
			plate = null;
			if (!plateWarned) {
				plateWarned = true;
				try { java.nio.file.Files.createDirectories(dir); } catch (Throwable ignored) { }
				com.witch.gui.Toasts.push("Sus Solo: coloque um png/jpg na pasta 'placas' do jogo", false);
			}
			return;
		}
		plateLoading = true;
		final java.nio.file.Path ff = f;
		Thread th = new Thread(() -> {
			int[] px = PlateImage.load(ff, n);
			plateN = n;
			plate = px;
			if (px == null) {
				Minecraft.getInstance().execute(() -> com.witch.gui.Toasts.push("Sus Solo: nao consegui ler a imagem (jpg pode nao funcionar neste aparelho; use png)", false));
			}
			plateLoading = false;
		}, "witch-plate");
		th.setDaemon(true);
		th.start();
	}

	@Override
	protected void onEnable() {
		plateStamp = -1;
		plateWarned = false;
		cooldown = 0;
		cursor = 0;
		alertCount = 0;
		seen.clear();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		lastScan.clear();
		found.clear();
		seen.clear();
		naturalZone.clear();
		view = List.of();
		alertText = null;
		alertUntil = 0L;
		alertCount = 0;
		cursor = 0;
		cooldown = 0;
	}

	private void buildOffsets(int r) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) list.add(new int[]{dx, dz});
		}
		list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		offsets = list.toArray(new int[0][]);
		offsetsRadius = r;
		cursor = 0;
	}

	private long lastReloadNs;

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();

		int rk = reloadKey.key();
		if (rk != 0) {
			boolean down = InputConstants.isKeyDown(mc.getWindow(), rk);
			if (down && !reloadKey.wasDown && mc.gui.screen() == null && System.nanoTime() - lastReloadNs > 3_000_000_000L) {
				lastReloadNs = System.nanoTime(); // no maximo 1 reload a cada 3s
				reset();
			}
			reloadKey.wasDown = down;
		} else {
			reloadKey.wasDown = false;
		}

		if (--plateCheck <= 0) {
			plateCheck = 60;
			refreshPlate();
		}
		if (--cooldown > 0) return;
		cooldown = 1;

		var level = mc.level;
		var player = mc.player;
		if (player == null || level == null) {
			found.clear();
			view = List.of();
			return;
		}

		int r = radius.get();
		if (offsets == null || offsetsRadius != r) buildOffsets(r);

		BlockPos pp = player.blockPosition();
		int pcx = pp.getX() >> 4;
		int pcz = pp.getZ() >> 4;

		long t0 = System.nanoTime();
		int scanned = 0;
		for (int steps = 0; steps < offsets.length && steps < 600; steps++) {
			int[] off = offsets[cursor];
			cursor = (cursor + 1) % offsets.length;
			int cx = pcx + off[0];
			int cz = pcz + off[1];
			if (!level.getChunkSource().hasChunk(cx, cz)) continue;
			long ck = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
			Long due = lastScan.get(ck);
				long gt = level.getGameTime();
				if (due != null && gt < due) continue;
				lastInteresting = false;
				scanChunk(level, cx, cz, player);
				lastScan.put(ck, gt + (lastInteresting ? RESCAN_ACTIVE : RESCAN_QUIET));
			scanned++;
			if (scanned >= MAX_SCANS_PER_TICK || System.nanoTime() - t0 > SCAN_BUDGET_NS) break;
		}

		if (lastScan.size() > 40000) lastScan.clear();
		found.values().removeIf(v -> Math.abs(v[0] - pcx) > r + 1 || Math.abs(v[1] - pcz) > r + 1);
		view = new ArrayList<>(found.values());
	}

	private void scanChunk(net.minecraft.client.multiplayer.ClientLevel level, int cx, int cz,
			net.minecraft.client.player.LocalPlayer player) {
		long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
		// Cidade Antiga (Warden): bioma deep dark, ignora a chunk e as vizinhas
		try {
			boolean deep = false;
			for (int by = -48; by <= -16 && !deep; by += 16) {
				if (by >= level.getMinY() && by <= level.getMaxY()
						&& level.getBiome(new BlockPos(cx * 16 + 8, by, cz * 16 + 8)).is(Biomes.DEEP_DARK)) deep = true;
			}
			if (deep) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long nk = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xFFFFFFFFL);
						naturalZone.add(nk);
						found.remove(nk);
						seen.add(nk);
					}
				}
				return;
			}
		} catch (Throwable ignored) { }
		int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8);
		int y0 = Math.max(level.getMinY(), minY.get());
		int y1 = Math.min(level.getMaxY(), Math.max(y0, ground + above.get()));
		// acc: 0 score, 1 marcadores naturais, 2 lo, 3 hi
		int[] acc = {0, 0, Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 0};
		SectionScan.chunk(level, cx, cz, y0, y1,
				st -> weights.containsKey(st.getBlock()) || trialBlocks.contains(st.getBlock()) || villageMarkers.contains(st.getBlock()),
				(x, y, z, st) -> {
					Block block = st.getBlock();
					if (trialBlocks.contains(block)) acc[1]++;
					if (villageMarkers.contains(block)) acc[4]++;
					Integer w = weights.get(block);
					if (w != null) {
						acc[0] += w;
						if (w <= 4) acc[5] += w;
						if (y < acc[2]) acc[2] = y;
						if (y > acc[3]) acc[3] = y;
					}
				});
		int score = acc[0];
		int markers = acc[1];
		lastInteresting = score > 0 || markers > 0;
		if (acc[4] >= 3) score -= acc[5]; // aldeia: bau/forno/bigorna dela nao contam
		// estrutura natural (trial chamber, cidade antiga, fortaleza): nao e base, e as vizinhas ficam silenciosas
		if (markers >= 6) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					long nk = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xFFFFFFFFL);
					naturalZone.add(nk);
					found.remove(nk);
					seen.add(nk);
				}
			}
			return;
		}
		if (naturalZone.contains(key)) {
			found.remove(key);
			return;
		}

		int chance = (int) Math.round(score * 100.0 / (score + K));
		if (score > 0 && chance >= minChance.get()) {
			boolean isNew = !found.containsKey(key);
			int lo = acc[2] == Integer.MAX_VALUE ? ground : acc[2];
			int hi = acc[3] == Integer.MIN_VALUE ? ground : acc[3];
			found.put(key, new int[]{cx, cz, ground, Math.min(chance, 99), lo, hi});
			if (isNew) {
				tryAlert(key, player, chance, (lo + hi) / 2);
				if (autoWp.get() == 1) Waypoints.addAuto("Solo " + Math.min(chance, 99) + "%", cx * 16 + 8, (lo + hi) / 2, cz * 16 + 8);
			}
		} else {
			found.remove(key);
		}
	}

	private void tryAlert(long key, net.minecraft.client.player.LocalPlayer player, int chance, int midY) {
		if (seen.contains(key)) return;
		seen.add(key);
		if (alertCount >= maxAlerts.get()) return;
		alertCount++;
		alertText = "Solo suspeito: " + Math.min(chance, 99) + "%  (Y " + midY + ")";
		alertUntil = System.currentTimeMillis() + ALERT_DISPLAY_MS;
		if (GuiSettingsModule.sounds()) Sfx.alert(chance >= 75 ? 1.3f : 1.0f); else player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, chance >= 75 ? 2.0f : 1.4f);
	}

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		String text = instance.alertText;
		long until = instance.alertUntil;
		if (text == null || System.currentTimeMillis() > until) return;
		long left = until - System.currentTimeMillis();
		float a = left > 500 ? 1f : Math.max(0f, left / 500f);
		com.witch.gui.Hud.alert(g, text, 0xFF22C55E, a);
	}
}
