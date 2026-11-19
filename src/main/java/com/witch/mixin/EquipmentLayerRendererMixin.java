package com.witch.mixin;

import com.witch.render.PlayersGalaxy;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Players Galaxy: armadura e elytra no corpo do jogador (a camada base; trims e brilho continuam normais). */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
	private static int dbg;
	@Redirect(method = "renderLayers", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType witch$equipment(Identifier tex) {
		boolean dbgOn = System.getProperty("witch.debug") != null && dbg++ < 8;
		if (dbgOn) System.out.println("[WDBG] equipment hit tex=" + tex + " curBody=" + PlayersGalaxy.curBody);
		try {
			RenderType t = PlayersGalaxy.equipment(tex);
			if (dbgOn) System.out.println("[WDBG] equipment -> " + (t != null ? "galaxy" : "normal") + " status=" + PlayersGalaxy.status);
			if (t != null) return t;
		} catch (Throwable e) {
			if (dbgOn) e.printStackTrace();
		}
		return RenderTypes.armorCutoutNoCull(tex);
	}
}
