package com.witch.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Reflexo de tela (SSR) para o Mirror Floor: copia o quadro (cor + profundidade), desenha os pisos refletivos
 * e, para cada pixel do piso, caminha o raio refletido pela profundidade da copia. O que o raio acha
 * (blocos, mobs, skins) vira o reflexo. So reflete o que esta na tela. Se algo falhar, desliga e o brilho simples assume.
 */
public final class MirrorSsr {
	private MirrorSsr() {}

	/** Desenhou pelo menos um quadro sem erro. */
	public static volatile boolean working;
	private static volatile boolean failed;
	private static boolean inited;
	private static boolean logged;
	private static RenderPipeline pipe;
	private static TextureTarget copy;
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();
	private static final StagedVertexBuffer STAGED = new StagedVertexBuffer(() -> "Witch SSR Buffer", RenderType.SMALL_BUFFER_SIZE);

	/** Quem fornece os pisos e os parametros (o modulo Mirror Floor). */
	public interface Source {
		boolean ssrWanted();

		/** cada item: {x, y, z, forca 0..1} */
		Collection<float[]> ssrTiles();

		/** cor de fallback (rgb 0..1) quando o raio nao acha nada */
		float[] ssrTint();
	}

	public static Source source;

	public static void init() {
		if (inited) return;
		inited = true;
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(MirrorSsr::onRender);
	}

	private static RenderPipeline pipeline() {
		if (pipe != null || failed) return pipe;
		try {
			var p = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation("pipeline/witch_mirror_ssr")
					.withVertexShader(Identifier.fromNamespaceAndPath("witch", "core/witch_ssr"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("witch", "core/witch_ssr"))
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0_SAMPLER1)
					.withCull(false)
					.withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.build();
			try {
				var r = RenderPipelines.class.getDeclaredMethod("register", RenderPipeline.class);
				r.setAccessible(true);
				p = (RenderPipeline) r.invoke(null, p);
			} catch (Throwable t) {
				System.out.println("[WDBG] mirror ssr: register falhou: " + t);
			}
			var compiled = RenderSystem.getDevice().precompilePipeline(p);
			if (compiled == null || !compiled.isValid()) throw new IllegalStateException("shader nao compilou");
			pipe = p;
			System.out.println("[WDBG] mirror ssr: shader OK");
		} catch (Throwable t) {
			fail("pipeline", t);
		}
		return pipe;
	}

	private static void fail(String where, Throwable t) {
		failed = true;
		working = false;
		System.out.println("[WDBG] mirror ssr: FALHOU em " + where + ": " + t);
		t.printStackTrace();
	}

	private static void onRender(LevelRenderContext context) {
		Source src = source;
		if (failed || src == null || !src.ssrWanted()) {
			if (!failed) working = false;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Collection<float[]> tiles = src.ssrTiles();
		if (tiles.isEmpty()) return;
		RenderPipeline p = pipeline();
		if (p == null || p.getVertexFormatBinding(0) == null) return;
		try {
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			if (main.getColorTextureView() == null || main.getDepthTextureView() == null) return;
			if (copy == null) {
				copy = new TextureTarget("witch ssr", main.width, main.height, true, main.getColorTexture().getFormat());
			} else if (copy.width != main.width || copy.height != main.height) {
				copy.resize(main.width, main.height);
			}
			var enc = RenderSystem.getDevice().createCommandEncoder();
			enc.copyTextureToTexture(main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			copy.copyDepthFrom(main);

			PoseStack pose = context.poseStack();
			Vec3 cam = context.levelState().cameraRenderState.pos;
			Matrix4fc m = new org.joml.Matrix4f(pose.last().pose()).translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
			Vector3f up = m.transformDirection(0f, 1f, 0f, new Vector3f()).normalize();
			float nr = up.x * 0.5f + 0.5f, ng = up.y * 0.5f + 0.5f, nb = up.z * 0.5f + 0.5f;

			var binding = p.getVertexFormatBinding(0);
			var primitive = p.getPrimitiveTopology();
			var sorting = primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null;
			StagedVertexBuffer.Draw draw = STAGED.appendDraw(binding, primitive, sorting);
			VertexConsumer b = STAGED.getVertexBuilder(draw);
			for (float[] t : tiles) {
				float x = t[0], y = t[1] + 0.012f, z = t[2], a = t[3];
				b.addVertex(m, x, y, z).setColor(nr, ng, nb, a);
				b.addVertex(m, x, y, z + 1f).setColor(nr, ng, nb, a);
				b.addVertex(m, x + 1f, y, z + 1f).setColor(nr, ng, nb, a);
				b.addVertex(m, x + 1f, y, z).setColor(nr, ng, nb, a);
			}
			STAGED.upload();
			StagedVertexBuffer.ExecuteInfo info = STAGED.getExecuteInfo(draw);
			if (info != null) {
				float[] tint = src.ssrTint();
				var dyn = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
						new Vector4f(tint[0], tint[1], tint[2], 1f), MODEL_OFFSET, TEXTURE_MATRIX);
				var lin = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
				var nea = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				try (RenderPass pass = enc.createRenderPass(() -> "witch mirror ssr", main.getColorTextureView(), Optional.empty(),
						main.getDepthTextureView(), OptionalDouble.empty())) {
					pass.setPipeline(p);
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", dyn);
					pass.bindTexture("Sampler0", copy.getColorTextureView(), lin);
					pass.bindTexture("Sampler1", copy.getDepthTextureView(), nea);
					pass.setVertexBuffer(0, info.vertexBuffer().slice());
					pass.setIndexBuffer(info.indexBuffer(), info.indexType());
					pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
				}
				working = true;
				if (!logged) {
					logged = true;
					System.out.println("[WDBG] mirror ssr: desenhando " + tiles.size() + " pisos");
				}
			}
			STAGED.endFrame();
		} catch (Throwable t) {
			fail("draw", t);
		}
	}
}
