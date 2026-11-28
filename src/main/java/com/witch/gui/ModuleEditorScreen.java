package com.witch.gui;

import com.witch.config.Config;
import com.witch.module.Module;
import com.witch.setting.IntSetting;
import com.witch.setting.KeybindSetting;
import com.witch.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Editor de modulo em tela cheia: ajustes do modulo numa coluna central, fundo preto com listras e vermelho. */
public class ModuleEditorScreen extends Screen {
	private final Module m;
	private final Screen parent;
	private float scroll = 0f;
	private float anim = 0f;
	private long lastNanos = 0L;
	private IntSetting slider;
	private int sliderX, sliderW = 1;
	private Runnable deferred;
	private double pressY;
	private float scrollStart;
	private boolean dragScroll, moved;

	public ModuleEditorScreen(Module m, Screen parent) {
		super(Component.literal("Editor de Modulo"));
		this.m = m;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int colW() { return Math.min(this.width - 40, 340); }
	private int colX() { return (this.width - colW()) / 2; }
	private int top() { return 66; }
	private int bottom() { return this.height - 16; }

	private int contentH() {
		int h = 0;
		for (Setting st : m.settings()) h += SettingRenderer.height(st, colW());
		return h;
	}

	private void clampScroll() {
		scroll = Mth.clamp(scroll, 0f, Math.max(0, contentH() - (bottom() - top())));
	}

	private void trapezoid(GuiGraphicsExtractor g, int cx, int y, int topW, int h, int color) {
		for (int i = 0; i < h; i++) {
			int hw = topW / 2 - Math.round(i * 0.9f);
			g.fill(cx - hw, y + i, cx + hw, y + i + 1, color);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		long now = System.nanoTime();
		float speed = lastNanos == 0 ? 1f : Math.min(3f, (now - lastNanos) / 16_666_667f);
		lastNanos = now;
		anim = Mth.lerp(Math.min(1f, speed * 0.18f), anim, 1f);
		float a = anim;
		try {
			this.extractBlurredBackground(g);
		} catch (Throwable ignored) {
			// sem desfoque
		}
		GuiBackdrop.draw(g, this.width, this.height, a);
		int fh = FontUtil.height();
		int cx = this.width / 2;

		// aba do titulo (trapezio) + linha vermelha
		int tw = Math.min(this.width - 120, 190);
		trapezoid(g, cx, 6, tw, 22, RenderUtil.alpha(0xFF14110F, 0.85f * a));
		String title = ModuleRow.fit(m.name, tw - 40);
		FontUtil.draw(g, title, cx - FontUtil.width(title) / 2, 6 + (22 - fh) / 2 + 1, RenderUtil.alpha(0xFFE8E4E5, a));
		GuiBackdrop.redRule(g, 14, 29, this.width - 28, a);

		// voltar
		String back = "< Voltar";
		int bw = FontUtil.width(back) + 14, bx = 12, by = 12, bh = 16;
		boolean bh0 = RenderUtil.inside(mx, my, bx, by, bw, bh);
		RenderUtil.roundRect(g, bx, by, bw, bh, 8, RenderUtil.alpha(bh0 ? 0x30FFFFFF : 0x18FFFFFF, a));
		FontUtil.draw(g, back, bx + 7, by + (bh - fh) / 2, RenderUtil.alpha(bh0 ? 0xFFFFFFFF : 0xFFC9C3C5, a));

		// liga/desliga
		boolean on = m.isEnabled();
		String st = on ? "ON" : "OFF";
		int sw = FontUtil.width("OFF") + 18, sx = this.width - 12 - sw;
		boolean sh0 = RenderUtil.inside(mx, my, sx, by, sw, bh);
		RenderUtil.roundRect(g, sx, by, sw, bh, 8, RenderUtil.alpha(on ? GuiBackdrop.RED : (sh0 ? 0x30FFFFFF : 0x18FFFFFF), a));
		FontUtil.draw(g, st, sx + (sw - FontUtil.width(st)) / 2, by + (bh - fh) / 2, RenderUtil.alpha(0xFFFFFFFF, a));

		// coluna de ajustes
		int x = colX(), w = colW(), t = top(), b = bottom();
		clampScroll();
		RenderUtil.softShadow(g, x - 8, t - 6, w + 16, b - t + 12, 14, RenderUtil.alpha(0xFF000000, 0.5f * a));
		RenderUtil.roundRect(g, x - 8, t - 6, w + 16, b - t + 12, 8, RenderUtil.alpha(0xFF040406, 0.42f * a));
		RenderUtil.glow(g, x - 8, t - 6, 40, RenderUtil.alpha(GuiBackdrop.RED, 0.18f * a));
		RenderUtil.glow(g, x + w + 8, b + 6, 40, RenderUtil.alpha(GuiBackdrop.RED, 0.14f * a));
		if (m.settings().isEmpty()) {
			String none = "Este modulo nao tem ajustes.";
			FontUtil.draw(g, none, cx - FontUtil.width(none) / 2, t + 20, RenderUtil.alpha(0xFF8E8688, a));
			return;
		}
		g.enableScissor(x - 6, t - 4, x + w + 6, b + 4);
		int ry = t - Math.round(scroll);
		for (Setting s : m.settings()) {
			int sh = SettingRenderer.height(s, w);
			if (ry + sh >= t - 4 && ry <= b + 4) SettingRenderer.draw(g, s, x, ry, w, sh, a, 0f);
			ry += sh;
		}
		g.disableScissor();
		int total = contentH(), vis = b - t;
		if (total > vis) {
			int th = Math.max(14, Math.round(vis * (vis / (float) total)));
			float f = scroll / Math.max(1, total - vis);
			RenderUtil.roundRect(g, x + w + 4, t + Math.round((vis - th) * f), 2, th, 1, RenderUtil.alpha(GuiBackdrop.RED, 0.8f * a));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		int fh = FontUtil.height();
		deferred = null;
		dragScroll = false;
		String back = "< Voltar";
		if (RenderUtil.inside(mx, my, 12, 12, FontUtil.width(back) + 14, 16)) {
			this.onClose();
			return true;
		}
		int sw = FontUtil.width("OFF") + 18;
		if (RenderUtil.inside(mx, my, this.width - 12 - sw, 12, sw, 16)) {
			m.toggle();
			Config.save();
			return true;
		}
		int x = colX(), w = colW(), t = top(), b = bottom();
		if (my < t || my > b || mx < x - 6 || mx > x + w + 6) return super.mouseClicked(click, doubled);
		int ry = t - Math.round(scroll);
		for (Setting s : m.settings()) {
			int sh = SettingRenderer.height(s, w);
			if (my >= ry && my < ry + sh) {
				Runnable[] out = new Runnable[1];
				IntSetting slid = SettingRenderer.click(s, x, ry, w, sh, mx, my, fh, out, click.button());
				if (slid != null) {
					slider = slid;
					sliderX = x + 7;
					sliderW = Math.max(1, w - 14);
				}
				if (slid == null && !SettingRenderer.dragging()) {
					deferred = out[0];
					pressY = my;
					scrollStart = scroll;
					dragScroll = true;
					moved = false;
				}
				return true;
			}
			ry += sh;
		}
		pressY = my;
		scrollStart = scroll;
		dragScroll = true;
		moved = false;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (SettingRenderer.dragging()) {
			SettingRenderer.drag(click.x(), click.y());
			return true;
		}
		if (slider != null) {
			slider.setFraction((float) ((click.x() - sliderX) / sliderW));
			return true;
		}
		if (dragScroll) {
			double dy = click.y() - pressY;
			if (moved || Math.abs(dy) > 3) {
				moved = true;
				deferred = null;
				scroll = scrollStart - (float) dy;
				clampScroll();
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		boolean was = slider != null || SettingRenderer.dragging() || dragScroll;
		if (deferred != null && !moved) deferred.run();
		deferred = null;
		dragScroll = false;
		SettingRenderer.release();
		if (slider != null) Config.save();
		slider = null;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (float) (verticalAmount * 16.0);
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();
		for (Setting st : m.settings()) {
			if (st instanceof KeybindSetting ks && ks.listening) {
				ks.listening = false;
				if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) ks.setKey(0);
				else if (key != GLFW.GLFW_KEY_ESCAPE) ks.setKey(key);
				Config.save();
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}
}
