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

	/** Tipo do efeito enquanto o jogo monta as camadas do item: 0 nenhum, 1 ferramenta (so a parte vermelha), 2 item inteiro. */
	public static int pendingKind;
	/** Idem, enquanto o ItemFeatureRenderer desenha a camada (shader). */
	public static int activeKind;
	/** Glow Items: mesmo esquema (pendente ao montar o item, ativo ao desenhar a camada). */
	public static boolean pendingGlow;
	public static boolean activeGlow;

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

	private static Set<Item> extras;

	/** Totem, macas douradas, elytra e pecas de armadura (na mao/chao; no corpo e com o Players Galaxy). */
	private static boolean extraItem(ItemStack stack) {
		if (extras == null) extras = Set.of(Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA);
		if (extras.contains(stack.getItem())) return true;
		String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots");
	}

	/**
	 * Que efeito de shader este item recebe? 0 nenhum, 1 ferramenta mascarada (modo 2), 2 item inteiro.
	 * Ferramentas no modo 1 ficam com 0: la o efeito vem da troca de modelo (swap).
	 */
	public static int kind(ItemStack stack) {
		var g = com.witch.module.GalaxyModule.instance;
		if (g == null || !com.witch.module.GalaxyModule.on || stack == null || stack.isEmpty()) return 0;
		if (galaxyItem(stack)) {
			if (g.mode.get() != 2) return 0;
			return g.whole.get() == 2 ? 2 : 1;
		}
		int sc = g.scope.get();
		if (sc >= 3) return 2;
		return sc == 2 && extraItem(stack) ? 2 : 0;
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

	private static final java.util.Map<Integer, Object> pipes = new java.util.HashMap<>();
	private static final java.util.Set<Integer> failedPipes = new java.util.HashSet<>();
	private static final java.util.Map<Long, net.minecraft.client.renderer.rendertype.RenderType> types = new java.util.HashMap<>();
	private static final java.util.Set<Integer> reported = new java.util.HashSet<>();

	private static int style() {
		var g = com.witch.module.GalaxyModule.instance;
		return g == null ? 1 : g.style.get();
	}

	private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline(int kind, int style) {
		int key = kind * 16 + style;
		Object o = pipes.get(key);
		if (o != null) return (com.mojang.blaze3d.pipeline.RenderPipeline) o;
		if (failedPipes.contains(key)) return null;
		String what = kind == 2 ? "itens" : "ferramentas";
		try {
			var f = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredField("ITEM_SNIPPET");
			f.setAccessible(true);
			var snippet = (com.mojang.blaze3d.pipeline.RenderPipeline.Snippet) f.get(null);
			var b = com.mojang.blaze3d.pipeline.RenderPipeline.builder(snippet)
					.withLocation("pipeline/witch_galaxy_" + kind + "_" + style)
					.withFragmentShader(net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "core/witch_galaxy"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1f)
					.withShaderDefine("STYLE", style);
			if (kind == 2) b.withShaderDefine("ANYITEM");
			var pipe = b.build();
			try {
				var r = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				r.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) r.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] galaxia: register falhou: " + t);
			}
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			pipes.put(key, pipe);
			if (reported.add(kind)) report(what + ": shader OK", true);
			return pipe;
		} catch (Throwable t) {
			failedPipes.add(key);
			report(what + ": shader FALHOU (textura normal)", false);
			System.out.println("[Witch] galaxia: motivo: " + t);
			t.printStackTrace();
			return null;
		}
	}

	/**
	 * RenderType do shader de galaxia (kind 1 ferramenta mascarada, 2 item inteiro) sobre o atlas do sprite
	 * (itens ou blocos), no estilo escolhido. null se o shader falhar (vale o render normal).
	 */
	public static net.minecraft.client.renderer.rendertype.RenderType type(int kind, net.minecraft.resources.Identifier atlas) {
		int style = style();
		long key = ((long) (kind * 16 + style) << 32) | (atlas.hashCode() & 0xffffffffL);
		var c = types.get(key);
		if (c != null) return c;
		var pipe = pipeline(kind, style);
		if (pipe == null) return null;
		try {
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", atlas)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var m = net.minecraft.client.renderer.rendertype.RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			m.setAccessible(true);
			c = (net.minecraft.client.renderer.rendertype.RenderType) m.invoke(null, "witch_galaxy", setup);
			types.put(key, c);
			return c;
		} catch (Throwable t) {
			failedPipes.add(kind * 16 + style);
			report("shader FALHOU (textura normal)", false);
			System.out.println("[Witch] galaxia: type falhou: " + t);
			return null;
		}
	}


	/** Lista marcadora: o render de itens reconhece pela classe. */
	public static final class Quads extends ArrayList<BakedQuad> {
		public final int kind;
		public Quads(List<BakedQuad> src, int kind) { super(src); this.kind = kind; }
	}

	/** Lista marcadora do Glow Items. */
	public static final class GlowQuads extends ArrayList<BakedQuad> {
		public GlowQuads(List<BakedQuad> src) { super(src); }
	}

	/** Duck interface na camada de render. */
	public interface Flag {
		int witch$galaxy();
		void witch$setGalaxy(int kind);
		boolean witch$glow();
		void witch$setGlow(boolean v);
	}
}
