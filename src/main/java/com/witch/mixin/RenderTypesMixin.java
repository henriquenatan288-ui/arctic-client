package com.witch.mixin;

import com.witch.render.PlayersGalaxy;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players Galaxy: armadura e elytra (a camada base; trims e brilho continuam normais).
 * Pega no proprio RenderTypes.armorCutoutNoCull, que HumanoidArmorLayer, WingsLayer e cia chamam pelo EquipmentLayerRenderer.
 */
@Mixin(RenderTypes.class)
public abstract class RenderTypesMixin {
	private static int dbg;

	@Inject(method = "armorCutoutNoCull", at = @At("HEAD"), cancellable = true, require = 0)
	private static void witch$armor(Identifier tex, CallbackInfoReturnable<RenderType> cir) {
		boolean d = System.getProperty("witch.debug") != null && dbg++ < 8;
		if (d) System.out.println("[WDBG] armorCutoutNoCull hit tex=" + tex + " curBody=" + PlayersGalaxy.curBody);
		try {
			RenderType t = PlayersGalaxy.equipment(tex);
			if (d) System.out.println("[WDBG] armor -> " + (t != null ? "galaxy" : "normal") + " status=" + PlayersGalaxy.status);
			if (t != null) cir.setReturnValue(t);
		} catch (Throwable e) {
			if (d) e.printStackTrace();
		}
	}
}
