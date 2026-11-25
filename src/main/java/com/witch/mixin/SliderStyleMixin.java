package com.witch.mixin;

import com.witch.gui.WidgetStyle;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Barras deslizantes (volume, FOV...) no mesmo estilo dos botoes, com sprites proprios. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderStyleMixin {
	@Inject(method = "getSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$track(CallbackInfoReturnable<Identifier> cir) {
		if (!WidgetStyle.applies()) return;
		AbstractSliderButton s = (AbstractSliderButton) (Object) this;
		cir.setReturnValue(Identifier.fromNamespaceAndPath("witch", s.isHoveredOrFocused() ? "widget/slider_track_hover" : "widget/slider_track"));
	}

	@Inject(method = "getHandleSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void witch$handle(CallbackInfoReturnable<Identifier> cir) {
		if (!WidgetStyle.applies()) return;
		AbstractSliderButton s = (AbstractSliderButton) (Object) this;
		cir.setReturnValue(Identifier.fromNamespaceAndPath("witch", s.isHoveredOrFocused() ? "widget/slider_handle_hover" : "widget/slider_handle"));
	}
}
