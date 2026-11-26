package com.witch.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** RenderType do fogo procedural da katana (shader proprio, translucido, no atlas de blocos). Falhou? null: o jogo desenha a textura normal. */
public final class FireItems {
	private FireItems() {}

	private static final Map<Integer, RenderType> CACHE = new HashMap<>();
	private static boolean failed;
	public static volatile String status;

	public static RenderType type(boolean blue) {
		if (failed) return null;
		int key = blue ? 1 : 0;
		RenderType c = CACHE.get(key);
		if (c != null) return c;
		try {
			var f = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredField("ITEM_SNIPPET");
			f.setAccessible(true);
			var snippet = (com.mojang.blaze3d.pipeline.RenderPipeline.Snippet) f.get(null);
			var pipe = com.mojang.blaze3d.pipeline.RenderPipeline.builder(snippet)
					.withLocation("pipeline/witch_katana_fire_" + key)
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_katana_fire"))
					.withShaderDefine("BLUE", key)
					.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
					.withCull(false)
					.build();
			try {
				var reg = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				reg.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) reg.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] katana fire: register falhou: " + t);
			}
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var mk = RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			mk.setAccessible(true);
			c = (RenderType) mk.invoke(null, "witch_katana_fire_" + key, setup);
			CACHE.put(key, c);
			status = "fogo shader OK";
			return c;
		} catch (Throwable t) {
			failed = true;
			status = "fogo shader FALHOU";
			System.out.println("[Witch] katana fire: motivo: " + t);
			t.printStackTrace();
			return null;
		}
	}
}
