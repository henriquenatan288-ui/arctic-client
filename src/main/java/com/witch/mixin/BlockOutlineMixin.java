package com.witch.mixin;

import com.witch.module.BlockOutlineModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Troca cor e espessura do contorno do bloco mirado (o jogo desenha com submitHitOutline). */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
	private static final String HIT = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V";

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT), index = 4, require = 0)
	private int witch$color(int c) {
		return BlockOutlineModule.active() && (c >>> 24) != 0xFF ? BlockOutlineModule.argb() : c;
	}

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT), index = 5, require = 0)
	private float witch$width(float w) {
		return BlockOutlineModule.active() ? BlockOutlineModule.lineWidth() : w;
	}
}
