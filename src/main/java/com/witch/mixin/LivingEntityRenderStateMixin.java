package com.witch.mixin;

import com.witch.render.DissolveHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements DissolveHolder {
	@Unique
	private float witch$dissolve = 1f;

	@Override
	public float witch$getDissolve() {
		return witch$dissolve;
	}

	@Override
	public void witch$setDissolve(float a) {
		witch$dissolve = a;
	}
}
