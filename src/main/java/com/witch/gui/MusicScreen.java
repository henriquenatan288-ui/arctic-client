package com.witch.gui;

import com.witch.music.MusicPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.List;

/** Lista as musicas da pasta "musicas" e controla a reproducao. */
public class MusicScreen extends Screen {
	private static final int ROW = 17;
	private int wx, wy, ww, wh;
	private float scroll = 0f;
	private boolean volDrag = false, barDrag = false;

	public MusicScreen() {
		super(Component.literal("Music"));
		MusicPlayer.refresh();
	}

	private void layout() {
		ww = Math.min(this.width - 12, 340);
		wh = Math.min(this.height - 12, 250);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
	}

	private int listX() { return wx + 10; }
	private int listY() { return wy + 40; }
	private int listW() { return ww - 20; }
	private int listH() { return wh - 40 - 82; }
	private int barY() { return wy + wh - 66; }
	private int btnY() { return wy + wh - 38; }
	private int volX() { return wx + ww - 10 - 70; }

	private float maxScroll() {
		return Math.max(0, MusicPlayer.tracks().size() * ROW - listH());
	}

	private static final int ACC = 0xFF8EC5FF;      // azul bebe do Arctic
	private static final int BG = 0xFF0B1020, BG2 = 0xFF141C34;

	/** Retangulos dos botoes {x, y, w, h}: prev, play/pause, next, stop, shuffle, repeat. Usado no desenho e no clique. */
	private int[][] btnRects() {
		int y = btnY();
		int[] w = {20, 26, 20, 20, 20, 20};
		int[][] r = new int[6][];
		int x = listX();
		for (int i = 0; i < 6; i++) {
			int yy = i == 1 ? y - 3 : y;
			r[i] = new int[] {x, yy, w[i], w[i] == 26 ? 26 : 20};
			x += w[i] + 6;
		}
		return r;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		layout();
		int fh = FontUtil.height();
		g.fill(0, 0, this.width, this.height, 0x99000000);
		RenderUtil.softShadow(g, wx, wy, ww, wh, 6, 0x66000000);
		RenderUtil.roundRect(g, wx, wy, ww, wh, 10, 0xFF2B3A66);
		RenderUtil.roundRect(g, wx + 1, wy + 1, ww - 2, wh - 2, 9, BG);
		// degrade suave de cima para baixo
		for (int yy = wy + 6; yy < wy + wh - 6; yy++) {
			float k = (yy - wy) / (float) wh;
			g.fill(wx + 3, yy, wx + ww - 3, yy + 1, RenderUtil.alpha(BG2, 0.55f * k));
		}

		// cabecalho: estrela + titulo
		StarLogo.glow(g, wx + 20, wy + 18, 34f, 0.55f, 0x5A8CFF);
		StarLogo.draw(g, wx + 20, wy + 18, 22f, 1f, 0xFFFFFF);
		FontUtil.draw(g, "Arctic Player", wx + 38, wy + 8, 0xFFFFFFFF);
		String hint = "pasta: " + MusicPlayer.dir().toAbsolutePath();
		FontUtil.draw(g, ModuleRow.fit(hint, ww - 130), wx + 38, wy + 8 + fh + 2, 0xFF6F7FA6);
		String rf = "Atualizar";
		int rw = FontUtil.width(rf) + 14;
		button(g, wx + ww - 10 - rw, wy + 9, rw, 16, rf, false, mx, my);

		// lista
		List<Path> tr = MusicPlayer.tracks();
		g.enableScissor(listX(), listY(), listX() + listW(), listY() + listH());
		if (tr.isEmpty()) {
			FontUtil.draw(g, "Nenhuma musica. Coloque arquivos na pasta e toque em Atualizar.", listX() + 2, listY() + 4, 0xFF8E9BC0);
		}
		scroll = Mth.clamp(scroll, 0f, maxScroll());
		for (int i = 0; i < tr.size(); i++) {
			int ry = listY() + i * ROW - Math.round(scroll);
			if (ry + ROW < listY() || ry > listY() + listH()) continue;
			boolean cur = i == MusicPlayer.current;
			boolean hov = RenderUtil.inside(mx, my, listX(), ry, listW(), ROW) && my >= listY() && my < listY() + listH();
			if (cur) {
				RenderUtil.roundRect(g, listX(), ry, listW(), ROW - 1, 5, 0xFF1A2850);
				RenderUtil.roundRing(g, listX(), ry, listW(), ROW - 1, 5, 0x668EC5FF);
			} else if (hov) RenderUtil.roundRect(g, listX(), ry, listW(), ROW - 1, 5, 0xFF121A33);
			int ix = listX() + 10, iy = ry + ROW / 2;
			if (cur) Icons.equalizer(g, ix - 5, iy - 5, 11, 10, MusicPlayer.isPlaying(), ACC);
			else if (hov) Icons.play(g, ix, iy, 8, 0xFFB8C6EE);
			else FontUtil.draw(g, String.valueOf(i + 1), ix - FontUtil.width(String.valueOf(i + 1)) / 2, iy - fh / 2, 0xFF55648C);
			String nm = MusicPlayer.nameOf(tr.get(i)).replace('_', ' ');
			FontUtil.draw(g, ModuleRow.fit(nm, listW() - 30), listX() + 24, ry + (ROW - fh) / 2, cur ? 0xFFFFFFFF : 0xFFA9B6DA);
		}
		g.disableScissor();

		// agora tocando
		String now;
		int nc = 0xFFE8EEFF;
		if (!MusicPlayer.error.isEmpty()) { now = MusicPlayer.error; nc = 0xFFE08080; }
		else if (MusicPlayer.loading) now = "Carregando...";
		else if (MusicPlayer.isActive() && MusicPlayer.current >= 0 && MusicPlayer.current < tr.size()) now = MusicPlayer.nameOf(tr.get(MusicPlayer.current)).replace('_', ' ');
		else now = "Nada tocando";
		FontUtil.draw(g, ModuleRow.fit(now, ww - 20), wx + 10, barY() - fh - 5, nc);

		// progresso
		float dur = MusicPlayer.duration(), pos = MusicPlayer.position();
		float frac = dur > 0 ? Mth.clamp(pos / dur, 0f, 1f) : 0f;
		RenderUtil.roundRect(g, listX(), barY(), listW(), 4, 2, 0xFF222E55);
		int fw = Math.max(4, Math.round(listW() * frac));
		if (frac > 0) RenderUtil.roundRect(g, listX(), barY(), fw, 4, 2, ACC);
		RenderUtil.roundRect(g, listX() + fw - 3, barY() - 2, 8, 8, 4, 0xFFFFFFFF);
		FontUtil.draw(g, fmt(pos), listX(), barY() + 8, 0xFF6F7FA6);
		String tm = fmt(dur);
		FontUtil.draw(g, tm, wx + ww - 10 - FontUtil.width(tm), barY() + 8, 0xFF6F7FA6);

		// botoes (icones)
		int[][] br = btnRects();
		boolean pl = MusicPlayer.isPlaying();
		for (int i = 0; i < 6; i++) {
			int[] r = br[i];
			boolean hov = RenderUtil.inside(mx, my, r[0], r[1], r[2], r[3]);
			boolean on = (i == 4 && MusicPlayer.shuffle) || (i == 5 && MusicPlayer.repeat);
			int cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
			if (i == 1) {
				RenderUtil.roundRect(g, r[0], r[1], r[2], r[3], r[2] / 2, hov ? 0xFFFFFFFF : ACC);
				int ic = 0xFF0B1020;
				if (pl) Icons.pause(g, cx, cy, 10, ic); else Icons.play(g, cx + 1, cy, 11, ic);
			} else {
				if (hov || on) RenderUtil.roundRect(g, r[0], r[1], r[2], r[3], 10, on ? 0xFF1A2850 : 0xFF141C34);
				int ic = on ? ACC : (hov ? 0xFFFFFFFF : 0xFFB8C6EE);
				switch (i) {
					case 0 -> Icons.prev(g, cx, cy, 9, ic);
					case 2 -> Icons.next(g, cx, cy, 9, ic);
					case 3 -> Icons.stop(g, cx, cy, 8, ic);
					case 4 -> Icons.shuffle(g, cx, cy, 11, ic);
					default -> Icons.repeat(g, cx, cy, 12, ic);
				}
			}
		}
		int y = btnY();
		Icons.speaker(g, volX() - 14, y + 10, 10, MusicPlayer.volume, 0xFF8E9BC0);
		RenderUtil.roundRect(g, volX(), y + 8, 70, 4, 2, 0xFF222E55);
		RenderUtil.roundRect(g, volX(), y + 8, Math.max(3, Math.round(70 * MusicPlayer.volume)), 4, 2, ACC);
		int kx = volX() + Math.round(70 * MusicPlayer.volume) - 4;
		RenderUtil.roundRect(g, kx, y + 6, 8, 8, 4, 0xFFFFFFFF);
	}

	private static String fmt(float s) {
		int t = Math.max(0, Math.round(s));
		return (t / 60) + ":" + String.format("%02d", t % 60);
	}

	private int btnW(String t) { return Math.max(22, FontUtil.width(t) + 12); }

	private int btn(GuiGraphicsExtractor g, int x, int y, String t, boolean on, int mx, int my) {
		int w = btnW(t);
		button(g, x, y, w, 14, t, on, mx, my);
		return x + w + 4;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String t, boolean on, int mx, int my) {
		boolean hov = RenderUtil.inside(mx, my, x, y, w, h);
		RenderUtil.roundRect(g, x, y, w, h, 5, on ? ACC : (hov ? 0xFF3A4C86 : 0xFF2B3A66));
		RenderUtil.roundRect(g, x + 1, y + 1, w - 2, h - 2, 4, on ? 0xFF1A2850 : (hov ? 0xFF16203F : 0xFF0F1530));
		FontUtil.draw(g, t, x + (w - FontUtil.width(t)) / 2, y + (h - FontUtil.height()) / 2, on ? 0xFFFFFFFF : 0xFFC4C8CE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		layout();
		double mx = click.x(), my = click.y();
		String rf = "Atualizar";
		int rw = FontUtil.width(rf) + 14;
		if (RenderUtil.inside(mx, my, wx + ww - 10 - rw, wy + 9, rw, 16)) { MusicPlayer.refresh(); return true; }

		int[][] br = btnRects();
		int y = btnY();
		for (int i = 0; i < br.length; i++) {
			int[] r = br[i];
			if (RenderUtil.inside(mx, my, r[0], r[1], r[2], r[3])) {
				switch (i) {
					case 0 -> MusicPlayer.prev();
					case 1 -> MusicPlayer.togglePause();
					case 2 -> MusicPlayer.next(false);
					case 3 -> MusicPlayer.stop();
					case 4 -> MusicPlayer.shuffle = !MusicPlayer.shuffle;
					default -> MusicPlayer.repeat = !MusicPlayer.repeat;
				}
				return true;
			}
		}
		if (RenderUtil.inside(mx, my, volX() - 4, y, 78, 14)) {
			volDrag = true;
			MusicPlayer.setVolume((float) ((mx - volX()) / 70.0));
			return true;
		}
		if (RenderUtil.inside(mx, my, listX(), barY() - 3, listW(), 10)) {
			barDrag = true;
			MusicPlayer.seek((float) ((mx - listX()) / listW()));
			return true;
		}
		if (RenderUtil.inside(mx, my, listX(), listY(), listW(), listH())) {
			int i = (int) ((my - listY() + scroll) / ROW);
			if (i >= 0 && i < MusicPlayer.tracks().size()) MusicPlayer.play(i);
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (volDrag) {
			MusicPlayer.setVolume((float) ((click.x() - volX()) / 70.0));
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (barDrag) MusicPlayer.seek((float) ((click.x() - listX()) / listW()));
		volDrag = false;
		barDrag = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Mth.clamp(scroll - (float) verticalAmount * ROW * 2, 0f, maxScroll());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
