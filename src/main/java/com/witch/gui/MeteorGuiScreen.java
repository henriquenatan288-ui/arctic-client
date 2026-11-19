package com.witch.gui;

import com.witch.config.Config;
import com.witch.module.Category;
import com.witch.module.Module;
import com.witch.module.ModuleManager;
import com.witch.setting.IntSetting;
import com.witch.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClickGUI em janelas: uma janela arrastavel por categoria (como nos clients de PC). Toque/clique no modulo liga e desliga,
 * botao direito (ou a seta) abre os ajustes dentro da propria janela. Arrastar o titulo move a janela; arrastar a lista
 * (ou a roda do mouse) rola. Posicoes e janelas abertas ficam salvas enquanto o jogo estiver aberto.
 */
public class MeteorGuiScreen extends Screen {
	private static final float UI = 0.8f;
	private static final int W = 118, HEAD = 18, ROWH = 16, GAP = 8, PADX = 10, PADY = 12;
	private static final int RED = GuiBackdrop.RED;

	private static final class Win {
		float x, y, scroll;
		boolean open = true, placed;
	}

	private static final Category[] CATS = Category.values();
	private static final Win[] WINS = new Win[CATS.length];
	private static final List<Integer> ORDER = new ArrayList<>(); // do fundo para o topo
	private static final Set<Module> EXPANDED = new HashSet<>();
	private static final Map<Module, Float> EXP = new HashMap<>();

	static {
		for (int i = 0; i < CATS.length; i++) {
			WINS[i] = new Win();
			ORDER.add(i);
		}
	}

	private int vw() { return Math.round(this.width / UI); }
	private int vh() { return Math.round(this.height / UI); }

	private float openAnim = 0f;
	private long lastNanos = 0L;
	private Module tipNow;
	private Setting tipSet;
	private Object tipLast;
	private long tipSince;

	// interacao: o clique so vale ao soltar se nao arrastou (assim da para rolar/mover sem ligar nada sem querer)
	private Runnable deferred;
	private boolean moved;
	private int dragWin = -1;      // janela cuja lista esta rolando
	private int moveWin = -1;      // janela sendo movida pelo titulo
	private double pressX, pressY, offX, offY;
	private float scrollStart;
	private IntSetting dragInt;
	private int dragIx, dragIw;

	public MeteorGuiScreen() {
		this(null);
	}

	public MeteorGuiScreen(Module openSettings) {
		super(Component.literal("Witch Client"));
		if (openSettings != null) {
			EXPANDED.add(openSettings);
			EXP.put(openSettings, 1f);
			int ci = openSettings.category.ordinal();
			WINS[ci].open = true;
			ORDER.remove(Integer.valueOf(ci));
			ORDER.add(ci);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- layout ----------

	private int cols() { return Math.max(1, (vw() - 2 * PADX + GAP) / (W + GAP)); }
	private int rows() { return (CATS.length + cols() - 1) / cols(); }

	/** Altura maxima do corpo de uma janela que ainda nao foi mexida. */
	private int bodyCap() {
		int r = rows();
		return Math.max(70, (vh() - 2 * PADY - (r - 1) * GAP) / r - HEAD);
	}

	private void place() {
		int cols = cols();
		int stride = HEAD + bodyCap() + GAP;
		for (int i = 0; i < CATS.length; i++) {
			Win w = WINS[i];
			if (w.placed) continue;
			w.x = PADX + (i % cols) * (W + GAP);
			w.y = PADY + (i / cols) * stride;
			w.placed = true;
		}
	}

	private static float expAnim(Module m) {
		return EXP.getOrDefault(m, 0f);
	}

	private static int settingsH(Module m) {
		int h = 4;
		for (Setting s : m.settings()) h += SettingRenderer.height(s, W);
		return h;
	}

	private static int blockH(Module m) {
		return ROWH + Math.round(settingsH(m) * expAnim(m));
	}

	private int contentH(int ci) {
		int h = 0;
		for (Module m : ModuleManager.byCategory(CATS[ci])) h += blockH(m);
		return h;
	}

	private int bodyH(int ci) {
		Win w = WINS[ci];
		if (!w.open) return 0;
		int room = vh() - Math.round(w.y) - HEAD - 8;
		int cap = Math.max(60, rows() > 1 ? Math.min(bodyCap(), room) : room);
		return Math.min(contentH(ci), cap);
	}

	private void clampScroll(int ci) {
		Win w = WINS[ci];
		w.scroll = Mth.clamp(w.scroll, 0, Math.max(0, contentH(ci) - bodyH(ci)));
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
			// sem desfoque
		}
		g.fill(0, 0, this.width, this.height, RenderUtil.alpha(0xFF000000, 0.38f * a));
		g.pose().pushMatrix();
		g.pose().scale(UI, UI);
		mx = Math.round(mx / UI);
		my = Math.round(my / UI);
		place();
		tipNow = null;
		tipSet = null;
		int fh = FontUtil.height();

		for (Map.Entry<Module, Float> e : EXP.entrySet()) {
			float t = EXPANDED.contains(e.getKey()) ? 1f : 0f;
			e.setValue(Mth.lerp(Math.min(1f, speed * 0.35f), e.getValue(), t));
		}
		for (Module m : EXPANDED) EXP.putIfAbsent(m, 0f);

		for (int ci : ORDER) {
			Win w = WINS[ci];
			clampScroll(ci);
			int x = Math.round(w.x);
			int y = Math.round(w.y + (1f - a) * 8f);
			int bh = bodyH(ci);
			RenderUtil.roundRect(g, x - 1, y - 1, W + 2, HEAD + bh + 2, 6, RenderUtil.alpha(0xFFFFFFFF, 0.08f * a));
			RenderUtil.roundRect(g, x, y, W, HEAD + bh, 5, RenderUtil.alpha(0xFF101013, 0.95f * a));
			RenderUtil.roundRect(g, x, y, W, HEAD, 5, RenderUtil.alpha(0xFF18181D, a));
			if (bh > 0) g.fill(x, y + HEAD / 2, x + W, y + HEAD, RenderUtil.alpha(0xFF18181D, a));
			g.fill(x + 6, y + HEAD - 1, x + W - 6, y + HEAD, RenderUtil.alpha(RED, 0.9f * a));
			FontUtil.draw(g, CATS[ci].label, x + 8, y + (HEAD - fh) / 2, RenderUtil.alpha(0xFFFFFFFF, a));
			String tog = w.open ? "-" : "+";
			FontUtil.draw(g, tog, x + W - 8 - FontUtil.width(tog) / 2, y + (HEAD - fh) / 2, RenderUtil.alpha(0xFF8E939B, a));
			if (bh <= 0) continue;

			g.enableScissor(x, y + HEAD, x + W, y + HEAD + bh);
			int ry = y + HEAD - Math.round(w.scroll);
			for (Module m : ModuleManager.byCategory(CATS[ci])) {
				int blk = blockH(m);
				if (ry + blk >= y + HEAD && ry <= y + HEAD + bh) {
					row(g, m, x, ry, mx, my, a, speed, y + HEAD, bh, ci);
				}
				ry += blk;
			}
			g.disableScissor();
			int total = contentH(ci);
			if (total > bh) {
				int th = Math.max(12, Math.round(bh * (bh / (float) total)));
				float f = w.scroll / Math.max(1, total - bh);
				RenderUtil.roundRect(g, x + W - 3, y + HEAD + Math.round((bh - th) * f), 2, th, 1, RenderUtil.alpha(RED, 0.8f * a));
			}
		}

		String hint = "Toque: liga/desliga   Direito ou seta: ajustes   Arraste o titulo: mover   RSHIFT fecha";
		FontUtil.draw(g, hint, 10, vh() - 10, RenderUtil.alpha(0xFF6F686B, a));

		Object tipKey = tipSet != null ? tipSet : tipNow;
		if (tipKey != tipLast) { tipLast = tipKey; tipSince = System.currentTimeMillis(); }
		if (tipKey != null && com.witch.module.GuiSettingsModule.tooltips() && System.currentTimeMillis() - tipSince > 450) {
			String d = tipSet != null ? tipSet.name : ModuleDescriptions.of(tipNow.name);
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

	private void row(GuiGraphicsExtractor g, Module m, int x, int ry, int mx, int my, float a, float speed, int clipY, int clipH, int ci) {
		int fh = FontUtil.height();
		boolean hover = RenderUtil.inside(mx, my, x, ry, W, ROWH) && my >= clipY && my < clipY + clipH && dragWin < 0 && moveWin < 0;
		m.toggleAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.toggleAnim, m.isEnabled() ? 1f : 0f);
		m.hoverAnim = Mth.lerp(Math.min(1f, speed * 0.3f), m.hoverAnim, hover ? 1f : 0f);
		float t = m.toggleAnim;
		if (t > 0.01f) {
			g.fill(x + 1, ry, x + W - 1, ry + ROWH, RenderUtil.alpha(RED, 0.30f * t * a));
			g.fill(x + 1, ry + 2, x + 3, ry + ROWH - 2, RenderUtil.alpha(RED, t * a));
		}
		if (m.hoverAnim > 0.01f) g.fill(x + 1, ry, x + W - 1, ry + ROWH, RenderUtil.alpha(0xFFFFFFFF, 0.07f * m.hoverAnim * a));
		int col = RenderUtil.mix(0xFF8E939B, 0xFFFFFFFF, Math.max(t, m.hoverAnim * 0.6f));
		FontUtil.draw(g, ModuleRow.fit(m.name, W - 26), x + 7, ry + (ROWH - fh) / 2, RenderUtil.alpha(col, a));
		boolean open = EXPANDED.contains(m);
		String arrow = open ? "v" : ">";
		FontUtil.draw(g, arrow, x + W - 10 - FontUtil.width(arrow) / 2, ry + (ROWH - fh) / 2, RenderUtil.alpha(0xFF6B7078, a));
		if (hover) tipNow = m;

		float e = expAnim(m);
		if (e > 0.01f) {
			int hh = Math.round(settingsH(m) * e);
			int sy0 = ry + ROWH;
			g.enableScissor(x, Math.max(sy0, clipY), x + W, Math.min(sy0 + hh, clipY + clipH));
			g.fill(x, sy0, x + W, sy0 + hh, RenderUtil.alpha(0xFF08080A, 0.9f * a));
			int sy = sy0 + 2;
			for (Setting s : m.settings()) {
				int sh = SettingRenderer.height(s, W);
				SettingRenderer.shortLabels = true;
				try {
					SettingRenderer.draw(g, s, x, sy, W, sh, a, 0f);
				} finally {
					SettingRenderer.shortLabels = false;
				}
				if (RenderUtil.inside(mx, my, x, sy, W, sh) && my >= clipY && my < clipY + clipH && dragWin < 0 && moveWin < 0) tipSet = s;
				sy += sh;
			}
			g.disableScissor();
		}
	}

	// ---------- entrada ----------

	/** Indice da janela mais ao topo sob o ponto, ou -1. */
	private int winAt(double mx, double my) {
		for (int k = ORDER.size() - 1; k >= 0; k--) {
			int ci = ORDER.get(k);
			Win w = WINS[ci];
			if (RenderUtil.inside(mx, my, Math.round(w.x), Math.round(w.y), W, HEAD + bodyH(ci))) return ci;
		}
		return -1;
	}

	private void raise(int ci) {
		ORDER.remove(Integer.valueOf(ci));
		ORDER.add(ci);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x() / UI, my = click.y() / UI;
		int button = click.button();
		deferred = null;
		moved = false;
		dragWin = moveWin = -1;
		dragInt = null;
		int ci = winAt(mx, my);
		if (ci < 0) return super.mouseClicked(click, doubled);
		raise(ci);
		Win w = WINS[ci];
		int x = Math.round(w.x), y = Math.round(w.y);
		pressX = mx;
		pressY = my;
		if (my < y + HEAD) {
			moveWin = ci;
			offX = mx - w.x;
			offY = my - w.y;
			deferred = () -> w.open = !w.open;
			return true;
		}
		int bh = bodyH(ci);
		int ry = y + HEAD - Math.round(w.scroll);
		for (Module m : ModuleManager.byCategory(CATS[ci])) {
			int blk = blockH(m);
			if (my >= ry && my < ry + blk && my < y + HEAD + bh) {
				if (my < ry + ROWH) {
					boolean arrow = mx >= x + W - 20;
					if (button == 1 || arrow) deferred = () -> toggleExpand(m);
					else if (button == 0) deferred = () -> { m.toggle(); Config.save(); };
				} else {
					int sy = ry + ROWH + 2;
					for (Setting s : m.settings()) {
						int sh = SettingRenderer.height(s, W);
						if (my >= sy && my < sy + sh) {
							Runnable[] pend = new Runnable[1];
							IntSetting di = SettingRenderer.click(s, x, sy, W, sh, mx, my, FontUtil.height(), pend);
							if (di != null) {
								dragInt = di;
								dragIx = x + 7;
								dragIw = W - 14;
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
			dragWin = ci;
			scrollStart = w.scroll;
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
		if (moveWin >= 0) {
			if (moved || Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
				moved = true;
				deferred = null;
				Win w = WINS[moveWin];
				w.x = (float) Mth.clamp(mx - offX, 0, Math.max(0, vw() - W));
				w.y = (float) Mth.clamp(my - offY, 0, Math.max(0, vh() - HEAD - 4));
				clampScroll(moveWin);
			}
			return true;
		}
		if (dragWin >= 0) {
			double dy = my - pressY;
			if (moved || Math.abs(dy) > 3) {
				moved = true;
				deferred = null;
				WINS[dragWin].scroll = scrollStart - (float) dy;
				clampScroll(dragWin);
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		boolean was = dragInt != null || moveWin >= 0 || dragWin >= 0 || SettingRenderer.dragging();
		if (dragInt != null) Config.save();
		SettingRenderer.release();
		if (deferred != null && !moved) deferred.run();
		deferred = null;
		dragInt = null;
		moveWin = dragWin = -1;
		return was || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int ci = winAt(mouseX / UI, mouseY / UI);
		if (ci < 0) return false;
		WINS[ci].scroll -= (float) (verticalAmount * 18.0);
		clampScroll(ci);
		return true;
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
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(input);
	}
}
