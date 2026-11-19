package com.witch.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** RenderType proprio (shader de galaxia no formato de entidade) por textura de skin. */
public final class PlayersGalaxy {
	private PlayersGalaxy() {}

	private static final Object[] PIPES = new Object[8 * 4 * 2];
	private static final boolean[] FAILED = new boolean[8 * 4 * 2];
	@SuppressWarnings("unchecked")
	private static final Map<Identifier, RenderType>[] CACHES = new Map[8 * 4 * 2];
	public static volatile String status;
	public static volatile boolean statusOk;
	private static boolean reportedOk;
	/** O corpo que esta sendo desenhado agora (e suas camadas de armadura/elytra) e um jogador que recebe galaxia? */
	public static int curBody; // 0 nenhum, 1 jogador com galaxia, 2 outro ser (armor stand, mob...)
	/** Idem para o modulo Elytra Shader (independente do Players Galaxy). */
	public static int curWings;

	private static int variant() {
		var m = com.witch.module.PlayersGalaxyModule.instance;
		return m == null ? (1 << 2 | 1) : (m.style.get() << 2 | m.relief.get());
	}

	private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline(int v, boolean noCull) {
		int key = v * 2 + (noCull ? 1 : 0);
		if (PIPES[key] != null) return (com.mojang.blaze3d.pipeline.RenderPipeline) PIPES[key];
		if (FAILED[key]) return null;
		try {
			var b = com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_SNIPPET)
					.withLocation("pipeline/witch_galaxy_entity_" + v + (noCull ? "_nc" : ""))
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_galaxy_entity"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1f)
					.withShaderDefine("STYLE", v >> 2)
					.withShaderDefine("RELIEF", v & 3)
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1);
			if (noCull) b.withCull(false); // armadura e elytra sao dupla face
			var pipe = b.build();
			try {
				var r = net.minecraft.client.renderer.RenderPipelines.class.getDeclaredMethod("register", com.mojang.blaze3d.pipeline.RenderPipeline.class);
				r.setAccessible(true);
				pipe = (com.mojang.blaze3d.pipeline.RenderPipeline) r.invoke(null, pipe);
			} catch (Throwable t) {
				System.out.println("[Witch] players galaxy: register falhou: " + t);
			}
			var compiled = com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(pipe);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou neste aparelho");
			PIPES[key] = pipe;
			if (!reportedOk) {
				reportedOk = true;
				status = "shader OK";
				statusOk = true;
			}
			return pipe;
		} catch (Throwable t) {
			FAILED[key] = true;
			status = "shader FALHOU (skin normal)";
			statusOk = false;
			System.out.println("[Witch] players galaxy: motivo: " + t);
			t.printStackTrace();
			return null;
		}
	}

	/** RenderType da textura (skin, armadura ou elytra) com galaxia, ou null se o shader nao funcionar (ai vale o render normal). */
	public static RenderType type(Identifier tex, boolean noCull) {
		return type(tex, noCull, variant());
	}

	/** Mesma coisa com variante explicita (estilo << 2 | relevo). */
	public static RenderType type(Identifier tex, boolean noCull, int v) {
		if (tex == null) return null;
		int key = v * 2 + (noCull ? 1 : 0);
		Map<Identifier, RenderType> cache = CACHES[key];
		if (cache == null) cache = CACHES[key] = new HashMap<>();
		RenderType c = cache.get(tex);
		if (c != null) return c;
		var pipe = pipeline(v, noCull);
		if (pipe == null) return null;
		try {
			var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipe)
					.withTexture("Sampler0", tex)
					.useLightmap().useOverlay().affectsCrumbling()
					.setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
					.createRenderSetup();
			var m = RenderType.class.getDeclaredMethod("create", String.class, net.minecraft.client.renderer.rendertype.RenderSetup.class);
			m.setAccessible(true);
			c = (RenderType) m.invoke(null, "witch_galaxy_entity", setup);
			cache.put(tex, c);
			return c;
		} catch (Throwable t) {
			FAILED[key] = true;
			status = "shader FALHOU (skin normal)";
			statusOk = false;
			System.out.println("[Witch] players galaxy: type falhou: " + t);
			return null;
		}
	}

	public static RenderType type(Identifier skin) {
		return type(skin, false);
	}

	/** Armadura ou elytra/asas do corpo atual com galaxia, conforme as opcoes do modulo (1 normal, 2 so jogadores, 3 todos os seres). */
	public static RenderType equipment(Identifier tex) {
		if (tex != null && curWings != 0 && com.witch.module.ElytraShaderModule.on) {
			String p = tex.getPath();
			if (p.contains("wings") || p.contains("elytra")) {
				var e = com.witch.module.ElytraShaderModule.instance;
				RenderType t = type(tex, true, e.style.get() << 2 | e.relief.get());
				if (t != null) return t;
			}
		}
		var m = com.witch.module.PlayersGalaxyModule.instance;
		int body = curBody;
		if (m == null || body == 0 || tex == null || !com.witch.module.PlayersGalaxyModule.on) return null;
		boolean armor = tex.getPath().contains("humanoid");
		int opt = armor ? m.armor.get() : m.wings.get();
		if (opt == 1 || (opt == 2 && body != 1)) return null;
		return type(tex, true);
	}

	/** Esta skin/entidade deve receber galaxia? (modulo ligado + alvo) */
	public static boolean wants(boolean isSelf) {
		var m = com.witch.module.PlayersGalaxyModule.instance;
		if (m == null || !com.witch.module.PlayersGalaxyModule.on) return false;
		int t = m.target.get(); // 1 so eu, 2 so outros, 3 todos
		return t == 3 || (t == 1 && isSelf) || (t == 2 && !isSelf);
	}
}
