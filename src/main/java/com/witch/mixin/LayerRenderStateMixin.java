package com.witch.mixin;

import com.witch.render.GalaxyTools;
import java.util.List;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class LayerRenderStateMixin implements GalaxyTools.Flag {
	private int witch$galaxy;
	private static int witch$dbg;
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

	/** Itens de modelo especial (tridente): avisa o renderer especial de que esta camada tem galaxia (so fora de hotbar/inventario). */
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = "submit", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"))
	private void witch$special(net.minecraft.client.renderer.special.SpecialModelRenderer r, Object arg, com.mojang.blaze3d.vertex.PoseStack pose,
			net.minecraft.client.renderer.SubmitNodeCollector c, int light, int overlay, boolean glint, int outline) {
		GalaxyTools.specialKind = (witch$galaxy & 4) == 0 ? witch$galaxy : 0;
		if (System.getProperty("witch.debug") != null && witch$dbg++ < 6) System.out.println("[WDBG] special renderer " + r.getClass().getSimpleName() + " kind=" + witch$galaxy);
		try {
			r.submit(arg, pose, c, light, overlay, glint, outline);
		} finally {
			GalaxyTools.specialKind = 0;
		}
	}
}
