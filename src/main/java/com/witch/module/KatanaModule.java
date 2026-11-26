package com.witch.module;

import com.witch.setting.IntSetting;

/** Troca todas as espadas (madeira ate netherite) por um modelo 3D de katana original. Vale na mao, no chao e no inventario. */
public class KatanaModule extends Module {
	public static volatile boolean on;

	public static KatanaModule instance;
	public final IntSetting fire = add(new IntSetting("Fogo na lamina (0 nao 1 fogo 2 fogo azul)", 0, 2, 0, ""));
	public final IntSetting slash = add(new IntSetting("Golpe de samurai (0/1)", 0, 1, 1, ""));
	public final IntSetting slashPower = add(new IntSetting("Golpe - amplitude", 1, 10, 6, ""));

	public KatanaModule() {
		super("Katana 3D", Category.AVATAR);
		instance = this;
	}

	/** O jogador local segura uma espada (que vira katana) e o golpe de samurai esta ligado. */
	public static boolean slashing() {
		var m = instance;
		if (m == null || !on || m.slash.get() == 0) return false;
		var p = net.minecraft.client.Minecraft.getInstance().player;
		return p != null && swordId(p.getMainHandItem()) != null;
	}

	static String swordId(net.minecraft.world.item.ItemStack st) {
		if (st == null || st.isEmpty()) return null;
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		return switch (id) {
			case "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword" -> id;
			default -> null;
		};
	}

	@Override
	public void onTick() {
		var m = instance;
		var mc = net.minecraft.client.Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.level == null || fire.get() == 0 || swordId(p.getMainHandItem()) == null || mc.isPaused()) return;
		// chamas subindo pela lamina (aproximacao da posicao da mao; vale em 1a e 3a pessoa)
		var look = p.getViewVector(1f);
		double rxx = -look.z, rzz = look.x, rl = Math.max(1e-4, Math.sqrt(rxx * rxx + rzz * rzz));
		rxx /= rl; rzz /= rl;
		var eye = p.getEyePosition();
		boolean first = mc.options.getCameraType().isFirstPerson();
		if (first) return; // em 1a pessoa a particula nasce colada na camera (enorme): ali vale so o fogo do modelo
		double bx = eye.x + look.x * (first ? 0.55 : 0.35) + rxx * 0.34, by = eye.y - (first ? 0.30 : 0.55) + look.y * 0.3, bz = eye.z + look.z * (first ? 0.55 : 0.35) + rzz * 0.34;
		var type = fire.get() == 2 ? net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME : net.minecraft.core.particles.ParticleTypes.FLAME;
		var rnd = p.getRandom();
		for (int i = 0; i < 3; i++) {
			double t = 0.25 + rnd.nextDouble() * 1.1;
			mc.level.addParticle(type, bx + look.x * t * 0.4 + (rnd.nextDouble() - 0.5) * 0.06, by + t * 0.8, bz + look.z * t * 0.4 + (rnd.nextDouble() - 0.5) * 0.06,
					(rnd.nextDouble() - 0.5) * 0.01, 0.012 + rnd.nextDouble() * 0.01, (rnd.nextDouble() - 0.5) * 0.01);
		}
	}

	@Override
	protected void onEnable() {
		on = true;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	/** Copia do item apontando para o modelo katana_*; o proprio item nao muda. null se nao se aplica. */
	public static net.minecraft.world.item.ItemStack swap(net.minecraft.world.item.ItemStack stack) {
		if (!on || stack == null || stack.isEmpty()) return null;
		try {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (!id.endsWith("_sword")) return null;
			switch (id) {
				case "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword" -> { }
				default -> { return null; }
			}
			var c = stack.copy();
			int fm = instance == null ? 0 : instance.fire.get();
			String pre = fm == 1 ? "katana_fire_" : fm == 2 ? "katana_sfire_" : "katana_";
			c.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
					net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", pre + id));
			return c;
		} catch (Throwable t) {
			return null;
		}
	}
}
