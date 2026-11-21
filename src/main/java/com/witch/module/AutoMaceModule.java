package com.witch.module;

import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Caindo com um alvo ao alcance: troca pra mace e ataca (smash). Depois volta pro slot anterior.
 * Por padrao o alvo e o mais perto ao alcance (nao precisa estar exatamente na mira: na queda a mira
 * quase nunca ficava no alvo e o modulo raramente atacava). "Aim only" volta ao modo antigo.
 */
public class AutoMaceModule extends Module {
	private final IntSetting minFall = add(new IntSetting("Min fall", 2, 30, 3, " blocks"));
	private final IntSetting players = add(new IntSetting("Players (0/1)", 0, 1, 1, ""));
	private final IntSetting mobs = add(new IntSetting("Mobs (0/1)", 0, 1, 0, ""));
	private final IntSetting aimOnly = add(new IntSetting("Aim only (0/1)", 0, 1, 0, ""));

	private int back = -1;

	public AutoMaceModule() {
		super("Auto Mace", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		back = -1;
	}

	private LivingEntity pick(Minecraft mc, net.minecraft.client.player.LocalPlayer p) {
		// 1) o que esta na mira, se valido
		if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && valid(p, le)) return le;
		if (aimOnly.get() == 1) return null;
		// 2) o mais perto ao alcance de ataque
		LivingEntity best = null;
		double bd = 1e9;
		for (LivingEntity le : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(p.entityInteractionRange() + 1.5))) {
			if (!valid(p, le)) continue;
			double d = le.getBoundingBox().distanceToSqr(p.getEyePosition());
			if (d < bd) { bd = d; best = le; }
		}
		return best;
	}

	private boolean valid(net.minecraft.client.player.LocalPlayer p, LivingEntity le) {
		if (le == p || !le.isAlive() || le instanceof ArmorStand) return false;
		if (le instanceof Player pl) {
			if (players.get() == 0 || pl.isSpectator() || pl.isCreative()) return false;
		} else if (mobs.get() == 0) {
			return false;
		}
		return p.isWithinEntityInteractionRange(le, 0.0);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
		if (back >= 0 && !p.getMainHandItem().is(Items.MACE)) back = -1;
		if (!p.isAlive() || p.isSpectator() || p.onGround() || p.isFallFlying() || p.getAbilities().flying) {
			restore(p);
			return;
		}
		if (p.fallDistance < minFall.get()) { restore(p); return; }
		LivingEntity le = pick(mc, p);
		if (le == null) { restore(p); return; }

		if (!p.getMainHandItem().is(Items.MACE)) {
			int prev = p.getInventory().getSelectedSlot();
			if (!CombatUtil.select(p, Items.MACE)) return;
			back = prev;
		}
		mc.gameMode.attack(p, le);
		p.swing(InteractionHand.MAIN_HAND);
	}

	private void restore(net.minecraft.client.player.LocalPlayer p) {
		if (back >= 0 && back < 9) p.getInventory().setSelectedSlot(back);
		back = -1;
	}
}
