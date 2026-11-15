package com.witch.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Estado do efeito "galaxia" nas ferramentas de diamante e netherite. */
public final class GalaxyTools {
	private GalaxyTools() {}

	/** true enquanto o jogo monta as camadas do item que e ferramenta galaxia. */
	public static boolean pending;
	/** true enquanto o ItemFeatureRenderer desenha uma camada galaxia. */
	public static boolean active;

	private static Set<Item> set;

	public static boolean isGalaxy(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (set == null) {
			set = Set.of(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
					Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE);
		}
		return set.contains(stack.getItem());
	}

	/** Lista marcadora: o render de itens reconhece pela classe. */
	public static final class Quads extends ArrayList<BakedQuad> {
		public Quads(List<BakedQuad> src) { super(src); }
	}

	/** Duck interface na camada de render. */
	public interface Flag {
		boolean witch$galaxy();
		void witch$setGalaxy(boolean v);
	}
}
