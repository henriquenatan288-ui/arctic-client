package com.witch.mixin;

import com.witch.module.KillEffectModule;
import com.witch.render.DissolveHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Kill Effect "po": o mob morto fica transparente (translucido + tint com alpha) enquanto vira po. */
@Mixin(LivingEntityRenderer.class)
public abstract class DissolveRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void witch$dissolveExtract(LivingEntity entity, LivingEntityRenderState state, float pt, CallbackInfo ci) {
		try {
			float a = KillEffectModule.dissolveAlpha(entity.getId());
			((DissolveHolder) state).witch$setDissolve(a);
			if (a < 1f) { // o corpo fica de pe, sem cair e sem o vermelho da morte
				state.deathTime = 0f;
				state.hasRedOverlay = false;
			}
		} catch (Throwable ignored) {
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$dissolveType(LivingEntityRenderState state, boolean visible, boolean ghost, boolean glowing,
			CallbackInfoReturnable<RenderType> cir) {
		try {
			if (((DissolveHolder) state).witch$getDissolve() < 1f) {
				cir.setReturnValue(RenderTypes.entityTranslucentCullItemTarget(((LivingEntityRenderer) (Object) this).getTextureLocation(state)));
			}
		} catch (Throwable ignored) {
		}
	}

	/** Camadas (armadura, roupa, olhos) nao ficam translucidas: somem quando o corpo ja esta bem transparente. */
	@Inject(method = "shouldRenderLayers", at = @At("RETURN"), cancellable = true, require = 0)
	private void witch$dissolveLayers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
		try {
			if (((DissolveHolder) state).witch$getDissolve() < 0.3f) cir.setReturnValue(false);
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true, require = 0)
	private void witch$dissolveTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		try {
			float a = ((DissolveHolder) state).witch$getDissolve();
			if (a < 1f) cir.setReturnValue(ARGB.color(Math.max(0, Math.round(a * 255f)), 0xFFFFFFFF));
		} catch (Throwable ignored) {
		}
	}
}
