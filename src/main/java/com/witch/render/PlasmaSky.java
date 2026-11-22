package com.witch.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Ceu de plasma (shader proprio) desenhado no lugar do disco do ceu. Reaproveita o buffer do disco do jogo
 * (formato POSITION, triangle fan) e o mesmo tipo de passe de render que o ceu normal usa.
 * Se qualquer coisa falhar, devolve false e o ceu normal continua.
 */
public final class PlasmaSky {
	private PlasmaSky() {}

	private static RenderPipeline pipe;
	private static boolean failed;
	private static boolean reported;

	private static RenderPipeline.Snippet snippet(String name) throws Exception {
		var f = RenderPipelines.class.getDeclaredField(name);
		f.setAccessible(true);
		return (RenderPipeline.Snippet) f.get(null);
	}

	private static RenderPipeline pipeline() {
		if (pipe != null || failed) return pipe;
		try {
			var b = RenderPipeline.builder(snippet("MATRICES_FOG_SNIPPET"), snippet("GLOBALS_SNIPPET"))
					.withLocation("pipeline/witch_plasma_sky")
					.withVertexShader(Identifier.fromNamespaceAndPath("witch", "core/witch_plasma"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_plasma"))
					.withVertexBinding(0, DefaultVertexFormat.POSITION)
					.withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLE_FAN);
			var p = b.build();
			try {
				var r = RenderPipelines.class.getDeclaredMethod("register", RenderPipeline.class);
				r.setAccessible(true);
				p = (RenderPipeline) r.invoke(null, p);
			} catch (Throwable t) {
				System.out.println("[Witch] plasma: register falhou: " + t);
			}
			var compiled = RenderSystem.getDevice().precompilePipeline(p);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou");
			pipe = p;
			System.out.println("[WDBG] plasma sky: shader OK");
		} catch (Throwable t) {
			failed = true;
			System.out.println("[WDBG] plasma sky: FALHOU: " + t);
			t.printStackTrace();
		}
		return pipe;
	}

	/** ARGB: rgb = cor do plasma, a = intensidade. Retorna true se desenhou (o disco normal deve ser cancelado). */
	public static boolean draw(RenderTarget target, GpuBuffer disc, int argb) {
		if (failed || target == null || disc == null) return false;
		RenderPipeline p = pipeline();
		if (p == null) return false;
		try {
			GpuBufferSlice t = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), ARGB.vector4fFromARGB32(argb));
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					() -> "witch plasma sky", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(p);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", t);
				pass.setVertexBuffer(0, disc.slice());
				pass.draw(10, 1, 0, 0);
			}
			if (!reported) { reported = true; System.out.println("[WDBG] plasma sky: desenhando"); }
			return true;
		} catch (Throwable t) {
			failed = true;
			System.out.println("[WDBG] plasma sky: erro ao desenhar: " + t);
			t.printStackTrace();
			return false;
		}
	}
}
