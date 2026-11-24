package com.witch.module;

import com.witch.gui.FontUtil;
import com.witch.gui.RenderUtil;
import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entity Alert: avisa quando ha villager no subsolo (salao de troca, prisao ou base escondida).
 * Cada villager e marcado uma vez com um retangulo pequeno em rosa neon; o aviso na tela e o som tambem tocam uma vez por villager.
 * So enxerga entidades que o servidor enviou (distancia de entidades do cliente).
 */
public class EntityAlertModule extends Module implements EspSource {
	private static final int NEON_PINK = 0xFFFF2BD6;
	private static final long ALERT_MS = 4500L;

	private static EntityAlertModule instance;

	private final IntSetting depth = add(new IntSetting("Profundidade abaixo do solo", 3, 60, 8, " blocos"));
	private final IntSetting range = add(new IntSetting("Distancia max", 16, 256, 128, ""));
	private final IntSetting fill = add(new IntSetting("Fill opacity", 0, 60, 25, "%"));
	private final IntSetting autoWp = add(new IntSetting("Waypoint automatico (0/1)", 0, 1, 0, ""));

	/** Villagers ja avisados (uma vez so cada). */
	private final Set<UUID> alerted = new HashSet<>();
	/** Marcados agora: {x, y, z, altura*100} e o uuid para sumir quando sair do subsolo. */
	private volatile List<double[]> marks = List.of();
	private volatile String alertText;
	private volatile long alertUntil;
	private int cooldown;

	public EntityAlertModule() {
		super("Entity Alert", Category.VISUALS);
		instance = this;
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	protected void onDisable() {
		alerted.clear();
		marks = List.of();
		alertText = null;
	}

	@Override
	public void collect(Esp esp) {
		float fa = fill.get() / 100f;
		for (double[] m : marks) {
			double w = 0.33, h = m[3] / 100.0;
			// retangulo pequeno e justo no villager
			esp.box(m[0] - w, m[1], m[2] - w, m[0] + w, m[1] + h, m[2] + w, NEON_PINK, fa);
		}
	}

	@Override
	public void onTick() {
		if (--cooldown > 0) return;
		cooldown = 10;
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		var me = mc.player;
		if (level == null || me == null) {
			marks = List.of();
			return;
		}
		double max2 = (double) range.get() * range.get();
		int need = depth.get();
		List<double[]> out = new java.util.ArrayList<>();
		Set<UUID> now = new HashSet<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (var e : level.entitiesForRendering()) {
			if (!(e instanceof Villager v) || !v.isAlive()) continue;
			if (v.distanceToSqr(me) > max2) continue;
			p.set(v.getBlockX(), v.getBlockY(), v.getBlockZ());
			int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ());
			// no subsolo: bem abaixo da superficie e sem ver o ceu (poco aberto ou encosta nao contam)
			if (v.getBlockY() > surface - need || level.canSeeSky(p)) continue;
			now.add(v.getUUID());
			out.add(new double[]{v.getX(), v.getY(), v.getZ(), v.getBbHeight() * 100.0});
			if (alerted.add(v.getUUID())) {
				alertText = "Villager no subsolo  (Y " + v.getBlockY() + ")";
				alertUntil = System.currentTimeMillis() + ALERT_MS;
				if (GuiSettingsModule.sounds()) Sfx.alert(1.1f);
				else me.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
				if (autoWp.get() == 1) Waypoints.addAuto("Villager", v.getBlockX(), v.getBlockY(), v.getBlockZ());
			}
		}
		// quem subiu a superficie ou foi embora pode ser avisado de novo se voltar ao subsolo
		alerted.retainAll(now);
		marks = out;
	}

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		String text = instance.alertText;
		if (text == null || System.currentTimeMillis() > instance.alertUntil) return;
		long left = instance.alertUntil - System.currentTimeMillis();
		float a = left > 500 ? 1f : Math.max(0f, left / 500f);
		com.witch.gui.Hud.alert(g, text, NEON_PINK, a);
	}
}
