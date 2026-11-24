package com.witch.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.witch.module.WingsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;

/** Asas: penas em leque, cada uma um triangulo fino, presas aos ombros e viradas para tras. */
public final class Wings {
	private Wings() {}

	private static final Identifier TEX = Identifier.fromNamespaceAndPath("witch", "textures/misc/white.png");

	/** ps esta na origem da entidade (pes), sem rotacao. */
	public static void submit(AvatarRenderState st, PoseStack ps, SubmitNodeCollector c) {
		var m = WingsModule.instance;
		if (m == null) return;
		boolean galaxy = m.look.get() == 1;
		RenderType type = galaxy ? PlayersGalaxy.type(TEX, true, m.style.get() << 2) : null;
		final boolean flat = type == null; // cor escolhida, ou shader falhou
		if (flat) type = RenderTypes.entityTranslucent(TEX);

		final float scale = 0.55f + m.size.get() * 0.1f;
		final int nf = m.feathers.get(), nl = m.layers.get();
		final int argb = m.color.get();
		final boolean glide = st.pose == Pose.FALL_FLYING;
		final boolean crouch = st.pose == Pose.CROUCHING;
		float speed = m.flap.get() * 0.045f;
		if (glide) speed = 0f; // planando: asas abertas e paradas
		else if (st.walkAnimationSpeed > 0.15f) speed *= 1.6f;
		final float t = st.ageInTicks * speed;
		final float open = glide ? 0.85f : 0.45f + (m.flap.get() == 0 ? 0f : 0.3f * (float) Math.sin(t));

		ps.pushPose();
		ps.mulPose(Axis.YP.rotationDegrees(-st.bodyRot));
		float shoulderY = st.eyeHeight * 0.78f - (crouch ? 0.22f : 0f);
		ps.translate(0f, shoulderY, -0.17f);
		if (crouch) ps.mulPose(Axis.XP.rotationDegrees(-22f));
		final int fl = nf, ll = nl;
		final float sc = scale;
		c.submitCustomGeometry(ps, type, (pose, vc) -> {
			for (int side = -1; side <= 1; side += 2) {
				for (int layer = 0; layer < ll; layer++) {
					float lenK = 1.0f - layer * 0.24f;
					for (int i = 0; i < fl; i++) {
						float k = fl == 1 ? 0f : i / (float) (fl - 1); // 0 = pena de cima, 1 = de baixo
						// angulo no plano da asa: de quase vertical (cima) ate quase horizontal-para-baixo
						float phi = (float) Math.toRadians(76.0 - 112.0 * k + layer * 6.0);
						float len = sc * lenK * (0.55f + 0.45f * (float) Math.sin(Math.PI * (0.18 + 0.82 * (1.0 - k))));
						float wid = 0.045f * sc * (0.8f + 0.4f * lenK);
						feather(vc, pose, side, phi, len, wid, open, layer * 0.006f + i * 0.0007f, flat, argb, k);
					}
				}
			}
		});
		ps.popPose();
	}

	private static void feather(VertexConsumer vc, PoseStack.Pose pose, int side, float phi, float len, float wid,
			float open, float zoff, boolean flat, int argb, float k) {
		// plano da asa: u = para fora do corpo, v = para cima. A asa gira em torno do eixo vertical ("abrir/fechar").
		float cu = (float) Math.cos(phi), cv = (float) Math.sin(phi);
		float tipU = cu * len, tipV = cv * len;
		float pu = -cv, pv = cu; // perpendicular, para dar largura
		float rootU = 0.11f, rootV = 0f;
		float ca = (float) Math.cos(open), sa = (float) Math.sin(open);
		// pontos (u,v) -> x = side*u*ca, z = -u*sa (atras), y = v
		float[] x = new float[4], y = new float[4], z = new float[4];
		float tw = len * 0.21f; // ponta larga: as penas se encostam e formam uma asa continua
		float[][] uv = {
				{rootU - pu * wid * 0.5f, rootV - pv * wid * 0.5f},
				{rootU + pu * wid * 0.5f, rootV + pv * wid * 0.5f},
				{tipU + pu * tw, tipV + pv * tw},
				{tipU - pu * tw, tipV - pv * tw}};
		for (int i = 0; i < 4; i++) {
			x[i] = side * uv[i][0] * ca;
			y[i] = uv[i][1];
			z[i] = -uv[i][0] * sa + zoff;
		}
		int r, g, b, ar, at;
		if (flat) {
			r = argb >> 16 & 255; g = argb >> 8 & 255; b = argb & 255;
			// raiz mais clara, ponta translucida
			int tone = 255 - Math.round(k * 40f);
			r = r * tone / 255; g = g * tone / 255; b = b * tone / 255;
			ar = 235; at = 120;
		} else {
			r = g = b = 255; ar = at = 255;
		}
		for (int pass = 0; pass < 2; pass++) { // frente e verso (a camada translucida costuma usar cull)
			float nz = pass == 0 ? 1f : -1f;
			int[] order = pass == 0 ? new int[] {0, 1, 2, 3} : new int[] {1, 0, 3, 2};
			for (int idx : order) {
				boolean tip = idx >= 2;
				vc.addVertex(pose, x[idx], y[idx], z[idx]).setColor(r, g, b, tip ? at : ar).setUv(0.5f, 0.5f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, nz);
			}
		}
	}
}
