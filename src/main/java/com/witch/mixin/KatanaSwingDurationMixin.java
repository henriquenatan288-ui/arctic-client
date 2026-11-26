package com.witch.mixin;

import com.witch.module.KatanaModule;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O golpe de samurai dura um pouco mais que o tapa normal (so a animacao; o dano nao muda). */
@Mixin(LivingEntity.class)
public abstract class KatanaSwingDurationMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
	private void witch$dur(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == net.minecraft.client.Minecraft.getInstance().player && KatanaModule.slashing()) cir.setReturnValue(Math.max(cir.getReturnValue(), 10));
	}
}
