package com.witch.gui;

import com.witch.config.Config;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import com.witch.setting.KeybindSetting;
import com.witch.setting.OreSetting;
import com.witch.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.witch.gui.ClickGuiMetrics.*;

/**
 * Desenho e clique dos settings dentro de um módulo expandido.
 * Lógica idêntica à do ClickGuiScreen original.
 */
public final class SettingRenderer {
	private SettingRenderer() {}

	private static final int SV_H = 34, HUE_H = 7, PICK_GAP = 4;

	// arraste do seletor de cor (um por vez)
	private static ColorSetting dragColor;
	private static int dragMode; // 1 = quadrado SV, 2 = barra de matiz
	private static int dragX, dragY, dragW;

	public static boolean dragging() { return dragColor != null; }

	public static void drag(double mx, double my) {
		if (dragColor == null) return;
		float fx = (float) Math.max(0, Math.min(1, (mx - dragX) / Math.max(1, dragW)));
		if (dragMode == 1) {
			float fy = (float) Math.max(0, Math.min(1, (my - dragY) / SV_H));
			dragColor.setHsv(dragColor.hue, fx, 1f - fy);
		} else {
			dragColor.setHsv(fx, dragColor.sat == 0f && dragColor.val == 0f ? 1f : dragColor.sat, dragColor.val == 0f ? 1f : dragColor.val);
		}
	}

	public static void release() {
		if (dragColor != null) Config.save();
		dragColor = null;
	}

	/** Opcao de sim/nao (0 ou 1): desenhada como interruptor. */
	private static boolean isToggle(Setting s) {
		return s instanceof IntSetting is && is.min == 0 && is.max == 1;
	}

	/** Escolha entre poucas opcoes (ex.: estilo 1..7): vira botao com setas em vez de slider. */
	private static boolean isStepper(Setting s) {
		return s instanceof IntSetting is && !isToggle(is) && is.max > is.min && is.max - is.min < 8;
	}

	private static final java.util.Map<IntSetting, String[]> OPTS = new java.util.IdentityHashMap<>();

	/** Nomes das opcoes tirados da legenda "(1 ciano 2 roxo ...)"; se a legenda nao der certo, usa os numeros. */
	private static String[] options(IntSetting s) {
		String[] cached = OPTS.get(s);
		if (cached != null) return cached;
		int n = s.max - s.min + 1;
		String[] out = new String[n];
		for (int i = 0; i < n; i++) out[i] = String.valueOf(s.min + i);
		int o = s.name.indexOf('('), c = o < 0 ? -1 : s.name.indexOf(')', o + 1);
		if (o >= 0 && c > o) {
			String in = s.name.substring(o + 1, c);
			int[] st = new int[n];
			int from = 0;
			boolean ok = true;
			for (int i = 0; i < n && ok; i++) {
				String num = String.valueOf(s.min + i);
				int p = from, found = -1;
				while ((p = in.indexOf(num, p)) >= 0) {
					boolean before = p == 0 || in.charAt(p - 1) == ' ' || in.charAt(p - 1) == ',';
					boolean after = p + num.length() < in.length() && in.charAt(p + num.length()) == ' ';
					if (before && after) { found = p; break; }
					p++;
				}
				if (found < 0) ok = false;
				else { st[i] = found; from = found + num.length() + 1; }
			}
			if (ok) {
				for (int i = 0; i < n; i++) {
					int b = st[i] + String.valueOf(s.min + i).length() + 1;
					int e = i + 1 < n ? st[i + 1] : in.length();
					String lab = in.substring(b, e);
					int semi = lab.indexOf(';');
					if (semi >= 0) lab = lab.substring(0, semi);
					lab = lab.trim();
					while (lab.endsWith(",")) lab = lab.substring(0, lab.length() - 1).trim();
					if (!lab.isEmpty()) out[i] = lab;
				}
			}
		}
		OPTS.put(s, out);
		return out;
	}

	private static String toggleName(String n) {
		return n.replace(" (0/1)", "");
	}

	/** Janelas estreitas (ClickGUI em janelas): mostra so o nome, sem a legenda das opcoes (ela vai no tooltip). */
	public static boolean shortLabels;
	/** Cor dos controles ligados (interruptor, barra do slider). */
	public static int accentColor = 0xFFFFFFFF;

	private static String label(String n, int maxW) {
		if (shortLabels) {
			int i = n.indexOf(" (");
			if (i > 0) n = n.substring(0, i);
		}
		return ModuleRow.fit(n, maxW);
	}

	public static int height(Setting s, int w) {
		if (isToggle(s)) return ORE_H;
		if (isStepper(s)) return FontUtil.height() + 23;
		if (s instanceof IntSetting) return INT_H;
		if (s instanceof ColorSetting) {
			int per = Math.max(1, (w - 16) / (SW + SW_GAP));
			int rows = (ColorSetting.PALETTE.length + per - 1) / per;
			return 12 + rows * (SW + SW_GAP) + 3 + PICK_GAP + SV_H + PICK_GAP + HUE_H + 4;
		}
		return ORE_H;
	}

	public static void draw(GuiGraphicsExtractor g, Setting s, int x, int ry, int w, int sh, float a, float glAdd) {
		int fh = FontUtil.height();
		if (isToggle(s)) {
			IntSetting t = (IntSetting) s;
			boolean on = t.get() == 1;
			FontUtil.draw(g, label(toggleName(t.name), w - 40), x + 7, ry + (sh - fh) / 2, RenderUtil.alpha(on ? TXT : TXT_DIM, a));
			int sx = x + w - 6 - 20;
			int sy = ry + (sh - 10) / 2;
			RenderUtil.roundRect(g, sx, sy, 20, 10, 5, RenderUtil.alpha(on ? accentColor : 0xFF2A2A2D, a));
			int kx = on ? sx + 11 : sx + 1;
			RenderUtil.roundRect(g, kx, sy + 1, 8, 8, 4, RenderUtil.alpha(on ? 0xFF0A0C0F : 0xFF8A8F97, a));
		} else if (isStepper(s)) {
			IntSetting is = (IntSetting) s;
			String[] op = options(is);
			int n = op.length;
			int idx = is.get() - is.min;
			FontUtil.draw(g, label(is.name, w - 14), x + 7, ry + 3, RenderUtil.alpha(TXT_DIM, a));
			int px = x + 7, pw = w - 14, py = ry + fh + 7, ph = 13;
			int arrow = RenderUtil.mix(accentColor, 0xFFFFFFFF, 0.35f);
			RenderUtil.roundRect(g, px - 1, py - 1, pw + 2, ph + 2, 4, RenderUtil.alpha(RenderUtil.mix(0xFF2A2A2D, accentColor, 0.5f), a));
			RenderUtil.roundRect(g, px, py, pw, ph, 3, RenderUtil.alpha(0xFF0F0F11, a));
			FontUtil.draw(g, "<", px + 5, py + (ph - fh) / 2, RenderUtil.alpha(arrow, a));
			FontUtil.draw(g, ">", px + pw - 5 - FontUtil.width(">"), py + (ph - fh) / 2, RenderUtil.alpha(arrow, a));
			String lab = ModuleRow.fit(op[idx], pw - 30);
			FontUtil.draw(g, lab, px + (pw - FontUtil.width(lab)) / 2, py + (ph - fh) / 2, RenderUtil.alpha(TXT, a));
			int bw = Math.round((pw - 8) * (idx + 1) / (float) n);
			g.fill(px + 4, py + ph - 2, px + 4 + bw, py + ph - 1, RenderUtil.alpha(accentColor, a));
		} else if (s instanceof IntSetting is) {
			String val = is.get() + is.suffix;
			FontUtil.draw(g, label(is.name, w - 20 - FontUtil.width(val)), x + 7, ry + 3, RenderUtil.alpha(TXT_DIM, a));
			FontUtil.draw(g, val, x + w - 6 - FontUtil.width(val), ry + 3, RenderUtil.alpha(TXT, a));
			int tx = x + 7;
			int tw = w - 14;
			int ty = ry + fh + 8;
			RenderUtil.roundRect(g, tx, ty, tw, 3, 1, RenderUtil.alpha(0xFF2A2E35, a));
			int fw = Math.round(tw * is.fraction());
			if (fw > 0) RenderUtil.roundRect(g, tx, ty, fw, 3, 1, RenderUtil.alpha(accentColor, a));
			RenderUtil.roundRect(g, tx + fw - 4, ty - 3, 9, 9, 4, RenderUtil.alpha(0xFF3A3F47, a));
			RenderUtil.roundRect(g, tx + fw - 3, ty - 2, 7, 7, 3, RenderUtil.alpha(0xFFFFFFFF, a));
		} else if (s instanceof ColorSetting cs) {
			FontUtil.draw(g, label(cs.name, w - 14), x + 7, ry + 3, RenderUtil.alpha(TXT_DIM, a));
			int per = Math.max(1, (w - 16) / (SW + SW_GAP));
			for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
				int sx = x + 7 + (i % per) * (SW + SW_GAP);
				int sy = ry + 13 + (i / per) * (SW + SW_GAP);
				if (i == cs.index()) RenderUtil.roundRect(g, sx - 1, sy - 1, SW + 2, SW + 2, 3, RenderUtil.alpha(TXT, a));
				RenderUtil.roundRect(g, sx, sy, SW, SW, 2, RenderUtil.alpha(ColorSetting.PALETTE[i], a));
			}
			int rows = (ColorSetting.PALETTE.length + per - 1) / per;
			int py = ry + 13 + rows * (SW + SW_GAP) + PICK_GAP - SW_GAP + 1;
			int px = x + 7, pw = w - 14;
			RenderUtil.roundRect(g, px - 1, py - 1, pw + 2, SV_H + 2, 3, RenderUtil.alpha(0xFF3A3F47, a));
			for (int cxp = 0; cxp < pw; cxp += 2) {
				int cw2 = Math.min(2, pw - cxp);
				int top = ColorSetting.hsvToRgb(cs.hue, (cxp + cw2 / 2f) / pw, 1f);
				g.fillGradient(px + cxp, py, px + cxp + cw2, py + SV_H, RenderUtil.alpha(top, a), RenderUtil.alpha(0xFF000000, a));
			}
			int kx = px + Math.round(cs.sat * (pw - 1));
			int ky = py + Math.round((1f - cs.val) * (SV_H - 1));
			RenderUtil.roundRect(g, kx - 3, ky - 3, 7, 7, 3, RenderUtil.alpha(0xFFFFFFFF, a));
			RenderUtil.roundRect(g, kx - 2, ky - 2, 5, 5, 2, RenderUtil.alpha(cs.get(), a));
			int hy = py + SV_H + PICK_GAP;
			for (int cxp = 0; cxp < pw; cxp += 2) {
				int cw2 = Math.min(2, pw - cxp);
				g.fill(px + cxp, hy, px + cxp + cw2, hy + HUE_H, RenderUtil.alpha(ColorSetting.hsvToRgb((cxp + cw2 / 2f) / pw, 1f, 1f), a));
			}
			int hx = px + Math.round(cs.hue * (pw - 1));
			RenderUtil.roundRect(g, hx - 2, hy - 2, 5, HUE_H + 4, 2, RenderUtil.alpha(0xFFFFFFFF, a));
		} else if (s instanceof KeybindSetting ks) {
			FontUtil.draw(g, label(ks.name, w - 60), x + 7, ry + (sh - fh) / 2, RenderUtil.alpha(TXT_DIM, a));
			String lab = ks.listening ? "..." : ks.label();
			int cw = Math.max(26, FontUtil.width(lab) + 10);
			int cx = x + w - 6 - cw;
			RenderUtil.roundRect(g, cx, ry + 2, cw, sh - 4, 3, RenderUtil.alpha(ks.listening ? 0xFFE2E4E8 : 0xFF3A3F47, a));
			RenderUtil.roundRect(g, cx + 1, ry + 3, cw - 2, sh - 6, 2, RenderUtil.alpha(0xFF0F1114, a));
			FontUtil.draw(g, lab, cx + (cw - FontUtil.width(lab)) / 2, ry + (sh - fh) / 2, RenderUtil.alpha(0xFFB5B9C0, a));
		} else if (s instanceof OreSetting os) {
			int bx = x + 7;
			int bY = ry + (sh - 9) / 2;
			RenderUtil.roundRect(g, bx, bY, 9, 9, 3, RenderUtil.alpha(0xFF3A3F47, a));
			RenderUtil.roundRect(g, bx + 1, bY + 1, 7, 7, 2, RenderUtil.alpha(0xFF0F1114, a));
			if (os.enabled()) RenderUtil.roundRect(g, bx + 2, bY + 2, 5, 5, 1, RenderUtil.alpha(TXT, a));
			FontUtil.draw(g, label(os.name, w - 50), bx + 14, ry + (sh - fh) / 2, RenderUtil.alpha(os.enabled() ? TXT : TXT_DIM, a));
			int cx = x + w - 6 - 12;
			RenderUtil.roundRect(g, cx - 1, bY - 1, 14, 11, 3, RenderUtil.alpha(0xFF3A3F47, a));
			RenderUtil.roundRect(g, cx, bY, 12, 9, 2, RenderUtil.alpha(os.color(), a));
		}
	}

	/**
	 * Trata clique em setting. Retorna o IntSetting se o usuário começou a arrastar um slider;
	 * caso contrário null. Ações adiantadas vão em {@code pendingOut[0]}.
	 */
	public static IntSetting click(Setting s, int x, int ry, int w, int sh, double mx, double my, int fh,
								   Runnable[] pendingOut) {
		if (isToggle(s)) {
			IntSetting t = (IntSetting) s;
			pendingOut[0] = () -> { t.set(t.get() == 1 ? 0 : 1); Config.save(); };
			return null;
		}
		if (isStepper(s)) {
			IntSetting is = (IntSetting) s;
			final boolean back = mx < x + w / 2.0;
			pendingOut[0] = () -> {
				int n = is.max - is.min + 1;
				is.set(is.min + ((is.get() - is.min + (back ? -1 : 1)) % n + n) % n);
				Config.save();
			};
			return null;
		}
		if (s instanceof IntSetting is) {
			int tx = x + 7;
			int tw = w - 14;
			int ty = ry + fh + 8;
			if (RenderUtil.inside(mx, my, tx - 4, ty - 7, tw + 8, 17)) {
				is.setFraction((float) ((mx - tx) / Math.max(1, tw)));
				return is;
			}
		} else if (s instanceof ColorSetting cs) {
			int per = Math.max(1, (w - 16) / (SW + SW_GAP));
			for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
				int sx = x + 7 + (i % per) * (SW + SW_GAP);
				int sy = ry + 13 + (i / per) * (SW + SW_GAP);
				if (RenderUtil.inside(mx, my, sx - 2, sy - 2, SW + 4, SW + 4)) {
					final int idx = i;
					pendingOut[0] = () -> { cs.setIndex(idx); Config.save(); };
					return null;
				}
			}
			int rows = (ColorSetting.PALETTE.length + per - 1) / per;
			int py = ry + 13 + rows * (SW + SW_GAP) + PICK_GAP - SW_GAP + 1;
			int px = x + 7, pw = w - 14;
			if (RenderUtil.inside(mx, my, px, py, pw, SV_H)) {
				dragColor = cs; dragMode = 1; dragX = px; dragY = py; dragW = pw;
				drag(mx, my);
			} else if (RenderUtil.inside(mx, my, px, py + SV_H + PICK_GAP - 2, pw, HUE_H + 4)) {
				dragColor = cs; dragMode = 2; dragX = px; dragY = py; dragW = pw;
				drag(mx, my);
			}
		} else if (s instanceof KeybindSetting ks) {
			pendingOut[0] = () -> ks.listening = !ks.listening;
		} else if (s instanceof OreSetting os) {
			final boolean onChip = mx >= x + w - 6 - 16;
			pendingOut[0] = () -> {
				if (onChip) os.cycleColor(); else os.toggle();
				Config.save();
			};
		}
		return null;
	}
}
