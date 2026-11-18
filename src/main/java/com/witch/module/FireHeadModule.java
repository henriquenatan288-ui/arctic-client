package com.witch.module;

import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

/** Cabeca em chamas: particulas de fogo saindo do topo da cabeca. Cosmetico: so quem usa o Witch Client ve. */
public class FireHeadModule extends Module {
	public final IntSetting target = add(new IntSetting("Alvo (1 eu, 2 outros, 3 todos)", 1, 3, 1, ""));
	public final IntSetting color = add(new IntSetting("Fogo (1 normal, 2 azul/alma, 3 os dois)", 1, 3, 1, ""));
	public final IntSetting amount = add(new IntSetting("Quantidade", 1, 10, 4, ""));
	public final IntSetting embers = add(new IntSetting("Brasas e fumaca (1 nao, 2 sim)", 1, 2, 2, ""));
	public final IntSetting firstPerson = add(new IntSetting("Em primeira pessoa (1 esconder, 2 mostrar)", 1, 2, 1, ""));

	public FireHeadModule() {
		super("Fire Head", Category.AVATAR);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (level == null || mc.player == null) return;
		boolean fp = mc.options.getCameraType().isFirstPerson();
		int t = target.get();
		for (Player p : level.players()) {
			boolean self = p == mc.player;
			if (!(t == 3 || (t == 1 && self) || (t == 2 && !self))) continue;
			if (self && fp && firstPerson.get() == 1) continue;
			if (p.isInvisible() || p.isSpectator() || mc.player.distanceToSqr(p) > 48 * 48) continue;
			double top = p.getY() + p.getBbHeight() - 0.05;
			int n = amount.get();
			for (int i = 0; i < n; i++) {
				double a = Math.random() * Math.PI * 2;
				double r = Math.sqrt(Math.random()) * 0.24;
				double x = p.getX() + Math.cos(a) * r;
				double z = p.getZ() + Math.sin(a) * r;
				double y = top + Math.random() * 0.12;
				ParticleOptions pt = switch (color.get()) {
					case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
					case 3 -> Math.random() < 0.5 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
					default -> ParticleTypes.FLAME;
				};
				level.addParticle(pt, x, y, z, (Math.random() - 0.5) * 0.01, 0.025 + Math.random() * 0.045, (Math.random() - 0.5) * 0.01);
			}
			if (embers.get() == 2) {
				if (Math.random() < 0.35) level.addParticle(ParticleTypes.LAVA, p.getX() + (Math.random() - 0.5) * 0.4, top + 0.1, p.getZ() + (Math.random() - 0.5) * 0.4, 0, 0, 0);
				if (Math.random() < 0.25) level.addParticle(ParticleTypes.SMOKE, p.getX() + (Math.random() - 0.5) * 0.3, top + 0.35, p.getZ() + (Math.random() - 0.5) * 0.3, 0, 0.04, 0);
			}
		}
	}
}
