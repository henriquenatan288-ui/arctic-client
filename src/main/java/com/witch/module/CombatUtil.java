package com.witch.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ajudantes dos modulos de combate (Auto Crystal / Auto Anchor). */
final class CombatUtil {
	private CombatUtil() {}

	/** Alvo mais perto. mode: 1 so jogadores, 2 so mobs, 3 os dois. */
	static LivingEntity target(Minecraft mc, double range, int mode) {
		LivingEntity best = null;
		double bd = range * range;
		for (LivingEntity o : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(range))) {
			if (o == mc.player || !o.isAlive() || o instanceof ArmorStand) continue;
			boolean pl = o instanceof Player;
			if (pl ? (mode & 1) == 0 : (mode & 2) == 0) continue;
			if (o instanceof Player pp && (pp.isSpectator() || pp.isCreative())) continue;
			double d = o.distanceToSqr(mc.player);
			if (d < bd) { bd = d; best = o; }
		}
		return best;
	}

	static Player target(Minecraft mc, double range) {
		Player best = null;
		double bd = range * range;
		for (Player o : mc.level.players()) {
			if (o == mc.player || !o.isAlive() || o.isSpectator() || o.isCreative()) continue;
			double d = o.distanceToSqr(mc.player);
			if (d < bd) { bd = d; best = o; }
		}
		return best;
	}

	/** Seleciona o item na hotbar; false se nao tem. */
	static boolean select(LocalPlayer p, Item item) {
		if (p.getMainHandItem().is(item)) return true;
		for (int i = 0; i < 9; i++) {
			if (p.getInventory().getItem(i).is(item)) { p.getInventory().setSelectedSlot(i); return true; }
		}
		return false;
	}

	/** Seleciona qualquer slot da hotbar que NAO seja o item dado. */
	static void selectOther(LocalPlayer p, Item not) {
		if (!p.getMainHandItem().is(not)) return;
		for (int i = 0; i < 9; i++) {
			if (!p.getInventory().getItem(i).is(not)) { p.getInventory().setSelectedSlot(i); return; }
		}
	}

	static boolean useOn(Minecraft mc, BlockPos pos, Direction face) {
		Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
		if (r.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
		return r.consumesAction();
	}

	static boolean free(Minecraft mc, BlockPos pos, int height) {
		for (int i = 0; i < height; i++) if (!mc.level.getBlockState(pos.above(i)).isAir()) return false;
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
		return mc.level.getEntities(null, box).isEmpty();
	}

	// ---------------- Safe: bloco na frente para segurar a explosao ----------------

	private static final Item[] SHIELD_PREF = {Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.COBBLESTONE, Items.STONE,
			Items.COBBLED_DEEPSLATE, Items.DEEPSLATE, Items.NETHERRACK, Items.BLACKSTONE, Items.DIRT};
	private static int shieldWait;

	private static boolean shieldable(ItemStack st) {
		if (!(st.getItem() instanceof BlockItem bi)) return false;
		Block b = bi.getBlock();
		if (b instanceof FallingBlock || b instanceof EntityBlock) return false;
		if (b == Blocks.GLOWSTONE || b == Blocks.RESPAWN_ANCHOR || b == Blocks.TNT) return false;
		return b.defaultBlockState().isSolidRender();
	}

	/** Slot da hotbar com bloco bom para a protecao (-1 se nao tem). */
	private static int shieldSlot(LocalPlayer p) {
		for (Item it : SHIELD_PREF) {
			for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(it)) return i;
		}
		for (int i = 0; i < 9; i++) if (shieldable(p.getInventory().getItem(i))) return i;
		return -1;
	}

	/**
	 * Garante 2 blocos (pe e cabeca) ao seu lado, do lado da explosao, para segurar o dano.
	 * true = protecao pronta (pode explodir); false = ainda colocando, sem bloco na hotbar, ou nao deu para colocar.
	 */
	static boolean ensureShield(Minecraft mc, LocalPlayer p, Vec3 blast) {
		double dx = blast.x - p.getX(), dz = blast.z - p.getZ();
		if (dx * dx + dz * dz < 1.0) return true; // em cima de voce: bloco do lado nao ajuda
		Direction dir = Math.abs(dx) >= Math.abs(dz)
				? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockPos c0 = p.blockPosition().relative(dir);
		BlockPos[] cells = {c0, c0.above()};
		for (BlockPos c : cells) {
			if (!mc.level.getBlockState(c).canBeReplaced()) continue; // ja tem bloco
			if (shieldWait > 0) { shieldWait--; return false; }
			int slot = shieldSlot(p);
			if (slot < 0) return false;
			p.getInventory().setSelectedSlot(slot);
			// apoio: bloco embaixo (chao) ou ao lado
			for (Direction f : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				BlockPos sup = c.relative(f.getOpposite());
				if (f == Direction.UP) sup = c.below();
				if (!mc.level.getBlockState(sup).isSolidRender()) continue;
				if (useOn(mc, sup, f)) break;
			}
			shieldWait = 2;
			return false;
		}
		return true;
	}
}
