package com.witch.gui;

import com.witch.config.Config;
import com.witch.module.Category;
import com.witch.module.Module;
import com.witch.module.ModuleManager;
import com.witch.setting.IntSetting;
import com.witch.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ClickGUI do Arctic: uma janela de vidro branco e azul bebe, barra lateral de categorias, busca no topo,
 * modulos em linhas com interruptor. Toque no nome liga/desliga; a seta (ou botao direito) abre os ajustes.
 */
public class ArcticGuiScreen extends Screen {
	private static final float UI = 0.85f;
	private static final int SIDE_W = 100, HEAD_H = 28, ROW_H = 20, GAP = 3, PAD = 8, R = 16;
	private static final int BASE = 0xFF4670B8, SIDE = 0xFF21407A, HEADC = 0xFF34589A;
	private static final int BLUE = 0xFF8EC5FF, BLUE_DK = 0xFF5B9BF0, ICE = 0xFFE3F1FF;

	private static final Category[] CATS = Category.values();
	private static Category current = CATS[0];
	private static final Set<Module> EXPANDED = new HashSet<>();
	private static final Map<Module, Float> EXP = new HashMap<>();

	private String search = "";
	private boolean searchFocused;
	private float scroll, openAnim;
	private long lastNanos;
	private Module tipNow;
	private Object tipLast;
	private long tipSince;

	private Runnable deferred;
	private boolean moved;
	private boolean scrolling;
	private double pressY;
	private float scrollStart;
	private IntSetting dragInt;
	private int dragIx, dragIw;

	public ArcticGuiScreen() {
		this(null);
	}

	public ArcticGuiScreen(Module openSettings) {
		super(Component.literal("Arctic Client"));
		if (openSettings != null) {
			current = openSettings.category;
			EXPANDED.add(openSettings);
			EXP.put(openSettings, 1f);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- geometria ----------

	private int vw() { return Math.round(this.width / UI); }
	private int vh() { return Math.round(this.height / UI); }
	private int ww() { return Math.min(vw() - 24, 580); }
	private int wh() { return Math.min(vh() - 24, 340); }
	private int wx() { return (vw() - ww()) / 2; }
	private int wy() { return (vh() - wh()) / 2; }
	private int cx() { return wx() + SIDE_W + PAD; }
	private int cw() { return ww() - SIDE_W - 2 * PAD; }
	private int cy() { return wy() + HEAD_H + PAD; }
	private int ch() { return wh() - HEAD_H - 2 * PAD; }
	private int searchW() { return Math.max(70, Math.min(150, ww() - 190)); }
	private int searchX() { return wx() + ww() - 36 - searchW(); }
	private int closeX() { return wx() + ww() - 26; }

	private List<Module> list() {
		if (search.isEmpty()) return ModuleManager.byCategory(current);
		String q = search.toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (m.name.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
		return out;
	}

	private static float expAnim(Module m) {
		return EXP.getOrDefault(m, 0f);
	}

	private int settingsH(Module m) {
		int h = 6;
		for (Setting s : m.settings()) h += SettingRenderer.height(s, cw());
		return h;
	}

	private int blockH(Module m) {
		return ROW_H + GAP + Math.round(settingsH(m) * expAnim(m));
	}

	private int contentH() {
		int h = 0;
		for (Module m : list()) h += blockH(m);
		return h;
	}

	private void clampScroll() {
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentH() - ch()));
	}

	// ---------- desenho ----------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		long now = System.nanoTime();
		float speed = lastNanos == 0 ? 1f : Math.min(3f, (now - lastNanos) / 16_666_667f);
		lastNanos = now;
		openAnim = Mth.lerp(Math.min(1f, speed * com.witch.module.GuiAnimationModule.rate()), openAnim, 1f);
		float a = openAnim;
		try {
			this.extractBlurredBackground(g);
		} catch (Throwable ignored) {
		}
		g.fill(0, 0, this.width, this.height, RenderUtil.alpha(0xFF1A3570, 0.38f * a));
		g.pose().pushMatrix();
		g.pose().scale(UI, UI);
		mx = Math.round(mx / UI);
		my = Math.round(my / UI);
		tipNow = null;
		int fh = FontUtil.height();

		for (Map.Entry<Module, Float> e : EXP.entrySet()) {
			float t = EXPANDED.contains(e.getKey()) ? 1f : 0f;
			e.setValue(Mth.lerp(Math.min(1f, speed * 0.35f), e.getValue(), t));
		}
		for (Module m : EXPANDED) EXP.putIfAbsent(m, 0f);

		int wx = wx(), wy = wy() + Math.round((1f - a) * 8f), ww = ww(), wh = wh();
		// brilho em volta da janela
		RenderUtil.softShadow(g, wx, wy, ww, wh, 16, RenderUtil.alpha(0xFFBFDAFF, 0.10f * a));
		RenderUtil.softShadow(g, wx, wy, ww, wh, 9, RenderUtil.alpha(0xFFCFE4FF, 0.14f * a));
		RenderUtil.softShadow(g, wx, wy, ww, wh, 4, RenderUtil.alpha(0xFFFFFFFF, 0.06f * a));
		// corpo e cabecalho
		RenderUtil.roundRect(g, wx, wy, ww, wh, R, RenderUtil.alpha(BASE, 0.78f * a));
		RenderUtil.roundRect(g, wx, wy, ww, HEAD_H, R, RenderUtil.alpha(HEADC, 0.70f * a));
		g.fill(wx, wy + HEAD_H / 2, wx + ww, wy + HEAD_H, RenderUtil.alpha(HEADC, 0.70f * a));
		g.fill(wx + 1, wy + HEAD_H, wx + ww - 1, wy + HEAD_H + 1, RenderUtil.alpha(ICE, 0.30f * a));
		RenderUtil.roundRing(g, wx, wy, ww, wh, R, RenderUtil.alpha(ICE, 0.50f * a)); // borda fina, 1px
		g.fill(wx + SIDE_W, wy + HEAD_H + 2, wx + SIDE_W + 1, wy + wh - R, RenderUtil.alpha(ICE, 0.40f * a));
		swoosh(g, wx, wy, ww, wh, a);
		// reflexo suave que passa
		float cyc = (System.nanoTime() / 1_000_000L % 9000L) / 9000f;
		int rx = Math.round(wx + (cyc * 1.5f - 0.25f) * ww);
		for (int d = -14; d <= 14; d++) {
			int xx = rx + d;
			if (xx <= wx + R || xx >= wx + ww - R) continue;
			g.fill(xx, wy + HEAD_H + 2, xx + 1, wy + wh - 3, ((int) ((1f - Math.abs(d) / 14f) * 0.06f * a * 255) << 24) | 0xFFFFFF);
		}

		// marca e nome
		int lw = 16;
		WitchLogo.draw(g, wx + 12, wy + (HEAD_H - WitchLogo.height(lw)) / 2, lw, a, 0xFFFFFFFF);
		Minimal.spaced(g, "ARCTIC", wx + 34, wy + (HEAD_H - fh) / 2f, 1f, 3f, 0xFFFFFFFF, a, false);

		// busca
		int sx = searchX(), sy = wy + 5, sw = searchW(), sh = HEAD_H - 10;
		RenderUtil.roundRect(g, sx, sy, sw, sh, 7, RenderUtil.alpha(0xFF0B1730, 0.35f * a));
		RenderUtil.roundRing(g, sx, sy, sw, sh, 7, RenderUtil.alpha(searchFocused ? BLUE : ICE, (searchFocused ? 0.95f : 0.35f) * a));
		CategoryIcons.blit(g, CategoryIcons.SEARCH, sx + 5, sy + (sh - 9) / 2, 9, RenderUtil.alpha(0xFFDCEBFF, a));
		String shown;
		int scol;
		if (search.isEmpty() && !searchFocused) {
			shown = "Search...";
			scol = 0xFF9DB6D9;
		} else {
			shown = search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
			scol = 0xFFFFFFFF;
		}
		FontUtil.draw(g, ModuleRow.fit(shown, sw - 22), sx + 17, sy + (sh - fh) / 2, RenderUtil.alpha(scol, a));
		// fechar
		int bx = closeX(), by = wy + 6, bs = HEAD_H - 12;
		boolean hc = RenderUtil.inside(mx, my, bx, by, bs, bs);
		RenderUtil.roundRect(g, bx, by, bs, bs, 6, RenderUtil.alpha(0xFFFFFFFF, (hc ? 0.22f : 0.10f) * a));
		FontUtil.draw(g, "x", bx + (bs - FontUtil.width("x")) / 2, by + (bs - fh) / 2, RenderUtil.alpha(0xFFFFFFFF, a));

		// barra lateral
		int sbx = wx + 5, sby = wy + HEAD_H + 6, sbw = SIDE_W - 8, sbh = wh - HEAD_H - 11;
		RenderUtil.roundRect(g, sbx, sby, sbw, sbh, 8, RenderUtil.alpha(SIDE, 0.50f * a));
		for (int i = 0; i < CATS.length; i++) {
			int iy = sby + 5 + i * 23;
			boolean sel = search.isEmpty() && CATS[i] == current;
			boolean hov = RenderUtil.inside(mx, my, sbx + 3, iy, sbw - 6, 21);
			if (sel) {
				RenderUtil.softShadow(g, sbx + 3, iy, sbw - 6, 21, 4, RenderUtil.alpha(BLUE, 0.22f * a));
				RenderUtil.roundRect(g, sbx + 3, iy, sbw - 6, 21, 10, RenderUtil.alpha(BLUE_DK, 0.95f * a));
				RenderUtil.roundRect(g, sbx + 5, iy + 1, sbw - 10, 8, 6, RenderUtil.alpha(0xFFFFFFFF, 0.16f * a));
				RenderUtil.roundRing(g, sbx + 3, iy, sbw - 6, 21, 10, RenderUtil.alpha(0xFFD9ECFF, 0.7f * a));
			}
			else if (hov) RenderUtil.roundRect(g, sbx + 3, iy, sbw - 6, 21, 10, RenderUtil.alpha(0xFFFFFFFF, 0.10f * a));
			int col = sel ? 0xFFFFFFFF : (hov ? 0xFFEAF4FF : 0xFFB6CCEB);
			CategoryIcons.drawSized(g, CATS[i], sbx + 8, iy + 3, 15, RenderUtil.alpha(col, a));
			FontUtil.draw(g, ModuleRow.fit(CATS[i].label, sbw - 38), sbx + 28, iy + (21 - fh) / 2, RenderUtil.alpha(col, a));
		}

		// modulos
		int cx = cx(), cw = cw(), cy = cy() + (wy - wy()), ch = ch();
		clampScroll();
		g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
		int ry = cy - Math.round(scroll);
		List<Module> mods = list();
		for (Module m : mods) {
			int blk = blockH(m);
			if (ry + blk >= cy && ry <= cy + ch) row(g, m, cx, ry, cw, mx, my, a, speed, cy, ch);
			ry += blk;
		}
		if (mods.isEmpty()) {
			String none = "Nenhum modulo";
			FontUtil.draw(g, none, cx + (cw - FontUtil.width(none)) / 2, cy + 20, RenderUtil.alpha(0xFFB6CCEB, a));
		}
		g.disableScissor();
		int total = contentH();
		if (total > ch) {
			int th = Math.max(14, Math.round(ch * (ch / (float) total)));
			float f = scroll / Math.max(1, total - ch);
			RenderUtil.roundRect(g, cx + cw + 3, cy + Math.round((ch - th) * f), 2, th, 1, RenderUtil.alpha(ICE, 0.6f * a));
		}

		// tooltip
		Object key = tipNow;
		if (key != tipLast) { tipLast = key; tipSince = System.currentTimeMillis(); }
		if (tipNow != null && com.witch.module.GuiSettingsModule.tooltips() && System.currentTimeMillis() - tipSince > 450) {
			String d = ModuleDescriptions.of(tipNow.name);
			if (d != null && !d.isEmpty()) {
				int tw = Math.min(vw() - 16, FontUtil.width(d) + 12);
				int th = fh + 8;
				int tx = Mth.clamp(mx + 10, 6, vw() - tw - 6);
				int ty = Mth.clamp(my + 12, 6, vh() - th - 6);
				RenderUtil.roundRect(g, tx, ty, tw, th, 5, RenderUtil.alpha(0xFF0B1730, 0.96f * a));
				FontUtil.draw(g, ModuleRow.fit(d, tw - 10), tx + 6, ty + 4, RenderUtil.alpha(0xFFEAF4FF, a));
			}
		}
		g.pose().popMatrix();
	}

	/** Curvas claras nos cantos (superior esquerdo e inferior direito), como no desenho de referencia. */
	private static void swoosh(GuiGraphicsExtractor g, int wx, int wy, int ww, int wh, float a) {
		int S = 64;
		for (int r = 0; r < S; r++) {
			float k = r / (float) S;
			int wdt = Math.round(S * (1f - (float) Math.sqrt(k)));
			int ins = cornerInset(r);
			int c = RenderUtil.alpha(RenderUtil.mix(0xFFFFFFFF, BLUE, k), (0.42f - 0.30f * k) * a);
			if (wx + wdt > wx + 1 + ins) g.fill(wx + 1 + ins, wy + 1 + r, wx + wdt, wy + 2 + r, c);
			int yb = wy + wh - 2 - r;
			if (wx + ww - wdt < wx + ww - 1 - ins) g.fill(wx + ww - wdt, yb, wx + ww - 1 - ins, yb + 1, c);
		}
	}

	private static int cornerInset(int dy) {
		if (dy >= R) return 0;
		double d = R - dy - 0.5;
		return (int) Math.ceil(R - Math.sqrt(Math.max(0, R * (double) R - d * d)));
	}

	private void row(GuiGraphicsExtractor g, Module m, int x, int ry, int w, int mx, int my, float a, float speed, int clipY, int clipH) {
		int fh = FontUtil.height();
		boolean hover = RenderUtil.inside(mx, my, x, ry, w, ROW_H) && my >= clipY && my < clipY + clipH;
		m.toggleAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.toggleAnim, m.isEnabled() ? 1f : 0f);
		m.hoverAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.hoverAnim, hover ? 1f : 0f);
		float t = m.toggleAnim;
		int bg = RenderUtil.mix(0x2EFFFFFF, 0x55FFFFFF, Math.max(t * 0.6f, m.hoverAnim * 0.5f));
		RenderUtil.roundRect(g, x, ry, w, ROW_H, 7, RenderUtil.alpha(bg, a));
		if (t > 0.01f) RenderUtil.roundRect(g, x, ry, w, ROW_H, 7, RenderUtil.alpha(BLUE_DK, 0.32f * t * a));
		int col = RenderUtil.mix(0xFFD2E2F7, 0xFFFFFFFF, Math.max(t, m.hoverAnim * 0.6f));
		FontUtil.draw(g, ModuleRow.fit(m.name, w - 70), x + 10, ry + (ROW_H - fh) / 2, RenderUtil.alpha(col, a));
		// interruptor
		int swW = 24, swH = 12, swx = x + w - swW - 9, swy = ry + (ROW_H - swH) / 2;
		RenderUtil.roundRect(g, swx, swy, swW, swH, 6, RenderUtil.alpha(RenderUtil.mix(0xFF35507F, BLUE, t), a));
		int kx = swx + 2 + Math.round((swW - swH) * t);
		RenderUtil.roundRect(g, kx, swy + 2, swH - 4, swH - 4, 4, RenderUtil.alpha(0xFFFFFFFF, a));
		// seta de ajustes
		boolean open = EXPANDED.contains(m);
		String arrow = open ? "v" : ">";
		FontUtil.draw(g, arrow, swx - 14 - FontUtil.width(arrow) / 2, ry + (ROW_H - fh) / 2, RenderUtil.alpha(0xFFB6CCEB, a));
		if (hover) tipNow = m;

		float e = expAnim(m);
		if (e > 0.01f) {
			int hh = Math.round(settingsH(m) * e);
			int sy0 = ry + ROW_H + 1;
			g.enableScissor(x, Math.max(sy0, clipY), x + w, Math.min(sy0 + hh, clipY + clipH));
			RenderUtil.roundRect(g, x, sy0, w, Math.max(2, hh - 2), 7, RenderUtil.alpha(0xFF0B1730, 0.34f * a));
			int sy = sy0 + 3;
			for (Setting s : m.settings()) {
				int sh = SettingRenderer.height(s, w);
				SettingRenderer.shortLabels = true;
				SettingRenderer.accentColor = BLUE_DK;
				SettingRenderer.flat = true;
				try {
					SettingRenderer.draw(g, s, x, sy, w, sh, a, 0f);
				} finally {
					SettingRenderer.shortLabels = false;
					SettingRenderer.accentColor = 0xFFFFFFFF;
					SettingRenderer.flat = false;
				}
				sy += sh;
			}
			g.disableScissor();
		}
	}

	// ---------- entrada ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x() / UI, my = click.y() / UI;
		int button = click.button();
		deferred = null;
		moved = false;
		scrolling = false;
		dragInt = null;
		searchFocused = RenderUtil.inside(mx, my, searchX(), wy() + 5, searchW(), HEAD_H - 10);
		if (searchFocused) return true;
		int bs = HEAD_H - 12;
		if (RenderUtil.inside(mx, my, closeX(), wy() + 6, bs, bs)) {
			this.onClose();
			return true;
		}
		// categorias
		int sbx = wx() + 5, sby = wy() + HEAD_H + 6, sbw = SIDE_W - 8;
		for (int i = 0; i < CATS.length; i++) {
			if (RenderUtil.inside(mx, my, sbx + 3, sby + 5 + i * 23, sbw - 6, 21)) {
				current = CATS[i];
				search = "";
				scroll = 0f;
				return true;
			}
		}
		int cx = cx(), cw = cw(), cy = cy(), ch = ch();
		if (!RenderUtil.inside(mx, my, cx, cy, cw, ch)) return super.mouseClicked(click, doubled);
		pressY = my;
		int ry = cy - Math.round(scroll);
		for (Module m : list()) {
			int blk = blockH(m);
			if (my >= ry && my < ry + blk) {
				if (my < ry + ROW_H) {
					boolean arrow = mx >= cx + cw - 56 && mx < cx + cw - 34;
					if (button == 1 || arrow) deferred = () -> toggleExpand(m);
					else if (button == 0) deferred = () -> { m.toggle(); Config.save(); };
				} else {
					int sy = ry + ROW_H + 4;
					for (Setting s : m.settings()) {
						int sh = SettingRenderer.height(s, cw);
						if (my >= sy && my < sy + sh) {
							Runnable[] pend = new Runnable[1];
							IntSetting di = SettingRenderer.click(s, cx, sy, cw, sh, mx, my, FontUtil.height(), pend);
							if (di != null) {
								dragInt = di;
								dragIx = cx + 7;
								dragIw = cw - 14;
								Config.save();
							} else if (pend[0] != null) {
								deferred = pend[0];
							}
							break;
						}
						sy += sh;
					}
				}
				break;
			}
			ry += blk;
		}
		if (dragInt == null && !SettingRenderer.dragging()) {
			scrolling = true;
			scrollStart = scroll;
		}
		return true;
	}

	private void toggleExpand(Module m) {
		if (!EXPANDED.remove(m)) EXPANDED.add(m);
		EXP.putIfAbsent(m, 0f);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		double mx = click.x() / UI, my = click.y() / UI;
		if (dragInt != null) {
			dragInt.setFraction((float) ((mx - dragIx) / Math.max(1, dragIw)));
			return true;
		}
		if (SettingRenderer.dragging()) {
			SettingRenderer.drag(mx, my);
			return true;
		}
		if (scrolling) {
			double dy = my - pressY;
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
		boolean was = dragInt != null || scrolling || SettingRenderer.dragging();
		if (dragInt != null) Config.save();
		SettingRenderer.release();
		if (deferred != null && !moved) deferred.run();
		deferred = null;
		dragInt = null;
		scrolling = false;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (float) (verticalAmount * 18.0);
		clampScroll();
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		int cp = input.codepoint();
		if (searchFocused && cp >= 32 && cp != 127 && search.length() < 24) {
			search += new String(Character.toChars(cp));
			scroll = 0f;
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();
		for (Module m : ModuleManager.all()) {
			java.util.List<com.witch.setting.KeybindSetting> ks = new java.util.ArrayList<>();
			ks.add(m.keybind);
			for (var st : m.settings()) if (st instanceof com.witch.setting.KeybindSetting k && k != m.keybind) ks.add(k);
			for (var k : ks) {
				if (!k.listening) continue;
				k.listening = false;
				if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) k.setKey(0);
				else if (key != GLFW.GLFW_KEY_ESCAPE) k.setKey(key);
				Config.save();
				return true;
			}
		}
		if (searchFocused) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
				scroll = 0f;
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				searchFocused = false;
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(input);
	}
}
