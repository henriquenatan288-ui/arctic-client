package com.witch.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Linha brilhante na borda dos itens (shader proprio; um pipeline por cor, cores quantizadas em 8 niveis). */
public final class GlowItems {
	private GlowItems() {}

	private static final Map<Integer, RenderType> CACHE = new HashMap<>();
	private static boolean failed;
	public static volatile String status;
	public static volatile boolean statusOk;

	public static boolean wants(ItemStack stack) {
		return com.witch.module.GlowItemsModule.on && !failed && stack != null && !stack.isEmpty();
	}

	/** RenderType da cor atual do modulo, ou null se o shader nao funcionar (vale o item normal). */
	public static RenderType type() {
		if (failed) return null;
		var m = com.witch.module.GlowItemsModule.instance;
		int rgb = m == null ? 0x22D3EE : m.color.get() & 0xFFFFFF;
		int q = (((rgb >> 16) & 0xFF) >> 5) << 6 | (((rgb >> 8) & 0xFF) >> 5) << 3 | ((rgb & 0xFF) >> 5);
		RenderType c = CACHE.get(q);
		if (c != null) return c;
		try {
			float r = ((q >> 6) & 7) / 7f, g = ((q >> 3) & 7) / 7f, b = (q & 7) / 7f;
			var f = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredField("ITEM_SNIPPET");
			f.setAccessible(true);
			var snippet = (com.mojang.blaze3d.pipeline.RenderPipeline.Snippet) f.get(null);
			var pipe = com.mojang.blaze3d.pipeline.RenderPipeline.builder(snippet)
					.withLocation("pipeline/witch_glow_item_" + q)
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_glow_item"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1f)
					.withShaderDefine("GLOW_R", r)
					.withShaderDefine("GLOW_G", g)
					.withShaderDefine("GLOW_B", b)
					.build();
			try {
				var reg = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				reg.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) reg.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] glow items: register falhou: " + t);
			}
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_ITEMS)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var mk = RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			mk.setAccessible(true);
			c = (RenderType) mk.invoke(null, "witch_glow_item_" + q, setup);
			CACHE.put(q, c);
			status = "shader OK";
			statusOk = true;
			return c;
		} catch (Throwable t) {
			failed = true;
			status = "shader FALHOU (item normal)";
			statusOk = false;
			System.out.println("[Witch] glow items: motivo: " + t);
			t.printStackTrace();
			return null;
		}
	}
}
