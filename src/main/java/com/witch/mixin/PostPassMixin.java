package com.witch.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O shader do Cosmic FX precisa da posicao do buraco negro na tela a cada quadro: troca o buffer de uniformes antes de desenhar. */
@Mixin(PostPass.class)
public class PostPassMixin {
	@Shadow @Final private Map<String, GpuBuffer> customUniforms;

	@Inject(method = "addToFrame", at = @At("HEAD"), require = 0)
	private void witch$lens(com.mojang.blaze3d.framegraph.FrameGraphBuilder b, Map<?, ?> m, com.mojang.blaze3d.buffers.GpuBufferSlice s, CallbackInfo ci) {
		GpuBuffer old = customUniforms.get("LensConfig");
		if (old == null) return;
		float[] d = com.witch.module.CosmicFxModule.lensData();
		if (d == null) return;
		try (MemoryStack st = MemoryStack.stackPush()) {
			var bb = Std140Builder.onStack(st, 32).putVec4(d[0], d[1], d[2], d[3]).putVec4(d[4], d[5], d[6], d[7]).get();
			GpuBuffer nb = RenderSystem.getDevice().createBuffer(() -> "witch cosmic lens", GpuBuffer.USAGE_UNIFORM, bb);
			customUniforms.put("LensConfig", nb);
			old.close();
		} catch (Throwable t) {
			// mantem o buffer antigo (sem lente)
		}
	}
}
