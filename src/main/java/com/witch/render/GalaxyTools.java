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

	private static Object type;
	private static boolean failed;

	/** RenderType proprio (shader de galaxia sobre o formato do item), ou null se falhar. */
	public static net.minecraft.client.renderer.rendertype.RenderType type() {
		if (type != null) return (net.minecraft.client.renderer.rendertype.RenderType) type;
		if (failed) return null;
		try {
			var f = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredField("ITEM_SNIPPET");
			f.setAccessible(true);
			var snippet = (com.mojang.blaze3d.pipeline.RenderPipeline.Snippet) f.get(null);
			var pipe = com.mojang.blaze3d.pipeline.RenderPipeline.builder(snippet)
					.withLocation("pipeline/witch_galaxy")
					.withFragmentShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "core/witch_galaxy"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1f)
					.build();
			try {
				var r = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				r.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) r.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] galaxia: register falhou: " + t);
			}
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_ITEMS)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var c = net.minecraft.client.renderer.rendertype.RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			c.setAccessible(true);
			type = c.invoke(null, "witch_galaxy", setup);
			System.out.println("[Witch] galaxia: pipeline criado");
			return (net.minecraft.client.renderer.rendertype.RenderType) type;
		} catch (Throwable t) {
			failed = true;
			System.out.println("[Witch] galaxia: falhou, usando fallback: " + t);
			t.printStackTrace();
			return null;
		}
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
