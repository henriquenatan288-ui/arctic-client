package com.witch.gui;

import com.witch.config.Config;
import com.witch.module.Category;
import com.witch.module.Module;
import com.witch.module.ModuleManager;
import com.witch.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ClickGUI: barra de abas no topo (categorias + busca), modulos em barras largas (icone, nome, keybind, "+"),
 * fundo preto com listras e vermelho. O "+" abre o editor de ajustes do modulo em tela cheia.
 */
public class ClickGuiScreen extends Screen {

	private static Category selected = Category.values()[0];
	private static float scroll = 0f;

	private static final int RED = GuiBackdrop.RED;
	private static final int NAV_Y = 10, NAV_H = 26;
	private static final int ROW_H = 26, GAP = 6, PAD_X = 14;
	private static final float SMALL = 0.72f;
	/** Escala geral do ClickGUI (menor = mais coisa na tela). */
	private static final float UI = 0.8f;
	private int vw() { return Math.round(this.width / UI); }
	private int vh() { return Math.round(this.height / UI); }

	private String search = "";
	private boolean searchFocused = false;
	private float openAnim = 0f;
	private long lastNanos = 0L;
	private Module autoEditor;
	private Module tipNow, tipLast;
	private long tipSince;

	// toque/clique nas barras: so vale ao soltar se nao arrastou (assim da para rolar arrastando)
	private Runnable deferred;
	private double pressY;
	private float scrollStart;
	private boolean dragging, moved;

	public ClickGuiScreen() {
		this(null);
	}

	/** Com modulo: abre direto o editor de ajustes dele. */
	public ClickGuiScreen(Module openSettings) {
		super(Component.literal("Arctic Client"));
		if (openSettings != null) {
			selected = openSettings.category;
			autoEditor = openSettings;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- layout ----------

	private int userW() {
		return Math.max(78, FontUtil.width(TitleSkin.nick()) + 40);
	}

	private int searchW() {
		return Math.min(110, Math.max(70, vw() / 6));
	}

	private int searchX() {
		return vw() - 12 - searchW();
	}

	private int tabsX() {
		return 12 + userW() + 12;
	}

	private int tabW() {
		int avail = searchX() - 10 - tabsX();
		return Mth.clamp(avail / Category.values().length, 34, 64);
	}

	/** Abas compactas: se nao cabem todos os nomes, so a aba selecionada mostra o nome; as outras so o icone. */
	private boolean compactTabs() {
		int avail = searchX() - 10 - tabsX();
		return avail / Category.values().length < 54;
	}

	private int tabWidth(int i) {
		if (!compactTabs()) return tabW();
		int avail = searchX() - 10 - tabsX();
		Category[] cs = Category.values();
		int selW = Math.min(80, FontUtil.width(cs[Math.max(0, selected.ordinal())].label) + 30);
		int other = Math.max(22, (avail - selW) / (cs.length - 1));
		return cs[i] == selected && search.isEmpty() ? selW : Math.min(other, 38);
	}

	private int tabX(int i) {
		int x = tabsX();
		for (int k = 0; k < i; k++) x += tabWidth(k);
		return x;
	}

	private int listX() { return PAD_X; }
	private int listY() { return NAV_Y + NAV_H + 14; }
	private int listW() { return vw() - PAD_X * 2; }
	private int listH() { return vh() - listY() - 18; }

	private int cols() {
		return Mth.clamp((listW() + GAP) / (180 + GAP), 1, 3);
	}

	private int rowW() {
		int c = cols();
		return (listW() - (c - 1) * GAP) / c;
	}

	private List<Module> modules() {
		List<Module> out = new ArrayList<>();
		if (search.isEmpty()) {
			out.addAll(ModuleManager.byCategory(selected));
		} else {
			String q = search.toLowerCase(Locale.ROOT);
			for (Module m : ModuleManager.all()) if (m.name.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
		}
		return out;
	}

	private int contentH(int n) {
		int rows = (n + cols() - 1) / cols();
		return rows == 0 ? 0 : rows * ROW_H + (rows - 1) * GAP;
	}

	private void clampScroll(int n) {
		scroll = Mth.clamp(scroll, 0f, Math.max(0, contentH(n) - listH()));
	}

	private int rowX(int i) { return listX() + (i % cols()) * (rowW() + GAP); }
	private int rowY(int i) { return listY() + (i / cols()) * (ROW_H + GAP) - Math.round(scroll); }

	private int plusX(int rx) { return rx + rowW() - 24; }

	private boolean hasPlus(Module m) {
		for (var s : m.settings()) if (!(s instanceof KeybindSetting)) return true;
		return false;
	}

	private String keyLabel(Module m) {
		if (m.keybind.listening) return "...";
		return m.keybind.key() == 0 ? "Keybind" : m.keybind.label();
	}

	private int pillW(Module m) {
		return Math.max(36, Math.round(FontUtil.width(keyLabel(m)) * SMALL) + 12);
	}

	private int pillX(int rx, Module m) {
		int right = hasPlus(m) ? plusX(rx) - 6 : rx + rowW() - 8;
		return right - pillW(m);
	}

	// ---------- desenho ----------

	private static void small(GuiGraphicsExtractor g, String t, int x, float cy, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, cy - FontUtil.height() * SMALL / 2f);
		g.pose().scale(SMALL, SMALL);
		FontUtil.draw(g, t, 0, 0, color);
		g.pose().popMatrix();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		if (autoEditor != null) {
			Module m = autoEditor;
			autoEditor = null;
			Minecraft.getInstance().gui.setScreen(new ModuleEditorScreen(m, this));
			return;
		}
		long now = System.nanoTime();
		float speed = lastNanos == 0 ? 1f : Math.min(3f, (now - lastNanos) / 16_666_667f);
		lastNanos = now;
		openAnim = Mth.lerp(Math.min(1f, speed * com.witch.module.GuiAnimationModule.rate()), openAnim, 1f);
		float a = openAnim;
		try {
			this.extractBlurredBackground(g);
		} catch (Throwable ignored) {
			// sem desfoque
		}
		GuiBackdrop.draw(g, this.width, this.height, a);
		g.pose().pushMatrix();
		g.pose().scale(UI, UI);
		mx = Math.round(mx / UI);
		my = Math.round(my / UI);
		int fh = FontUtil.height();
		tipNow = null;

		nav(g, mx, my, a);

		List<Module> mods = modules();
		clampScroll(mods.size());
		int lx = listX(), ly = listY(), lw = listW(), lh = listH();
		g.enableScissor(lx - 4, ly - 4, lx + lw + 4, ly + lh + 4);
		int rw = rowW();
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			int rx = rowX(i), ry = rowY(i);
			if (ry + ROW_H < ly - 4 || ry > ly + lh + 4) continue;
			float off = (1f - a) * 10f * (1 + (i % cols()));
			row(g, m, rx, Math.round(ry + off), rw, mx, my, a, speed, ly, lh);
		}
		g.disableScissor();
		if (mods.isEmpty()) {
			String none = search.isEmpty() ? "Sem modulos nesta categoria." : "Nenhum modulo encontrado.";
			FontUtil.draw(g, none, (vw() - FontUtil.width(none)) / 2, ly + 20, RenderUtil.alpha(0xFF8E8688, a));
		}
		int total = contentH(mods.size());
		if (total > lh) {
			int th = Math.max(14, Math.round(lh * (lh / (float) total)));
			float f = scroll / Math.max(1, total - lh);
			RenderUtil.roundRect(g, lx + lw + 6, ly + Math.round((lh - th) * f), 2, th, 1, RenderUtil.alpha(RED, 0.8f * a));
		}

		small(g, Minecraft.getInstance().getFps() + " FPS   RSHIFT fecha", 12, vh() - 10, RenderUtil.alpha(0xFF6F686B, a));

		if (tipNow != tipLast) { tipLast = tipNow; tipSince = System.currentTimeMillis(); }
		if (tipNow != null && com.witch.module.GuiSettingsModule.tooltips() && System.currentTimeMillis() - tipSince > 450) {
			String d = ModuleDescriptions.of(tipNow.name);
			if (d != null && !d.isEmpty()) {
				int tw = Math.min(vw() - 16, FontUtil.width(d) + 12);
				int th = fh + 8;
				int tx = Mth.clamp(mx + 10, 6, vw() - tw - 6);
				int ty = Mth.clamp(my + 12, 6, vh() - th - 6);
				RenderUtil.roundRect(g, tx, ty, tw, th, 4, RenderUtil.alpha(0xFF0A090B, 0.97f * a));
				FontUtil.draw(g, ModuleRow.fit(d, tw - 10), tx + 6, ty + 4, RenderUtil.alpha(0xFFE8E4E5, a));
			}
		}
		g.pose().popMatrix();
	}

	private void nav(GuiGraphicsExtractor g, int mx, int my, float a) {
		int fh = FontUtil.height();
		// caixa do usuario: cabeca da skin + nome
		String nick = TitleSkin.nick();
		int ux = 12, uy = NAV_Y, uw = userW(), uh = NAV_H;
		RenderUtil.roundRect(g, ux, uy, uw, uh, 8, RenderUtil.alpha(0xFF050507, 0.30f * a));
		RenderUtil.roundRing(g, ux, uy, uw, uh, 8, RenderUtil.alpha(0x50FFFFFF, a));
		int hs = uh - 8;
		try {
			PlayerFaceExtractor.extractRenderState(g, TitleSkin.skin(), ux + 4, uy + 4, hs, RenderUtil.alpha(0xFFFFFFFF, a));
		} catch (Throwable ignored) {
			// sem cabeca
		}
		FontUtil.draw(g, ModuleRow.fit(nick, uw - hs - 14), ux + hs + 9, uy + 4, RenderUtil.alpha(0xFFF2EEEF, a));
		small(g, "Arctic Client", ux + hs + 9, uy + uh - 7, RenderUtil.alpha(RED, a));

		// abas
		Category[] cs = Category.values();
		for (int i = 0; i < cs.length; i++) {
			int tx = tabX(i), tw = tabWidth(i);
			boolean sel = cs[i] == selected && search.isEmpty();
			boolean hov = RenderUtil.inside(mx, my, tx, NAV_Y, tw, NAV_H);
			if (i > 0) g.fill(tx, NAV_Y + 6, tx + 1, NAV_Y + NAV_H - 6, RenderUtil.alpha(0xFFFFFFFF, 0.10f * a));
			if (sel) {
				// selecao fina: linha de 1px + brilho bem leve
				for (int k = 0; k < 3; k++) {
					g.fill(tx + 8, NAV_Y + NAV_H - 2 - k, tx + tw - 8, NAV_Y + NAV_H - 1 - k, RenderUtil.alpha(RED, 0.07f * (1f - k / 3f) * a));
				}
				g.fill(tx + 8, NAV_Y + NAV_H - 2, tx + tw - 8, NAV_Y + NAV_H - 1, RenderUtil.alpha(RED, a));
			}
			int col = sel ? 0xFFFFFFFF : (hov ? 0xFFE0DADC : 0xFF9A9395);
			if (compactTabs()) {
				String nm = sel ? cs[i].label : "";
				int iw = 9, gap = nm.isEmpty() ? 0 : 5;
				int total = iw + gap + (nm.isEmpty() ? 0 : FontUtil.width(nm));
				int ix = tx + (tw - total) / 2;
				CategoryIcons.draw(g, cs[i], ix + 1, NAV_Y + (NAV_H - 7) / 2, RenderUtil.alpha(col, a));
				if (!nm.isEmpty()) FontUtil.draw(g, nm, ix + iw + gap, NAV_Y + (NAV_H - fh) / 2 - 1, RenderUtil.alpha(col, a));
			} else {
				String nm = ModuleRow.fit(cs[i].label, tw - 6);
				FontUtil.draw(g, nm, tx + (tw - FontUtil.width(nm)) / 2, NAV_Y + (NAV_H - fh) / 2 - 1, RenderUtil.alpha(col, a));
			}
		}

		// busca
		int sx = searchX(), sy = NAV_Y + 2, sw = searchW(), sh = NAV_H - 4;
		RenderUtil.roundRect(g, sx, sy, sw, sh, 8, RenderUtil.alpha(0xFF050507, 0.30f * a));
		RenderUtil.roundRing(g, sx, sy, sw, sh, 8, RenderUtil.alpha(searchFocused ? RenderUtil.alpha(RED, 0.9f) : 0x50FFFFFF, a));
		CategoryIcons.blit(g, CategoryIcons.SEARCH, sx + sw - 15, sy + (sh - 9) / 2, 9, RenderUtil.alpha(searchFocused ? 0xFFFFFFFF : 0xFFA39C9E, a));
		String shown;
		int scol;
		if (search.isEmpty() && !searchFocused) {
			shown = "Search...";
			scol = 0xFF857E80;
		} else {
			boolean blink = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
			shown = search + (blink ? "|" : "");
			scol = 0xFFFFFFFF;
		}
		FontUtil.draw(g, ModuleRow.fit(shown, sw - 28), sx + 7, sy + (sh - fh) / 2, RenderUtil.alpha(scol, a));
		GuiBackdrop.redRule(g, 12, NAV_Y + NAV_H + 4, vw() - 24, a * 0.6f);
	}

	private void row(GuiGraphicsExtractor g, Module m, int rx, int ry, int rw, int mx, int my, float a, float speed, int clipY, int clipH) {
		int fh = FontUtil.height();
		boolean hover = RenderUtil.inside(mx, my, rx, ry, rw, ROW_H) && my >= clipY && my < clipY + clipH && !dragging;
		m.toggleAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.toggleAnim, m.isEnabled() ? 1f : 0f);
		m.hoverAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.hoverAnim, hover ? 1f : 0f);
		float t = m.toggleAnim;
		int h = ROW_H;
		// vidro preto quase transparente com borda fina
		int border = RenderUtil.mix(RenderUtil.mix(0x5CFFFFFF, 0x8CFFFFFF, m.hoverAnim), RenderUtil.alpha(RED, 0.95f), t * 0.75f);
		RenderUtil.roundRect(g, rx, ry, rw, h, 7, RenderUtil.alpha(RenderUtil.mix(0xFF050507, 0xFF0C1830, t), (0.20f + 0.16f * t) * a));
		RenderUtil.roundRing(g, rx, ry, rw, h, 7, RenderUtil.alpha(border, a));
		// cantos com luz vermelha (sempre sutil; mais forte ligado)
		RenderUtil.glow(g, rx + 5, ry + 4, 18, RenderUtil.alpha(RED, (0.10f + 0.28f * t) * a));
		RenderUtil.glow(g, rx + rw - 5, ry + h - 4, 18, RenderUtil.alpha(RED, (0.10f + 0.26f * t) * a));
		if (m.hoverAnim > 0.02f) {
			RenderUtil.roundRect(g, rx + 1, ry + 1, Math.max(10, Math.round((rw - 2) * m.hoverAnim)), h - 2, 6, RenderUtil.alpha(0x0AFFFFFF, a));
		}
		// icone + divisoria
		CategoryIcons.draw(g, m.category, rx + 9, ry + (h - 7) / 2, RenderUtil.alpha(RenderUtil.mix(0xFF9A9294, 0xFF9CCBFF, t), a));
		g.fill(rx + 26, ry + 6, rx + 27, ry + h - 6, RenderUtil.alpha(0xFFFFFFFF, 0.12f * a));
		boolean plus = hasPlus(m);
		int px = plusX(rx);
		int nameMax = (pillX(rx, m) - 6) - (rx + 34);
		if (nameMax < 24) nameMax = (plus ? px : rx + rw - 8) - (rx + 34) - 4;
		boolean pillOk = pillVisible(rx, m);
		int tc = RenderUtil.mix(0xFFC2BCBE, 0xFFFFFFFF, t);
		FontUtil.draw(g, ModuleRow.fit(m.name, Math.max(16, nameMax)), rx + 34, ry + (h - fh) / 2, RenderUtil.alpha(tc, a));
		if (pillOk) {
			int kw = pillW(m), kx = pillX(rx, m), kh = 14, ky = ry + (h - kh) / 2;
			boolean lis = m.keybind.listening;
			RenderUtil.roundRect(g, kx, ky, kw, kh, 7, RenderUtil.alpha(0xFF030304, 0.28f * a));
			RenderUtil.roundRing(g, kx, ky, kw, kh, 7, RenderUtil.alpha(lis ? RenderUtil.alpha(RED, 0.95f) : 0x70FFFFFF, a));
			String kl = keyLabel(m);
			int tw = Math.round(FontUtil.width(kl) * SMALL);
			small(g, kl, kx + (kw - tw) / 2, ky + kh / 2f, RenderUtil.alpha(m.keybind.key() == 0 && !lis ? 0xFF9A9294 : 0xFFEDE8E9, a));
		}
		if (plus) {
			boolean ph = RenderUtil.inside(mx, my, px, ry, 24, h) && hover;
			RenderUtil.roundRect(g, px, ry + 1, 23, h - 2, 6, RenderUtil.alpha(ph ? 0x16FFFFFF : 0x08FFFFFF, a));
			g.fill(px, ry + 4, px + 1, ry + h - 4, RenderUtil.alpha(0xFFFFFFFF, 0.16f * a));
			int pc = RenderUtil.alpha(ph ? 0xFFFFFFFF : 0xFFB5AEB0, a);
			int cx = px + 12, cy = ry + h / 2;
			g.fill(cx - 4, cy, cx + 5, cy + 1, pc);
			g.fill(cx, cy - 4, cx + 1, cy + 5, pc);
		}
		if (hover) tipNow = m;
	}

	// ---------- entrada ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x() / UI, my = click.y() / UI;
		int button = click.button();
		deferred = null;
		dragging = false;
		searchFocused = RenderUtil.inside(mx, my, searchX(), NAV_Y + 2, searchW(), NAV_H - 4);
		if (searchFocused) return true;
		Category[] cs = Category.values();
		for (int i = 0; i < cs.length; i++) {
			if (RenderUtil.inside(mx, my, tabX(i), NAV_Y, tabWidth(i), NAV_H)) {
				selected = cs[i];
				search = "";
				scroll = 0f;
				return true;
			}
		}
		int lx = listX(), ly = listY(), lw = listW(), lh = listH();
		if (!RenderUtil.inside(mx, my, lx, ly, lw, lh)) return super.mouseClicked(click, doubled);
		List<Module> mods = modules();
		int rw = rowW();
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			int rx = rowX(i), ry = rowY(i);
			if (!RenderUtil.inside(mx, my, rx, ry, rw, ROW_H)) continue;
			boolean plus = hasPlus(m);
			if (plus && mx >= plusX(rx)) {
				deferred = () -> Minecraft.getInstance().gui.setScreen(new ModuleEditorScreen(m, this));
			} else if (button == 1 && mx >= pillX(rx, m) && mx < pillX(rx, m) + pillW(m) && pillVisible(rx, m)) {
				deferred = () -> { m.keybind.listening = false; m.keybind.setKey(0); Config.save(); };
			} else if (button == 0 && mx >= pillX(rx, m) && mx < pillX(rx, m) + pillW(m) && pillVisible(rx, m)) {
				deferred = () -> m.keybind.listening = !m.keybind.listening;
			} else if (button == 1 && plus) {
				deferred = () -> Minecraft.getInstance().gui.setScreen(new ModuleEditorScreen(m, this));
			} else if (button == 0) {
				deferred = () -> { m.toggle(); Config.save(); };
			}
			break;
		}
		pressY = my;
		scrollStart = scroll;
		dragging = true;
		moved = false;
		return true;
	}

	private boolean pillVisible(int rx, Module m) {
		return pillX(rx, m) - 6 - (rx + 34) >= 24;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (dragging) {
			double dy = click.y() / UI - pressY;
			if (moved || Math.abs(dy) > 3) {
				moved = true;
				deferred = null;
				scroll = scrollStart - (float) dy;
				clampScroll(modules().size());
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		boolean was = dragging;
		if (deferred != null && !moved) deferred.run();
		deferred = null;
		dragging = false;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (float) (verticalAmount * 18.0);
		clampScroll(modules().size());
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
			if (m.keybind.listening) {
				m.keybind.listening = false;
				if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) m.keybind.setKey(0);
				else if (key != GLFW.GLFW_KEY_ESCAPE) m.keybind.setKey(key);
				Config.save();
				return true;
			}
		}
		if (searchFocused) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
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
