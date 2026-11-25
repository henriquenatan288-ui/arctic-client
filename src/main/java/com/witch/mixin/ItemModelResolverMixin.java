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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	/** Modo "textura animada" do modulo Galaxy: troca o modelo do item (vale na mao, hotbar, inventario e no chao). */
	@ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack witch$swap(ItemStack stack) {
		ItemStack k = com.witch.module.KatanaModule.swap(stack);
		if (k != null) return k;
		return GalaxyTools.swap(stack);
	}

	@Inject(method = "appendItemLayers", at = @At("HEAD"), require = 0)
	private void witch$head(ItemStackRenderState s, ItemStack stack, ItemDisplayContext c, Level l, ItemOwner o, int seed, CallbackInfo ci) {
		// hotbar/inventario: galaxia com coordenadas do proprio item (opcao do modulo); Glow Items nao entra la
		int k = GalaxyTools.kind(stack, c == ItemDisplayContext.GUI);
		GalaxyTools.pendingKind = c != ItemDisplayContext.GUI ? k : (k != 0 && (GalaxyTools.gui() || (k >> 3) != 0) ? k | 4 : 0);
		GalaxyTools.pendingGlow = GalaxyTools.pendingKind == 0 && c != ItemDisplayContext.GUI && com.witch.render.GlowItems.wants(stack);
	}

	@Inject(method = "appendItemLayers", at = @At("RETURN"), require = 0)
	private void witch$ret(ItemStackRenderState s, ItemStack stack, ItemDisplayContext c, Level l, ItemOwner o, int seed, CallbackInfo ci) {
		GalaxyTools.pendingKind = 0;
		GalaxyTools.pendingGlow = false;
	}
}
