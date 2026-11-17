package com.witch.mixin;

import com.witch.render.GalaxyTools;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
	private static Method witch$quads;

	@Inject(method = "prepareMainSubmit", at = @At("HEAD"), require = 0)
	private void witch$head(@Coerce Object submit, CallbackInfo ci) {
		boolean on = false;
		boolean glow = false;
		try {
			if (witch$quads == null) {
				witch$quads = submit.getClass().getDeclaredMethod("quads");
				witch$quads.setAccessible(true);
			}
			Object q = witch$quads.invoke(submit);
			on = q instanceof GalaxyTools.Quads;
			glow = q instanceof GalaxyTools.GlowQuads;
		} catch (Throwable t) {
			on = false;
			glow = false;
		}
		GalaxyTools.active = on;
		GalaxyTools.activeGlow = glow;
	}

	@Redirect(method = "prepareMainSubmit", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType witch$type(BakedQuad.MaterialInfo info) {
		if (GalaxyTools.active) {
			RenderType t = GalaxyTools.type();
			if (t != null) return t;
		}
		if (GalaxyTools.activeGlow) {
			RenderType t = com.witch.render.GlowItems.type();
			if (t != null) return t;
		}
		return info.itemRenderType();
	}
}
