package com.witch.mixin;

import com.witch.render.GalaxyTools;
import java.util.List;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class LayerRenderStateMixin implements GalaxyTools.Flag {
	private int witch$galaxy;
	private boolean witch$glow;

	@Override public boolean witch$glow() { return witch$glow; }
	@Override public void witch$setGlow(boolean v) { witch$glow = v; }

	@Override public int witch$galaxy() { return witch$galaxy; }
	@Override public void witch$setGalaxy(int v) { witch$galaxy = v; }

	@ModifyArg(method = "submit", index = 6, require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem"))
	private List<BakedQuad> witch$wrap(List<BakedQuad> quads) {
		if (witch$galaxy != 0) return new GalaxyTools.Quads(quads, witch$galaxy);
		return witch$glow ? new GalaxyTools.GlowQuads(quads) : quads;
	}
}
