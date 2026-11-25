package com.witch.module;

/** Troca todas as espadas (madeira ate netherite) por um modelo 3D de katana original. Vale na mao, no chao e no inventario. */
public class KatanaModule extends Module {
	public static volatile boolean on;

	public KatanaModule() {
		super("Katana 3D", Category.AVATAR);
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
			c.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
					net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "katana_" + id));
			return c;
		} catch (Throwable t) {
			return null;
		}
	}
}
