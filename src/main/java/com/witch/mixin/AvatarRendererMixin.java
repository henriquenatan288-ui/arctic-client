package com.witch.mixin;

import com.witch.render.PlayersGalaxy;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Players Galaxy: a mao em primeira pessoa tambem recebe a galaxia. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Redirect(method = "renderHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityTranslucent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),
			require = 0)
	private RenderType witch$hand(Identifier skin) {
		try {
			if (PlayersGalaxy.wants(true)) {
				RenderType t = PlayersGalaxy.type(skin);
				if (t != null) return t;
			}
		} catch (Throwable ignored) {
		}
		return RenderTypes.entityTranslucent(skin);
	}
}
