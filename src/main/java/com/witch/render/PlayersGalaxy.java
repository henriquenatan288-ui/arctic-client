package com.witch.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** RenderType proprio (shader de galaxia no formato de entidade) por textura de skin. */
public final class PlayersGalaxy {
	private PlayersGalaxy() {}

	private static Object pipeline;
	private static boolean failed;
	private static final Map<Identifier, RenderType> CACHE = new HashMap<>();
	public static volatile String status;
	public static volatile boolean statusOk;

	private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
		if (pipeline != null) return (com.mojang.blaze3d.pipeline.RenderPipeline) pipeline;
		if (failed) return null;
		try {
			var pipe = com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_SNIPPET)
					.withLocation("pipeline/witch_galaxy_entity")
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_galaxy_entity"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1f)
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
					.build();
			try {
				var r = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				r.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) r.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] players galaxy: register falhou: " + t);
			}
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			pipeline = pipe;
			status = "shader OK";
			statusOk = true;
			return pipe;
		} catch (Throwable t) {
			failed = true;
			status = "shader FALHOU (skin normal)";
			statusOk = false;
			System.out.println("[Witch] players galaxy: motivo: " + t);
			t.printStackTrace();
			return null;
		}
	}

	/** RenderType da skin com galaxia, ou null se o shader nao funcionar (ai vale o render normal). */
	public static RenderType type(Identifier skin) {
		if (skin == null) return null;
		RenderType c = CACHE.get(skin);
		if (c != null) return c;
		var pipe = pipeline();
		if (pipe == null) return null;
		try {
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", skin)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var m = RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			m.setAccessible(true);
			c = (RenderType) m.invoke(null, "witch_galaxy_entity", setup);
			CACHE.put(skin, c);
			return c;
		} catch (Throwable t) {
			failed = true;
			status = "shader FALHOU (skin normal)";
			statusOk = false;
			System.out.println("[Witch] players galaxy: type falhou: " + t);
			return null;
		}
	}

	/** Esta skin/entidade deve receber galaxia? (modulo ligado + alvo) */
	public static boolean wants(boolean isSelf) {
		var m = com.witch.module.PlayersGalaxyModule.instance;
		if (m == null || !com.witch.module.PlayersGalaxyModule.on) return false;
		int t = m.target.get(); // 1 so eu, 2 so outros, 3 todos
		return t == 3 || (t == 1 && isSelf) || (t == 2 && !isSelf);
	}
}
