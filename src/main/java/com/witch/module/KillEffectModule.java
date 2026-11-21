package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Som e efeito quando voce mata um jogador ou mob (detecta o alvo do seu ultimo golpe morrendo). */
public class KillEffectModule extends Module {
	private final IntSetting players = add(new IntSetting("Players (0/1)", 0, 1, 1, ""));
	private final IntSetting mobs = add(new IntSetting("Mobs (0/1)", 0, 1, 1, ""));
	private final IntSetting sound = add(new IntSetting("Sound (0/1)", 0, 1, 1, ""));
	private final IntSetting soundType = add(new IntSetting("Sound type (1-3 vanilla, 4 witch, 5 impact)", 1, 5, 4, ""));
	private final IntSetting volume = add(new IntSetting("Volume", 1, 100, 70, "%"));
	private final IntSetting effect = add(new IntSetting("Effect (0 nenhum 1 cartas 2 brilho 3 coracoes 4 totem 5 po)", 0, 5, 5, ""));

	private final ColorSetting dustColor = add(new ColorSetting("Cor do po", 1));

	private LivingEntity watching = null;

	// ---- efeito "po": o morto fica transparente e vira po brilhante ----
	private static final long FADE_MS = 900;
	private static final Map<Integer, Long> DISSOLVING = new HashMap<>();
	private static final List<Object[]> DUST = new ArrayList<>(); // {AABB, Integer ticksRestantes}

	/** 1 = normal; menor = mais transparente (so enquanto o mob esta virando po). */
	public static float dissolveAlpha(int entityId) {
		if (DISSOLVING.isEmpty()) return 1f;
		Long t0 = DISSOLVING.get(entityId);
		if (t0 == null) return 1f;
		float k = (System.currentTimeMillis() - t0) / (float) FADE_MS;
		if (k >= 1f) return 0f;
		return 0.5f * (1f - k) + 0.02f;
	}

	private void startDust(LivingEntity v) {
		DISSOLVING.put(v.getId(), System.currentTimeMillis());
		DUST.add(new Object[] {v.getBoundingBox(), 20});
	}

	private void tickDust() {
		long now = System.currentTimeMillis();
		DISSOLVING.values().removeIf(t0 -> now - t0 > FADE_MS + 500);
		var mc = Minecraft.getInstance();
		for (Iterator<Object[]> it = DUST.iterator(); it.hasNext(); ) {
			Object[] d = it.next();
			AABB b = (AABB) d[0];
			int left = (Integer) d[1];
			if (mc.level != null) {
				int base = dustColor.get();
				for (int i = 0; i < 12; i++) {
					double x = b.minX + Math.random() * (b.maxX - b.minX);
					double y = b.minY + Math.random() * (b.maxY - b.minY);
					double z = b.minZ + Math.random() * (b.maxZ - b.minZ);
					boolean spark = Math.random() < 0.35;
					int col = i % 3 == 0 ? com.witch.render.FxShapes.mix(base, 0xFFFFFFFF, 0.65f) : base;
					mc.particleEngine.add(new CardParticle(mc.level, x, y, z,
							(Math.random() - 0.5) * 0.03, 0.02 + Math.random() * 0.05, (Math.random() - 0.5) * 0.03,
							CardParticlesModule.named(spark ? "shape_sparkle" : "shape_dust"),
							spark ? 0.13f : 0.15f, -0.06f, 34 + (int) (Math.random() * 26), col, true));
				}
			}
			if (--left <= 0) it.remove(); else d[1] = left;
		}
	}

	public KillEffectModule() {
		super("Kill Effect", Category.EFFECTS);
		InfoModules.hook();
	}

	private SoundEvent snd() {
		return switch (soundType.get()) {
			case 2 -> SoundEvents.PLAYER_LEVELUP;
			case 3 -> SoundEvents.ARROW_HIT_PLAYER;
			default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
		};
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (!DUST.isEmpty() || !DISSOLVING.isEmpty()) tickDust();
		if (mc.level == null || mc.player == null) return;
		LivingEntity t = InfoModules.lastTarget;
		if (t != null) watching = t;
		if (watching == null) return;
		if (System.currentTimeMillis() - InfoModules.lastHit > 4000) {
			watching = null;
			InfoModules.lastTarget = null;
			return;
		}
		if (watching.isDeadOrDying() || watching.isRemoved()) {
			LivingEntity v = watching;
			watching = null;
			InfoModules.lastTarget = null;
			boolean isPlayer = v instanceof Player;
			if ((isPlayer && players.get() == 0) || (!isPlayer && mobs.get() == 0)) return;
			if (sound.get() == 1 && soundType.get() >= 4) Sfx.play(soundType.get() == 4 ? "kill_1" : "kill_2", 1.0f, volume.get() / 100f);
			else if (sound.get() == 1) mc.getSoundManager().play(SimpleSoundInstance.forUI(snd(), 1.0f + (soundType.get() == 1 ? 0.2f : 0f), volume.get() / 100f));
			double x = v.getX(), y = v.getY() + v.getBbHeight() * 0.5, z = v.getZ();
			switch (effect.get()) {
				case 1 -> CardParticlesModule.burst(x, y, z, 12);
				case 2 -> { for (int i = 0; i < 18; i++) mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, (Math.random() - 0.5) * 0.3, Math.random() * 0.3, (Math.random() - 0.5) * 0.3); }
				case 3 -> { for (int i = 0; i < 8; i++) mc.level.addParticle(ParticleTypes.HEART, x + (Math.random() - 0.5), y + Math.random(), z + (Math.random() - 0.5), 0, 0.05, 0); }
				case 4 -> { for (int i = 0; i < 24; i++) mc.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, (Math.random() - 0.5) * 0.5, Math.random() * 0.5, (Math.random() - 0.5) * 0.5); }
				case 5 -> startDust(v);
				default -> { }
			}
		}
	}
}
