package com.witch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.witch.module.KatanaModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Golpe de samurai em primeira pessoa: levanta a katana atras do ombro, corta em diagonal (de cima-direita para baixo-esquerda) e recolhe. */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class KatanaSlashMixin {
	private static float ease(float t) { return t * t * (3f - 2f * t); }

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$slash(float a, PoseStack ps, int side, HumanoidArm arm, CallbackInfo ci) {
		if (!KatanaModule.slashing()) return;
		var p = net.minecraft.client.Minecraft.getInstance().player;
		if (p == null || arm != p.getMainArm()) return;
		float amp = 0.5f + KatanaModule.instance.slashPower.get() * 0.1f;
		float s;
		if (a < 0.26f) s = -0.4f * ease(a / 0.26f);                                   // prepara: puxa para tras
		else if (a < 0.6f) { float t = (a - 0.26f) / 0.34f; float o = 1f - (float) Math.pow(1f - t, 3); s = -0.4f + 1.4f * o; } // corte rapido
		else s = 1f - ease((a - 0.6f) / 0.4f);                                         // recolhe
		float sd = s * amp;
		// de cima-direita (s<0) para baixo-esquerda (s>0)
		ps.translate(side * -0.42f * sd, -0.30f * sd + (s < 0 ? 0.10f * -s : 0f), -0.30f * sd);
		ps.mulPose(Axis.YP.rotationDegrees(side * 25f * sd));
		ps.mulPose(Axis.XP.rotationDegrees(-65f * sd));
		ps.mulPose(Axis.ZP.rotationDegrees(side * -50f * sd));
		ci.cancel();
	}
}
