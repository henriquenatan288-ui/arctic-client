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

/** Tira a curva lateral do balanco do item em primeira pessoa e deixa so o tapa para baixo. */
@Mixin(ItemInHandRenderer.class)
public abstract class StraightSwingMixin {
	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$straight(float attack, PoseStack ps, int side, HumanoidArm arm, CallbackInfo ci) {
		var m = StraightSwingModule.instance;
		if (m == null || !StraightSwingModule.on || com.witch.module.KatanaModule.slashing()) return;
		// igual ao jogo, mas sem o puxao para o lado (x), sem o giro em Y e sem o giro em Z: so sobe/avanca um pouco e bate para baixo
		float g = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
		float h = 0.2f * Mth.sin(Mth.sqrt(attack) * (float) (Math.PI * 2));
		float z = -0.2f * Mth.sin(attack * (float) Math.PI);
		ps.translate(0f, h, z);
		ps.mulPose(Axis.XP.rotationDegrees(g * -m.angle.get()));
		ci.cancel();
	}
}
