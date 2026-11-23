package com.witch.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.witch.gui.FontUtil;
import com.witch.gui.RenderUtil;
import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import com.witch.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marca chunks com sinais fortes de base de jogador (containers, redstone, decoracao).
 * Cada bloco tem um peso; blocos que nascem naturalmente (tabua, trilho, tocha...) nao contam.
 * Alerta com mensagem no centro + som de level-up quando encontra uma chunk nova.
 * Trial chamber e Cidade Antiga (Warden): ignoradas, sem caixa e sem alerta (vizinhas tambem).
 * Aldeias: baus/fornos/bigornas de aldeia nao contam como base.
 * Chunks coladas viram uma base so: soma obsidiana, ancoras de respawn e cristais (entidades) e marca "possivelmente raidada" / "raidada".
 * Range de chunks 1-40. Limite de avisos 1-10. Cada chunk so alerta uma vez.
 * Keybind "Reload chunks" limpa e rescanneia.
 */
public class SusChunkFinderModule extends Module implements EspSource {
	/** Orcamento de tempo por tick para escanear chunks (varias por tick, em vez de 1 a cada 3 ticks). */
	private static final long SCAN_BUDGET_NS = 4_000_000L;
	private static final int MAX_SCANS_PER_TICK = 16;
	private final java.util.Map<Long, Long> lastScan = new java.util.HashMap<>();
	private static final long ALERT_DISPLAY_MS = 2800L;
	private static final int RAID_COLOR = 0xFFFF7A2E;
	private static final int RAIDED_COLOR = 0xFFEF4444;
	/** Chunks sem nada interessante so sao reescaneadas de vez em quando (mais rapido); com sinal, mais seguido. */
	private static final long RESCAN_QUIET = 2400L;
	private static final long RESCAN_ACTIVE = 600L;

	private static SusChunkFinderModule instance;

	private final IntSetting radius = add(new IntSetting("Chunk radius (so ate onde o servidor manda chunks)", 1, 64, 16, ""));
	private final IntSetting threshold = add(new IntSetting("Min score", 5, 200, 30, ""));
	private final IntSetting vrange = add(new IntSetting("Vertical +/- blocos (400 = coluna toda)", 16, 400, 400, ""));
	private final IntSetting maxAlerts = add(new IntSetting("Max alerts", 1, 10, 3, ""));
	private final ColorSetting color = add(new ColorSetting("Color", 4));
	private final IntSetting autoWp = add(new IntSetting("Auto waypoint (0/1)", 0, 1, 0, ""));
	private final IntSetting raidObs = add(new IntSetting("Obsidiana p/ 'possivelmente raidada' (raidada = 3x)", 20, 2000, 150, ""));
	private final IntSetting showList = add(new IntSetting("Lista de bases na tela (0/1)", 0, 1, 1, ""));
	private final KeybindSetting reloadKey = add(new KeybindSetting("Reload chunks"));

	private final Map<Block, Integer> weights = buildWeights();
	private final Map<Long, int[]> found = new HashMap<>();
	/** Chunks que ja avisaram / trial — nao alertam de novo ate reload ou desligar. */
	private final Set<Long> seen = new HashSet<>();
	/** Chunks de trial chamber e vizinhas (distancia 1): nao alertam nem aparecem como base. */
	private final Set<Long> trialZone = new HashSet<>();
	private final Set<Block> trialBlocks = buildTrialBlocks();
	private volatile List<int[]> view = List.of();
	/** Obsidiana e ancoras por chunk (todas as escaneadas): {obsidiana, ancoras}. */
	private final Map<Long, int[]> extras = new HashMap<>();
	private final Set<Block> villageMarkers = buildVillageMarkers();
	private volatile List<Group> groups = List.of();
	private final Set<Long> raidAlerted = new HashSet<>();
	private int groupCooldown = 0;
	private boolean lastInteresting;

	/** Uma base: chunks coladas somadas. */
	public static final class Group {
		final int chunks;
		final int obsidian, anchors, crystals;
		final int level; // 0 normal, 1 possivelmente raidada, 2 raidada
		final double dist;
		Group(int chunks, int obsidian, int anchors, int crystals, int level, double dist) {
			this.chunks = chunks; this.obsidian = obsidian; this.anchors = anchors; this.crystals = crystals;
			this.level = level; this.dist = dist;
		}
		String text() {
			String t = "Base " + chunks + (chunks == 1 ? " chunk" : " chunks") + " | obs " + obsidian + " | anc " + anchors + " | cri " + crystals;
			return level == 2 ? t + " | RAIDADA" : level == 1 ? t + " | possivelmente raidada" : t;
		}
	}

	private int[][] offsets = null;
	private int offsetsRadius = -1;
	private int cursor = 0;
	private int cooldown = 0;
	private int alertCount = 0;

	private volatile String alertText = null;
	private volatile long alertUntil = 0L;

	public SusChunkFinderModule() {
		super("Sus Chunk Finder", Category.VISUALS);
		instance = this;
	}

	static int weightOf(String n) {
		// quase nunca aparecem sozinhos
		if (n.endsWith("shulker_box") || n.equals("ender_chest") || n.equals("beacon")
				|| n.equals("enchanting_table")) {
			return 10;
		}
		if (n.equals("hopper") || n.equals("dropper") || n.equals("dispenser") || n.equals("observer")
				|| n.equals("piston") || n.equals("sticky_piston") || n.equals("repeater")
				|| n.equals("comparator") || n.equals("redstone_lamp") || n.equals("note_block")
				|| n.equals("daylight_detector")) {
			return 8;
		}
		// containers e estacoes de trabalho
		if (n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel") || n.equals("furnace")
				|| n.equals("blast_furnace") || n.equals("smoker") || n.equals("anvil")
				|| n.equals("chipped_anvil") || n.equals("damaged_anvil") || n.equals("brewing_stand")
				|| n.equals("smithing_table")) {
			return 4;
		}
		// sinais fortes de jogador: cobre encerado, blocos de metal, tnt, ancora, lodestone, conduit
		if (n.startsWith("waxed_") || n.equals("tnt") || n.equals("respawn_anchor") || n.equals("lodestone")
				|| n.equals("conduit") || n.equals("crafter") || n.equals("jukebox")) {
			return 3;
		}
		if (n.equals("iron_block") || n.equals("gold_block") || n.equals("diamond_block") || n.equals("emerald_block")
				|| n.equals("netherite_block") || n.equals("redstone_block") || n.equals("lapis_block")) {
			return 2;
		}
		// decoracao que nao nasce natural
		if (n.equals("crafting_table") || n.endsWith("_wool") || n.contains("concrete")
				|| n.endsWith("stained_glass") || n.endsWith("stained_glass_pane")
				|| n.endsWith("glazed_terracotta")) {
			return 2;
		}
		return 0;
	}

	static Map<Block, Integer> buildWeights() {
		Map<Block, Integer> m = new HashMap<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			int w = weightOf(BuiltInRegistries.BLOCK.getKey(b).getPath());
			if (w > 0) m.put(b, w);
		}
		return m;
	}

	/** Blocos tipicos de trial chamber (cobre/tuff/vault). Varios deles na mesma chunk = camara, nao base. */
	static Set<Block> buildTrialBlocks() {
		Set<Block> set = new HashSet<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			String n = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (n.equals("trial_spawner") || n.equals("vault") || n.contains("copper_bulb")
					|| n.contains("chiseled_copper") || n.contains("copper_grate")
					|| n.equals("tuff_bricks") || n.equals("chiseled_tuff_bricks")
					// cidade antiga e fortaleza: muitos baus, mas nao e base
					|| n.equals("reinforced_deepslate") || n.equals("sculk_shrieker") || n.equals("sculk_catalyst")
					|| n.equals("end_portal_frame")) {
				set.add(b);
			}
		}
		return set;
	}

	/** Blocos que quase so existem em aldeia (jogador raramente junta varios). */
	static Set<Block> buildVillageMarkers() {
		Set<Block> set = new HashSet<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			String n = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (n.equals("bell") || n.equals("hay_block") || n.equals("composter") || n.equals("lectern")
					|| n.equals("loom") || n.equals("stonecutter") || n.equals("fletching_table")
					|| n.equals("cartography_table") || n.equals("grindstone") || n.equals("cauldron")
					|| n.equals("farmland") || n.equals("path") || n.equals("dirt_path")) {
				set.add(b);
			}
		}
		return set;
	}

	static boolean isTrialSpawner(Block b) {
		String n = BuiltInRegistries.BLOCK.getKey(b).getPath();
		return n.equals("trial_spawner");
	}

	private static boolean isObsidianLike(Block b) {
		return b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN;
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void collect(Esp esp) {
		int normal = color.get();
		for (int[] v : view) {
			int x0 = v[0] * 16;
			int z0 = v[1] * 16;
			int argb = v[5] == 2 ? RAIDED_COLOR : v[5] == 1 ? RAID_COLOR : normal;
			esp.box(x0, v[2], z0, x0 + 16, v[3] + 1, z0 + 16, argb, 0f);
		}
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
		cursor = 0;
		alertCount = 0;
		seen.clear();
	}

	@Override
	protected void onDisable() {
		found.clear();
		seen.clear();
		lastScan.clear();
		trialZone.clear();
		extras.clear();
		groups = List.of();
		raidAlerted.clear();
		view = List.of();
		alertText = null;
		alertUntil = 0L;
		alertCount = 0;
	}

	/** Limpa chunks expostas, seen e contador de alertas — força rescan. */
	private long lastReloadNs;

	private void reloadExposed() {
		lastScan.clear();
		found.clear();
		seen.clear();
		trialZone.clear();
		extras.clear();
		groups = List.of();
		raidAlerted.clear();
		view = List.of();
		alertCount = 0;
		alertText = null;
		alertUntil = 0L;
		cursor = 0;
		cooldown = 0;
	}

	private void buildOffsets(int r) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				list.add(new int[]{dx, dz});
			}
		}
		list.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		offsets = list.toArray(new int[0][]);
		offsetsRadius = r;
		cursor = 0;
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();

		// keybind de reload (edge detect, so fora de tela)
		int rk = reloadKey.key();
		if (rk != 0) {
			boolean down = InputConstants.isKeyDown(mc.getWindow(), rk);
			if (down && !reloadKey.wasDown && mc.gui.screen() == null && System.nanoTime() - lastReloadNs > 3_000_000_000L) {
				lastReloadNs = System.nanoTime(); // no maximo 1 reload a cada 3s (tecla de andar, como o A, travava)
				reloadExposed();
			}
			reloadKey.wasDown = down;
		} else {
			reloadKey.wasDown = false;
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
				scanChunk(level, cx, cz, pp.getY(), player);
				lastScan.put(ck, gt + (lastInteresting ? RESCAN_ACTIVE : RESCAN_QUIET));
			scanned++;
			if (scanned >= MAX_SCANS_PER_TICK || System.nanoTime() - t0 > SCAN_BUDGET_NS) break;
		}

		if (lastScan.size() > 40000) lastScan.clear();
		found.values().removeIf(v -> Math.abs(v[0] - pcx) > r + 1 || Math.abs(v[1] - pcz) > r + 1);
		extras.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > r + 2 || Math.abs((int) (long) k - pcz) > r + 2);
		if (--groupCooldown <= 0) {
			groupCooldown = 10;
			buildGroups(level, pcx, pcz);
		}
		view = new ArrayList<>(found.values());
	}

	/** Junta chunks coladas (8 vizinhas) numa base, soma obsidiana/ancoras/cristais e classifica como raidada. */
	private void buildGroups(net.minecraft.client.multiplayer.ClientLevel level, int pcx, int pcz) {
		if (found.isEmpty()) {
			groups = List.of();
			return;
		}
		Map<Long, Integer> crystals = new HashMap<>();
		for (var e : level.entitiesForRendering()) {
			if (e.getType() == EntityType.END_CRYSTAL) {
				crystals.merge(key((int) Math.floor(e.getX()) >> 4, (int) Math.floor(e.getZ()) >> 4), 1, Integer::sum);
			}
		}
		Set<Long> left = new HashSet<>(found.keySet());
		List<Group> out = new ArrayList<>();
		int T = raidObs.get();
		while (!left.isEmpty()) {
			long start = left.iterator().next();
			List<Long> comp = new ArrayList<>();
			java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
			q.add(start);
			left.remove(start);
			while (!q.isEmpty()) {
				long k = q.poll();
				comp.add(k);
				int kx = (int) (k >> 32), kz = (int) k;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long nk = key(kx + dx, kz + dz);
						if (left.remove(nk)) q.add(nk);
					}
				}
			}
			// a base + 1 chunk em volta (obsidiana e cristais de uma base destruida ficam fora das chunks com bau)
			Set<Long> area = new HashSet<>();
			for (long k : comp) {
				int kx = (int) (k >> 32), kz = (int) k;
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) area.add(key(kx + dx, kz + dz));
			}
			int obs = 0, anc = 0, cri = 0;
			double sx = 0, sz = 0;
			for (long k : area) {
				int[] ex = extras.get(k);
				if (ex != null) { obs += ex[0]; anc += ex[1]; }
				Integer c = crystals.get(k);
				if (c != null) cri += c;
			}
			for (long k : comp) { sx += (int) (k >> 32); sz += (int) k; }
			double d = Math.hypot(sx / comp.size() - pcx, sz / comp.size() - pcz);
			int lvl = obs >= T * 3 || (obs >= T && cri >= 2) ? 2 : obs >= T ? 1 : 0;
			for (long k : comp) {
				int[] v = found.get(k);
				if (v != null) v[5] = lvl;
			}
			if (lvl > 0 && raidAlerted.add(start)) {
				alertText = lvl == 2 ? "Base raidada" : "Base possivelmente raidada";
				alertUntil = System.currentTimeMillis() + ALERT_DISPLAY_MS;
			}
			out.add(new Group(comp.size(), obs, anc, cri, lvl, d));
		}
		out.sort((a, b) -> Double.compare(a.dist, b.dist));
		groups = out;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private void scanChunk(net.minecraft.client.multiplayer.ClientLevel level, int cx, int cz, int py,
			net.minecraft.client.player.LocalPlayer player) {
		int vr = vrange.get();
		int minY = Math.max(level.getMinY(), py - vr);
		int maxY = Math.min(level.getMaxY(), py + vr);
		long key = key(cx, cz);
		// Cidade Antiga (Warden) nasce no bioma deep dark: ignora a chunk inteira sem nem escanear
		boolean deep = false;
		try {
			int bx = cx * 16 + 8, bz = cz * 16 + 8;
			for (int by = -48; by <= -16 && !deep; by += 16) {
				if (by >= level.getMinY() && by <= level.getMaxY()
						&& level.getBiome(new BlockPos(bx, by, bz)).is(Biomes.DEEP_DARK)) deep = true;
			}
		} catch (Throwable ignored) { }
		if (deep) {
			ignoreZone(cx, cz);
			return;
		}
		// acc: 0 score, 1 lo, 2 hi, 3 marcadores de estrutura natural, 4 obsidiana, 5 ancoras, 6 marcadores de aldeia, 7 peso "comum" (bau, forno...)
		int[] acc = {0, Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 0, 0, 0, 0};
		boolean[] trial = {false};
		SectionScan.chunk(level, cx, cz, minY, maxY,
				st -> weights.containsKey(st.getBlock()) || trialBlocks.contains(st.getBlock())
						|| villageMarkers.contains(st.getBlock()) || isObsidianLike(st.getBlock()),
				(x, y, z, st) -> {
					Block block = st.getBlock();
					boolean any = false;
					if (isObsidianLike(block)) acc[4]++;
					if (block == Blocks.RESPAWN_ANCHOR) acc[5]++;
					if (villageMarkers.contains(block)) acc[6]++;
					if (isTrialSpawner(block)) { trial[0] = true; any = true; }
					if (trialBlocks.contains(block)) { acc[3]++; any = true; }
					Integer w = weights.get(block);
					if (w != null) {
						acc[0] += w;
						if (w <= 4) acc[7] += w;
						any = true;
					}
					if (any) {
						if (y < acc[1]) acc[1] = y;
						if (y > acc[2]) acc[2] = y;
					}
				});
		int score = acc[0];
		int lo = acc[1];
		int hi = acc[2];
		int markers = acc[3];

		if (acc[4] > 0 || acc[5] > 0) extras.put(key, new int[]{acc[4], acc[5]});
		else extras.remove(key);
		lastInteresting = score > 0 || markers > 0 || acc[4] > 0;

		// trial chamber ou estrutura profunda (varios blocos tipicos): ignorada, sem caixa e sem alerta; vizinhas tambem
		if (trial[0] || markers >= 6) {
			ignoreZone(cx, cz);
			return;
		}
		if (trialZone.contains(key)) {
			found.remove(key);
			seen.add(key);
			return;
		}
		// aldeia: bau, barril, forno, bigorna etc. dela nao contam como base (so os sinais fortes de jogador)
		if (acc[6] >= 3) score -= acc[7];

		if (score >= threshold.get()) {
			boolean isNew = !found.containsKey(key);
			int[] old = found.get(key);
			found.put(key, new int[]{cx, cz, lo, hi, score, old == null ? 0 : old[5]});
			if (isNew) {
				tryAlert(key, player);
				if (autoWp.get() == 1) Waypoints.addAuto("Chunk", cx * 16 + 8, (lo + hi) / 2, cz * 16 + 8);
			}
		} else {
			found.remove(key);
		}
	}

	/** Marca a chunk e as 8 vizinhas como estrutura natural (trial / cidade antiga): somem da lista. */
	private void ignoreZone(int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long nk = key(cx + dx, cz + dz);
				trialZone.add(nk);
				found.remove(nk);
				seen.add(nk);
			}
		}
	}

	private void tryAlert(long key, net.minecraft.client.player.LocalPlayer player) {
		if (seen.contains(key)) return;
		if (alertCount >= maxAlerts.get()) {
			seen.add(key);
			return;
		}

		seen.add(key);
		alertCount++;
		alertText = "Chunk suspeita encontrada";
		alertUntil = System.currentTimeMillis() + ALERT_DISPLAY_MS;

		if (GuiSettingsModule.sounds()) Sfx.alert(1.0f); else player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
	}

	/** Mensagem pequena no centro da tela (chamado pelo HudRenderer). */
	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		instance.drawList(g);
		String text = instance.alertText;
		long until = instance.alertUntil;
		if (text == null || System.currentTimeMillis() > until) return;

		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int tw = FontUtil.width(text);
		int th = FontUtil.height();
		int padX = 10;
		int padY = 6;
		int bw = tw + padX * 2;
		int bh = th + padY * 2;
		int x = (sw - bw) / 2;
		int y = sh / 2 - 40;

		long left = until - System.currentTimeMillis();
		float a = left > 500 ? 1f : Math.max(0f, left / 500f);

		RenderUtil.roundRect(g, x, y, bw, bh, 5, RenderUtil.alpha(0xFF1A1028, 0.82f * a));
		RenderUtil.roundRect(g, x, y, 3, bh, 1, RenderUtil.alpha(0xFFE8C56A, a));
		FontUtil.draw(g, text, x + padX, y + padY, RenderUtil.alpha(0xFFFFF0C8, a));
	}

	/** Lista das bases (chunks coladas somadas), a mais proxima primeiro. */
	private void drawList(GuiGraphicsExtractor g) {
		if (showList.get() == 0) return;
		List<Group> gs = groups;
		if (gs.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		int sh = mc.getWindow().getGuiScaledHeight();
		int n = Math.min(4, gs.size());
		int th = FontUtil.height();
		int y = sh / 2 - 20;
		for (int i = 0; i < n; i++) {
			Group gr = gs.get(i);
			String t = gr.text();
			int w = FontUtil.width(t) + 14;
			int accent = gr.level == 2 ? RAIDED_COLOR : gr.level == 1 ? RAID_COLOR : 0xFFE8C56A;
			RenderUtil.roundRect(g, 6, y, w, th + 8, 4, RenderUtil.alpha(0xFF1A1028, 0.78f));
			RenderUtil.roundRect(g, 6, y, 3, th + 8, 1, accent);
			FontUtil.draw(g, t, 14, y + 4, 0xFFFFF0C8);
			y += th + 11;
		}
	}
}
