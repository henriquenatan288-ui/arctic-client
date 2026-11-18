package com.witch.mixin;

import com.witch.render.GalaxyTools;
import com.witch.render.PlayersGalaxy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.TridentSpecialRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Galaxy: o tridente (item de modelo especial, sem quads) usa o shader de entidade na mao e no chao. */
@Mixin(TridentSpecialRenderer.class)
public abstract class TridentSpecialRendererMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = "submit", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void witch$trident(OrderedSubmitNodeCollector c, Model model, Object state, PoseStack pose, Identifier tex,
			int light, int overlay, int color, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		RenderType t = null;
		try {
			if (GalaxyTools.specialKind != 0) t = PlayersGalaxy.type(tex, true);
		} catch (Throwable ignored) {
		}
		if (t != null) c.submitModel(model, state, pose, t, light, overlay, color, crumbling);
		else c.submitModel(model, state, pose, tex, light, overlay, color, crumbling);
	}
}
