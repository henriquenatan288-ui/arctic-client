package com.witch.mixin;

import com.witch.render.GalaxyTools;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackRenderState.class)
public class ItemStackRenderStateMixin {
	@Inject(method = "newLayer", at = @At("RETURN"), require = 0)
	private void witch$layer(CallbackInfoReturnable<ItemStackRenderState.LayerRenderState> cir) {
		var f = (GalaxyTools.Flag) (Object) cir.getReturnValue();
		f.witch$setGalaxy(GalaxyTools.pending);
		f.witch$setGlow(GalaxyTools.pendingGlow);
	}
}
