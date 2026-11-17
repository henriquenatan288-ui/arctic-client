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
	/** true enquanto o ItemFeatureRenderer desenha uma camada galaxia (modo shader). */
	public static boolean active;

	/** Ultimo estado do efeito, mostrado em toast pelo modulo (o usuario nao consegue ler log no celular). */
	public static volatile String status;
	public static volatile boolean statusOk;
	private static void report(String msg, boolean ok) {
		statusOk = ok;
		status = msg;
		System.out.println("[Witch] galaxia: " + msg);
	}

	/** 1 = textura animada (troca o modelo do item; funciona em qualquer GPU), 2 = shader proprio (experimental). */
	public static int mode() {
		var m = com.witch.module.GalaxyModule.instance;
		return m == null ? 1 : m.mode.get();
	}

	private static Set<Item> set;

	private static boolean galaxyItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (set == null) {
			set = Set.of(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
					Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE);
		}
		return set.contains(stack.getItem());
	}

	/** Modo 2 (shader): so com o modulo ligado. Desligado, valem as texturas normais do client. */
	public static boolean isGalaxy(ItemStack stack) {
		if (!com.witch.module.GalaxyModule.on || mode() != 2) return false;
		return galaxyItem(stack);
	}

	private static boolean swapReported;

	/**
	 * Modo 1 (textura animada): devolve uma copia do item apontando para o modelo "galaxy_*" do client
	 * (assets/witch/items). Nao usa shader nem pipeline proprio; so o que o jogo ja desenha.
	 */
	public static ItemStack swap(ItemStack stack) {
		if (!com.witch.module.GalaxyModule.on || mode() != 1 || !galaxyItem(stack)) return stack;
		try {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			ItemStack c = stack.copy();
			c.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
					net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "galaxy_" + id));
			if (!swapReported) { swapReported = true; report("animada OK", true); }
			return c;
		} catch (Throwable t) {
			if (!swapReported) { swapReported = true; report("animada FALHOU", false); System.out.println("[Witch] galaxia: motivo: " + t); }
			return stack;
		}
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
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_ITEMS)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var c = net.minecraft.client.renderer.rendertype.RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			c.setAccessible(true);
			type = c.invoke(null, "witch_galaxy", setup);
			report("shader OK", true);
			return (net.minecraft.client.renderer.rendertype.RenderType) type;
		} catch (Throwable t) {
			failed = true;
			report("shader FALHOU (textura normal)", false);
			System.out.println("[Witch] galaxia: motivo: " + t);
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
