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
