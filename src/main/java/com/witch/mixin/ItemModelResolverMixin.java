package com.witch.mixin;

import com.witch.render.GalaxyTools;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@Inject(method = "appendItemLayers", at = @At("HEAD"), require = 0)
	private void witch$head(ItemStackRenderState s, ItemStack stack, ItemDisplayContext c, Level l, ItemOwner o, int seed, CallbackInfo ci) {
		GalaxyTools.pending = GalaxyTools.isGalaxy(stack);
	}

	@Inject(method = "appendItemLayers", at = @At("RETURN"), require = 0)
	private void witch$ret(ItemStackRenderState s, ItemStack stack, ItemDisplayContext c, Level l, ItemOwner o, int seed, CallbackInfo ci) {
		GalaxyTools.pending = false;
	}
}
