package com.witch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.witch.module.StraightSwingModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Troca o balanco do item em primeira pessoa (puxao lateral + giro) por um desce-e-sobe reto. */
@Mixin(ItemInHandRenderer.class)
public abstract class StraightSwingMixin {
	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$straight(float attack, PoseStack ps, int side, HumanoidArm arm, CallbackInfo ci) {
		var m = StraightSwingModule.instance;
		if (m == null || !StraightSwingModule.on) return;
		float s = Mth.sin(Mth.sqrt(attack) * (float) Math.PI); // 0 -> 1 -> 0
		ps.translate(0f, -0.07f * m.dist.get() * s, 0f);
		int tilt = m.tilt.get();
		if (tilt > 0) ps.mulPose(Axis.XP.rotationDegrees(-tilt * s));
		ci.cancel();
	}
}
