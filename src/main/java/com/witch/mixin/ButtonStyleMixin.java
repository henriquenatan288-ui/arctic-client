package com.witch.mixin;

import com.witch.gui.RenderUtil;
import com.witch.gui.WidgetStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Botoes das telas de pausa/opcoes no estilo do client: vidro escuro, contorno azul-bebe que acende ao passar o mouse. */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin {
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$style(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (!WidgetStyle.applies()) return;
		AbstractButton b = (AbstractButton) (Object) this;
		int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
		boolean hot = b.isHoveredOrFocused() && b.active;
		int r = Math.min(6, h / 2);
		int fill = hot ? 0xFF1B3A72 : 0xFF0B1730;
		RenderUtil.roundRect(g, x, y, w, h, r, RenderUtil.alpha(fill, b.active ? (hot ? 0.82f : 0.6f) : 0.35f));
		RenderUtil.roundRing(g, x, y, w, h, r, RenderUtil.alpha(hot ? 0xFF8EC5FF : 0xFFE3F1FF, b.active ? (hot ? 0.95f : 0.28f) : 0.12f));
		ci.cancel();
	}
}
