package com.witch.gui;

import com.witch.config.Config;
import com.witch.module.Category;
import com.witch.module.Module;
import com.witch.module.ModuleManager;
import com.witch.setting.IntSetting;
import com.witch.setting.KeybindSetting;
import com.witch.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.witch.gui.ClickGuiMetrics.TXT;

/**
 * ClickGUI em paineis flutuantes: um painel de vidro por categoria (arrastavel, recolhivel, com rolagem propria),
 * barra superior com busca, perfis, musica e HUD. Fundo com desfoque nativo e destaque azul.
 */
public class ClickGuiScreen extends Screen {

	/** Estado de um painel (persiste entre aberturas enquanto o jogo roda). */
	private static final class P {
		float x, y;
		boolean placed, collapsed;
		float coll;          // 0 = aberto, 1 = recolhido (animado)
		float scroll;
		int cap = -1;        // altura maxima do painel; -1 = ate o fim da tela
	}

	private static final Map<Category, P> PANELS = new EnumMap<>(Category.class);
	private static final List<Category> ORDER = new ArrayList<>(List.of(Category.values()));
	private static final Set<Module> OPEN = new HashSet<>();
	private static final Map<Module, Float> EXP = new java.util.HashMap<>();
	private static boolean autoPlace = true;
	private static int lastW = -1, lastH = -1;

	static {
		for (Category c : Category.values()) PANELS.put(c, new P());
	}

	private static final int PAD = 4, HH = 19, CARD = 15, GAP = 2, SPACE = 6, TOP = 38;
	private static final float SMALL = 0.72f;
	private static final int BRAND_BLUE = 0xFF5AA8FF, BRAND_VIOLET = 0xFF8E7BFF;

	private String search = "";
	private boolean searchFocused = false;
	private float openAnim = 0f;
	private long lastNanos = 0L;
	private IntSetting slider = null;
	private int sliderX = 0, sliderW = 1;
	private Runnable deferred;
	private Module tipNow = null, tipLast = null;
	private long tipSince = 0L;

	// arraste de painel
	private Category dragC = null;
	private double dragDX, dragDY, pressX, pressY;
	private boolean dragMoved;
	private float lastSpeed = 1f;

	public ClickGuiScreen() {
		this(null);
	}

	/** Se 'openSettings' nao for nulo, abre com os ajustes dele expandidos. */
	public ClickGuiScreen(Module openSettings) {
		super(Component.literal("Witch Client"));
		if (openSettings != null) {
			PANELS.get(openSettings.category).collapsed = false;
			if (!openSettings.settings().isEmpty()) OPEN.add(openSettings);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- cores ----------

	/** Cor principal: azul da marca, a menos que o usuario escolha outra em GUI Settings. */
	private static int accA() {
		int c = com.witch.module.GuiSettingsModule.accent();
		return c == 0xFFFFFFFF ? BRAND_BLUE : c;
	}

	private static int accB() {
		int c = com.witch.module.GuiSettingsModule.accent();
		return c == 0xFFFFFFFF ? BRAND_VIOLET : RenderUtil.mix(c, 0xFFFFFFFF, 0.35f);
	}

	private static int rgb(int c, int alpha8) {
		return (c & 0xFFFFFF) | (alpha8 << 24);
	}

	// ---------- layout ----------

	private int panelW() {
		int n = Category.values().length;
		int avail = this.width - 12;
		return Mth.clamp((avail - (n - 1) * SPACE) / n, 100, 124);
	}

	private void place() {
		Category[] cs = Category.values();
		int n = cs.length, pw = panelW();
		int avail = this.width - 12;
		int cols = Math.max(1, Math.min(n, (avail + SPACE) / (pw + SPACE)));
		int rows = (n + cols - 1) / cols;
		int totalW = cols * pw + (cols - 1) * SPACE;
		int x0 = (this.width - totalW) / 2;
		int rowH = (this.height - TOP - 8) / rows;
		for (int i = 0; i < n; i++) {
			P p = PANELS.get(cs[i]);
			if (p.placed && !autoPlace) continue;
			p.x = x0 + (i % cols) * (pw + SPACE);
			p.y = TOP + (i / cols) * rowH;
			p.cap = rowH - SPACE;
			p.placed = true;
		}
		autoPlace = false;
	}

	private void ensureLayout() {
		if (this.width != lastW || this.height != lastH) {
			autoPlace = true;
			lastW = this.width;
			lastH = this.height;
		}
		place();
	}

	private int cap(P p) {
		return p.cap > 0 ? p.cap : Math.max(70, this.height - (int) p.y - 8);
	}

	private record Slot(Module m, boolean block, int y, int h) {}

	private List<Module> modules(Category c) {
		List<Module> all = ModuleManager.byCategory(c);
		if (search.isEmpty()) return all;
		String q = search.toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<>();
		for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
		return out;
	}

	private int innerW() {
		return panelW() - PAD * 2;
	}

	private int settingsW() {
		return innerW() - 10;
	}

	private List<Slot> slots(Category c) {
		List<Slot> out = new ArrayList<>();
		int y = 0;
		for (Module m : modules(c)) {
			float p = EXP.getOrDefault(m, 0f);
			out.add(new Slot(m, false, y, CARD));
			y += CARD;
			if (p > 0.01f && !m.settings().isEmpty()) {
				y += 2;
				int total = 0;
				for (Setting st : m.settings()) total += SettingRenderer.height(st, settingsW());
				int shown = Math.round(total * (p * p * (3f - 2f * p)));
				out.add(new Slot(m, true, y, shown));
				y += shown;
			}
			y += GAP;
		}
		return out;
	}

	private static int contentH(List<Slot> sl) {
		int h = 0;
		for (Slot s : sl) h = Math.max(h, s.y + s.h);
		return h;
	}

	/** Altura util do corpo (sem recolher). */
	private int bodyFull(P p, List<Slot> sl) {
		int want = contentH(sl) + PAD * 2;
		return Math.max(0, Math.min(want, cap(p) - HH));
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	private int panelH(P p, List<Slot> sl) {
		int body = bodyFull(p, sl);
		return HH + Math.round(body * (1f - ease(p.coll)));
	}

	private boolean visible(Category c) {
		return search.isEmpty() || !modules(c).isEmpty();
	}

	private void clampScroll(P p, List<Slot> sl) {
		int max = Math.max(0, contentH(sl) + PAD * 2 - bodyFull(p, sl));
		p.scroll = Mth.clamp(p.scroll, 0f, max);
	}

	// barra superior
	private int barW() { return Math.min(this.width - 12, 360); }
	private int barX() { return (this.width - barW()) / 2; }
	private static final int BAR_Y = 8, BAR_H = 22;
	private int searchX() { return barX() + 78; }
	private int searchW() { return 108; }
	private int btnW(String t) { return FontUtil.width(t) + 14; }
	private int hudX() { return barX() + barW() - 6 - btnW("HUD"); }
	private int musicX() { return hudX() - 4 - btnW("Music"); }
	private int perfX() { return musicX() - 4 - btnW("Perfis"); }

	// ---------- desenho ----------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		ensureLayout();
		float e = Mth.clamp(openAnim, 0f, 1f);
		float inv = 1f - e;
		float cx = this.width / 2f, cy = this.height / 2f;
		float tx = 0f, ty = 0f, sc = 1f;
		switch (com.witch.module.GuiAnimationModule.style()) {
			case 1 -> sc = 0.85f + 0.15f * e;
			case 2 -> ty = inv * 30f;
			case 3 -> ty = -inv * 30f;
			case 4 -> tx = -inv * 40f;
			case 5 -> sc = 0.8f + 0.2f * e + 0.05f * (float) Math.sin(Math.PI * e);
			default -> { }
		}
		try {
			this.extractBlurredBackground(g);
		} catch (Throwable ignored) {
			// sem desfoque: os paineis continuam translucidos
		}
		boolean moved = tx != 0f || ty != 0f || sc != 1f;
		if (moved) {
			g.pose().pushMatrix();
			g.pose().translate(cx + tx, cy + ty);
			g.pose().scale(sc, sc);
			g.pose().translate(-cx, -cy);
		}
		body(g, mx, my);
		if (moved) g.pose().popMatrix();
	}

	private static void small(GuiGraphicsExtractor g, String t, int x, float cy, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, cy - FontUtil.height() * SMALL / 2f);
		g.pose().scale(SMALL, SMALL);
		FontUtil.draw(g, t, 0, 0, color);
		g.pose().popMatrix();
	}

	/** Linha horizontal com degrade entre duas cores. */
	private static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		int step = 2;
		for (int i = 0; i < w; i += step) {
			g.fill(x + i, y, x + Math.min(w, i + step), y + h, RenderUtil.mix(c1, c2, i / (float) Math.max(1, w - 1)));
		}
	}

	private void body(GuiGraphicsExtractor g, int mx, int my) {
		long now = System.nanoTime();
		float speed = lastNanos == 0 ? 1f : Math.min(3f, (now - lastNanos) / 16_666_667f);
		lastNanos = now;
		lastSpeed = speed;
		openAnim = Mth.lerp(Math.min(1f, speed * com.witch.module.GuiAnimationModule.rate()), openAnim, 1f);
		float a = openAnim;
		int fh = FontUtil.height();

		for (Category c : Category.values()) {
			P p = PANELS.get(c);
			float t = p.collapsed ? 1f : 0f;
			p.coll += (t - p.coll) * Math.min(1f, speed * 0.25f);
			if (Math.abs(t - p.coll) < 0.004f) p.coll = t;
			for (Module m : ModuleManager.byCategory(c)) {
				boolean want = OPEN.contains(m) && !m.settings().isEmpty();
				float ep = EXP.getOrDefault(m, 0f), et = want ? 1f : 0f;
				ep += (et - ep) * Math.min(1f, speed * 0.22f);
				if (Math.abs(et - ep) < 0.004f) ep = et;
				EXP.put(m, ep);
			}
		}
		tipNow = null;

		g.fill(0, 0, this.width, this.height, RenderUtil.alpha(0x38000000, a));

		topBar(g, mx, my, a);

		int idx = 0;
		for (Category c : new ArrayList<>(ORDER)) {
			if (!visible(c)) continue;
			float pa = Mth.clamp(a * 1.7f - idx * 0.1f, 0f, 1f);
			idx++;
			panel(g, c, mx, my, pa, speed);
		}

		String fps = Minecraft.getInstance().getFps() + " FPS";
		small(g, fps + "   RSHIFT fecha", 8, this.height - 8, RenderUtil.alpha(0xFF6B7078, a));

		// tooltip
		if (tipNow != tipLast) { tipLast = tipNow; tipSince = System.currentTimeMillis(); }
		if (tipNow != null && com.witch.module.GuiSettingsModule.tooltips() && System.currentTimeMillis() - tipSince > 450) {
			String d = ModuleDescriptions.of(tipNow.name);
			if (d != null && !d.isEmpty()) {
				int tw = Math.min(this.width - 16, FontUtil.width(d) + 12);
				int th = fh + 8;
				int tx = Mth.clamp(mx + 10, 6, this.width - tw - 6);
				int ty = Mth.clamp(my + 12, 6, this.height - th - 6);
				RenderUtil.roundRect(g, tx - 1, ty - 1, tw + 2, th + 2, 5, RenderUtil.alpha(rgb(accA(), 0x80), a));
				RenderUtil.roundRect(g, tx, ty, tw, th, 4, RenderUtil.alpha(0xFF0C0E11, a * 0.97f));
				FontUtil.draw(g, ModuleRow.fit(d, tw - 10), tx + 6, ty + 4, RenderUtil.alpha(TXT, a));
			}
		}
	}

	private void topBar(GuiGraphicsExtractor g, int mx, int my, float a) {
		int fh = FontUtil.height();
		int x = barX(), y = BAR_Y, w = barW(), h = BAR_H;
		RenderUtil.softShadow(g, x, y + 3, w, h, 12, RenderUtil.alpha(0xFF000000, 0.5f * a));
		RenderUtil.roundRect(g, x, y, w, h, h / 2, RenderUtil.alpha(0xFFFFFFFF, 0.13f * a));
		RenderUtil.roundRect(g, x + 1, y + 1, w - 2, h - 2, h / 2 - 1, RenderUtil.alpha(0xFF12151B, 0.62f * a));
		// logo
		int ls = 12, lx = x + 9, ly = y + (h - ls) / 2;
		RenderUtil.glow(g, lx + ls / 2, ly + ls / 2, 14, RenderUtil.alpha(accA(), 0.45f * a));
		RenderUtil.roundRect(g, lx, ly, ls, ls, 4, RenderUtil.alpha(accA(), a));
		RenderUtil.roundRect(g, lx + 3, ly + 3, ls - 6, ls - 6, 2, RenderUtil.alpha(accB(), a));
		FontUtil.draw(g, "Witch", lx + ls + 6, y + (h - fh) / 2, RenderUtil.alpha(0xFFF2F3F5, a));
		g.fill(searchX() - 6, y + 5, searchX() - 5, y + h - 5, RenderUtil.alpha(0xFFFFFFFF, 0.14f * a));
		// busca
		int sx = searchX(), sy = y + 3, sw = searchW(), sh = h - 6;
		RenderUtil.roundRect(g, sx, sy, sw, sh, sh / 2, RenderUtil.alpha(searchFocused ? rgb(accA(), 0xAA) : 0x22FFFFFF, a));
		RenderUtil.roundRect(g, sx + 1, sy + 1, sw - 2, sh - 2, sh / 2 - 1, RenderUtil.alpha(0xCC0B0D11, a));
		CategoryIcons.blit(g, CategoryIcons.SEARCH, sx + 6, sy + (sh - 8) / 2, 8, RenderUtil.alpha(0xFF8A8F97, a));
		String shown;
		int scol;
		if (search.isEmpty() && !searchFocused) {
			shown = "Buscar modulos";
			scol = 0xFF7A7F87;
		} else {
			boolean blink = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
			shown = search + (blink ? "|" : "");
			scol = TXT;
		}
		FontUtil.draw(g, ModuleRow.fit(shown, sw - 24), sx + 17, sy + (sh - fh) / 2, RenderUtil.alpha(scol, a));
		// botoes
		button(g, "Perfis", perfX(), y, mx, my, a, false);
		button(g, "Music", musicX(), y, mx, my, a, false);
		button(g, "HUD", hudX(), y, mx, my, a, true);
	}

	private void button(GuiGraphicsExtractor g, String t, int bx, int by, int mx, int my, float a, boolean primary) {
		int fh = FontUtil.height();
		int bw = btnW(t), bh = BAR_H - 6, y = by + 3;
		boolean hov = RenderUtil.inside(mx, my, bx, y, bw, bh);
		if (primary) {
			RenderUtil.roundRect(g, bx, y, bw, bh, bh / 2, RenderUtil.alpha(RenderUtil.mix(accA(), 0xFFFFFFFF, hov ? 0.18f : 0f), a));
			FontUtil.draw(g, t, bx + 7, y + (bh - fh) / 2, RenderUtil.alpha(0xFF0B0D12, a));
		} else {
			RenderUtil.roundRect(g, bx, y, bw, bh, bh / 2, RenderUtil.alpha(hov ? 0x30FFFFFF : 0x16FFFFFF, a));
			FontUtil.draw(g, t, bx + 7, y + (bh - fh) / 2, RenderUtil.alpha(hov ? 0xFFFFFFFF : 0xFFC4C8CE, a));
		}
	}

	private void panel(GuiGraphicsExtractor g, Category c, int mx, int my, float a, float speed) {
		P p = PANELS.get(c);
		List<Slot> sl = slots(c);
		clampScroll(p, sl);
		int fh = FontUtil.height();
		int x = Math.round(p.x), pw = panelW();
		int y = Math.round(p.y + (1f - a) * 8f);
		int h = panelH(p, sl);
		int on = 0, total = ModuleManager.byCategory(c).size();
		for (Module m : ModuleManager.byCategory(c)) if (m.isEnabled()) on++;

		float op = Mth.clamp(com.witch.module.GuiSettingsModule.menuOpacity() * 0.62f, 0.42f, 0.88f) * a;
		RenderUtil.softShadow(g, x, y + 4, pw, h, 12, RenderUtil.alpha(0xFF000000, 0.55f * a));
		RenderUtil.roundRect(g, x, y, pw, h, 7, RenderUtil.alpha(0xFFFFFFFF, 0.13f * a));
		RenderUtil.roundRect(g, x + 1, y + 1, pw - 2, h - 2, 6, RenderUtil.alpha(0xFF12151B, op));
		// cabecalho
		g.enableScissor(x, y, x + pw, y + HH);
		RenderUtil.roundRect(g, x + 1, y + 1, pw - 2, h - 2, 6, RenderUtil.alpha(0xFF000000, 0.20f * a));
		g.disableScissor();
		g.fill(x + 8, y + 1, x + pw - 8, y + 2, RenderUtil.alpha(0xFFFFFFFF, 0.18f * a));
		boolean anyOn = on > 0;
		hgrad(g, x + 1, y + HH, pw - 2, 1, RenderUtil.alpha(rgb(accA(), anyOn ? 0x90 : 0x30), a), RenderUtil.alpha(rgb(accB(), anyOn ? 0x20 : 0x10), a));
		if (anyOn) RenderUtil.glow(g, x + 12, y + HH / 2, 12, RenderUtil.alpha(accA(), 0.30f * a));
		CategoryIcons.draw(g, c, x + 8, y + (HH - 7) / 2, RenderUtil.alpha(anyOn ? accA() : 0xFF9AA0A8, a));
		FontUtil.draw(g, c.label, x + 20, y + (HH - fh) / 2, RenderUtil.alpha(0xFFF2F3F5, a));
		g.pose().pushMatrix();
		g.pose().translate(x + pw - 11 + 3.5f, y + HH / 2f);
		g.pose().rotate((float) Math.PI * (1f - p.coll));
		g.pose().translate(-3.5f, -3.5f);
		CategoryIcons.blit(g, CategoryIcons.CHEV_DOWN, 0, 0, 7, RenderUtil.alpha(0xFF8E939B, a));
		g.pose().popMatrix();
		String cnt = on + "/" + total;
		int cw = Math.round(FontUtil.width(cnt) * SMALL);
		small(g, cnt, x + pw - 16 - cw, y + HH / 2f, RenderUtil.alpha(anyOn ? 0xFFB5B9C0 : 0xFF6B7078, a));

		int vis = h - HH;
		if (vis <= 1) return;
		int bx = x + PAD, by0 = y + HH + PAD, iw = innerW();
		g.enableScissor(x + 1, y + HH + 1, x + pw - 1, y + h - 1);
		int base = by0 - Math.round(p.scroll);
		int clipTop = y + HH, clipBot = y + h;
		boolean inPanel = RenderUtil.inside(mx, my, x, y + HH, pw, h - HH) && dragC == null;
		for (Slot s : sl) {
			Module m = s.m;
			int sy0 = base + s.y;
			if (sy0 + s.h < clipTop || sy0 > clipBot) continue;
			if (s.block) {
				RenderUtil.roundRect(g, bx + 1, sy0 - 1, iw - 2, s.h + 2, 4, RenderUtil.alpha(rgb(accA(), 0x22), a));
				RenderUtil.roundRect(g, bx + 2, sy0, iw - 4, s.h, 3, RenderUtil.alpha(0x70000000, a));
				g.enableScissor(bx, Math.max(sy0, clipTop), bx + iw, Math.min(sy0 + s.h, clipBot));
				int ry = sy0;
				for (Setting st : m.settings()) {
					int sh = SettingRenderer.height(st, settingsW());
					SettingRenderer.draw(g, st, bx + 4, ry, settingsW() - 4, sh, a, 0f);
					ry += sh;
				}
				g.disableScissor();
				continue;
			}
			boolean hover = inPanel && RenderUtil.inside(mx, my, bx, sy0, iw, CARD) && my >= clipTop && my < clipBot;
			m.toggleAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.toggleAnim, m.isEnabled() ? 1f : 0f);
			m.hoverAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.hoverAnim, hover ? 1f : 0f);
			float t = m.toggleAnim;
			int border = RenderUtil.mix(RenderUtil.mix(0x0EFFFFFF, 0x2CFFFFFF, m.hoverAnim), rgb(accA(), 0x99), t);
			int fill = RenderUtil.mix(RenderUtil.mix(0x28000000, 0x1C000000, m.hoverAnim), rgb(accA(), 0x40), t);
			if (t > 0.02f) RenderUtil.glow(g, bx + iw / 2, sy0 + CARD / 2, iw / 2 + 4, RenderUtil.alpha(accA(), 0.10f * t * a));
			RenderUtil.roundRect(g, bx, sy0, iw, CARD, 5, RenderUtil.alpha(border, a));
			RenderUtil.roundRect(g, bx + 1, sy0 + 1, iw - 2, CARD - 2, 4, RenderUtil.alpha(fill, a));
			if (m.hoverAnim > 0.02f && t < 0.98f) {
				RenderUtil.roundRect(g, bx + 1, sy0 + 1, Math.max(8, Math.round((iw - 2) * m.hoverAnim)), CARD - 2, 4, RenderUtil.alpha(0x12FFFFFF, a * (1f - t)));
			}
			// ponto de estado
			int dcx = bx + 8, dcy = sy0 + CARD / 2;
			if (t > 0.02f) RenderUtil.glow(g, dcx, dcy, 8, RenderUtil.alpha(accA(), 0.55f * t * a));
			RenderUtil.roundRect(g, dcx - 2, dcy - 2, 4, 4, 2, RenderUtil.alpha(RenderUtil.mix(0xFF565B64, accA(), t), a));
			int right = bx + iw - 5;
			boolean has = !m.settings().isEmpty();
			if (has) {
				float pe = EXP.getOrDefault(m, 0f);
				g.pose().pushMatrix();
				g.pose().translate(right - 7 + 3.5f, sy0 + CARD / 2f);
				g.pose().rotate((float) Math.PI * pe);
				g.pose().translate(-3.5f, -3.5f);
				CategoryIcons.blit(g, CategoryIcons.CHEV_DOWN, 0, 0, 7, RenderUtil.alpha(RenderUtil.mix(0xFF7E838B, TXT, pe), a));
				g.pose().popMatrix();
				right -= 10;
			}
			int nameMax = right - (bx + 16) - 4;
			if (m.keybind.key() != 0) {
				String kl = m.keybind.label();
				int kw = Math.round(FontUtil.width(kl) * SMALL) + 6;
				int kx = right - kw;
				RenderUtil.roundRect(g, kx, sy0 + 3, kw, CARD - 6, 3, RenderUtil.alpha(0x30FFFFFF, a));
				RenderUtil.roundRect(g, kx + 1, sy0 + 4, kw - 2, CARD - 8, 2, RenderUtil.alpha(0xFF0F1114, a));
				small(g, kl, kx + 3, sy0 + CARD / 2f, RenderUtil.alpha(0xFFB5B9C0, a));
				nameMax = kx - (bx + 16) - 4;
			}
			int tc = RenderUtil.mix(0xFFA9AEB6, 0xFFFFFFFF, t);
			FontUtil.draw(g, ModuleRow.fit(m.name, Math.max(16, nameMax)), bx + 16, sy0 + (CARD - fh) / 2, RenderUtil.alpha(tc, a));
			if (hover) tipNow = m;
		}
		g.disableScissor();
		int fullC = contentH(sl) + PAD * 2, bodyV = bodyFull(p, sl);
		if (fullC > bodyV && bodyV > 12) {
			int th = Math.max(10, Math.round(bodyV * (bodyV / (float) fullC)));
			float f = p.scroll / Math.max(1, fullC - bodyV);
			RenderUtil.roundRect(g, x + pw - 4, y + HH + 2 + Math.round((bodyV - 4 - th) * f), 2, th, 1, RenderUtil.alpha(rgb(accA(), 0x99), a));
		}
	}

	// ---------- entrada ----------

	private Category panelAt(double mx, double my) {
		for (int i = ORDER.size() - 1; i >= 0; i--) {
			Category c = ORDER.get(i);
			if (!visible(c)) continue;
			P p = PANELS.get(c);
			int h = panelH(p, slots(c));
			if (RenderUtil.inside(mx, my, Math.round(p.x), Math.round(p.y), panelW(), h)) return c;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		ensureLayout();
		double mx = click.x(), my = click.y();
		int button = click.button();
		int fh = FontUtil.height();
		deferred = null;
		searchFocused = RenderUtil.inside(mx, my, searchX(), BAR_Y + 3, searchW(), BAR_H - 6);
		if (searchFocused) return true;
		int bh = BAR_H - 6, by = BAR_Y + 3;
		if (RenderUtil.inside(mx, my, perfX(), by, btnW("Perfis"), bh)) {
			Minecraft.getInstance().gui.setScreen(new ProfilesScreen());
			return true;
		}
		if (RenderUtil.inside(mx, my, musicX(), by, btnW("Music"), bh)) {
			Minecraft.getInstance().gui.setScreen(new MusicScreen());
			return true;
		}
		if (RenderUtil.inside(mx, my, hudX(), by, btnW("HUD"), bh)) {
			Minecraft.getInstance().gui.setScreen(new HudEditScreen());
			return true;
		}
		Category c = panelAt(mx, my);
		if (c == null) return super.mouseClicked(click, doubled);
		ORDER.remove(c);
		ORDER.add(c);
		P p = PANELS.get(c);
		int x = Math.round(p.x), y = Math.round(p.y);
		if (my < y + HH) {
			if (button == 1) {
				p.collapsed = !p.collapsed;
				return true;
			}
			dragC = c;
			dragDX = mx - p.x;
			dragDY = my - p.y;
			pressX = mx;
			pressY = my;
			dragMoved = false;
			return true;
		}
		List<Slot> sl = slots(c);
		int bx = x + PAD, iw = innerW();
		int base = y + HH + PAD - Math.round(p.scroll);
		for (Slot s : sl) {
			Module m = s.m;
			int sy0 = base + s.y;
			if (s.block) {
				int ry = sy0;
				int sxx = bx + 4, sww = settingsW();
				for (Setting st : m.settings()) {
					int sh = SettingRenderer.height(st, sww);
					if (my >= ry && my < ry + sh && mx >= bx && mx < bx + iw) {
						Runnable[] out = new Runnable[1];
						IntSetting slid = SettingRenderer.click(st, sxx, ry, sww - 4, sh, mx, my, fh, out);
						if (slid != null) {
							slider = slid;
							sliderX = sxx + 7;
							sliderW = Math.max(1, sww - 4 - 14);
						}
						if (slid == null && !SettingRenderer.dragging()) deferred = out[0];
						return true;
					}
					ry += sh;
				}
				continue;
			}
			if (my >= sy0 && my < sy0 + CARD && mx >= bx && mx < bx + iw) {
				boolean has = !m.settings().isEmpty();
				boolean onArrow = has && mx >= bx + iw - 16;
				if (has && (button == 1 || onArrow)) {
					if (!OPEN.remove(m)) OPEN.add(m);
				} else if (button == 0) {
					m.toggle();
					Config.save();
				}
				return true;
			}
		}
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
		if (dragC != null) {
			if (!dragMoved && Math.hypot(click.x() - pressX, click.y() - pressY) > 3) dragMoved = true;
			if (dragMoved) {
				P p = PANELS.get(dragC);
				p.x = (float) Mth.clamp(click.x() - dragDX, 0, Math.max(0, this.width - panelW()));
				p.y = (float) Mth.clamp(click.y() - dragDY, 2, Math.max(2, this.height - HH - 2));
				p.cap = -1;
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		boolean was = slider != null || SettingRenderer.dragging() || dragC != null;
		if (dragC != null && !dragMoved) {
			P p = PANELS.get(dragC);
			p.collapsed = !p.collapsed;
		}
		dragC = null;
		if (deferred != null) deferred.run();
		deferred = null;
		SettingRenderer.release();
		if (slider != null) Config.save();
		slider = null;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		Category c = panelAt(mouseX, mouseY);
		if (c != null) {
			P p = PANELS.get(c);
			List<Slot> sl = slots(c);
			p.scroll -= (float) (verticalAmount * 14.0);
			clampScroll(p, sl);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		int cp = input.codepoint();
		if (searchFocused && cp >= 32 && cp != 127 && search.length() < 24) {
			search += new String(Character.toChars(cp));
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int key = input.key();
		for (Module m : OPEN) {
			for (Setting st : m.settings()) {
				if (st instanceof KeybindSetting ks && ks.listening) {
					ks.listening = false;
					if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) ks.setKey(0);
					else if (key != GLFW.GLFW_KEY_ESCAPE) ks.setKey(key);
					Config.save();
					return true;
				}
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
