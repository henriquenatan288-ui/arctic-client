package com.witch.module;

import com.witch.gui.FontUtil;
import com.witch.gui.HudLayout;
import com.witch.gui.RenderUtil;
import com.witch.music.CoverArt;
import com.witch.music.MusicPlayer;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Widget verde no topo da tela com a musica que esta tocando no Spotify.
 * Sem login nem chave de API: le o titulo da janela do app de computador do Spotify
 * (Windows), o playerctl (Linux) ou o osascript (macOS). Nao mostra capa nem tempo.
 */
public class SpotifyModule extends Module {
	private static SpotifyModule instance;
	private static final int GREEN = 0xFF1DB954;

	private final IntSetting showPaused = add(new IntSetting("Mostrar pausado (0/1)", 0, 1, 1, ""));

	private static volatile String track = null;   // "Artista - Musica" quando tocando
	private static volatile boolean running = false; // Spotify aberto
	private static volatile boolean playing = false;
	private static Thread poller;
	private static volatile boolean stop;

	public SpotifyModule() {
		super("Spotify", Category.HUD);
		instance = this;
	}

	@Override
	public void onEnable() {
		stop = false;
		if (poller != null && poller.isAlive()) return;
		poller = new Thread(SpotifyModule::loop, "witch-spotify");
		poller.setDaemon(true);
		poller.start();
	}

	@Override
	public void onDisable() {
		stop = true;
		track = null;
		running = playing = false;
	}

	private static void loop() {
		while (!stop) {
			try {
				poll();
			} catch (Throwable ignored) {
			}
			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void poll() throws Exception {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			String out = run("tasklist", "/v", "/fi", "imagename eq Spotify.exe", "/fo", "csv", "/nh");
			String best = null;
			boolean any = false;
			for (String line : out.split("\\r?\\n")) {
				if (!line.toLowerCase().startsWith("\"spotify.exe\"")) continue;
				any = true;
				int i = line.lastIndexOf("\",\"");
				if (i < 0) continue;
				String t = line.substring(i + 3).replace("\"", "").trim();
				if (t.isEmpty() || t.equalsIgnoreCase("N/A") || t.equalsIgnoreCase("Spotify")
						|| t.equalsIgnoreCase("Spotify Free") || t.equalsIgnoreCase("Spotify Premium")
						|| t.equalsIgnoreCase("Advertisement") || t.equalsIgnoreCase("Spotify Widget")
						|| t.equalsIgnoreCase("Drag")) continue;
				best = t;
			}
			running = any;
			playing = best != null;
			track = best;
		} else if (os.contains("mac")) {
			String out = run("osascript", "-e",
					"if application \"Spotify\" is running then tell application \"Spotify\" to if player state is playing then return (artist of current track) & \" - \" & (name of current track)");
			running = true;
			track = out.isBlank() ? null : out.trim();
			playing = track != null;
		} else {
			String st = run("playerctl", "-p", "spotify", "status").trim();
			running = !st.isEmpty();
			playing = st.equalsIgnoreCase("Playing");
			track = playing ? run("playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}").trim() : null;
			if (track != null && track.isEmpty()) track = null;
		}
	}

	private static String run(String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null) sb.append(l).append('\n');
		}
		p.waitFor(4, TimeUnit.SECONDS);
		return sb.toString();
	}

	private static String fit(String s, int maxW) {
		if (FontUtil.width(s) <= maxW) return s;
		while (s.length() > 1 && FontUtil.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	private static String mmss(float sec) {
		int s = Math.max(0, Math.round(sec));
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		// musica do player proprio do Witch (pasta "musicas"): com linha do tempo e capa
		var tracks = MusicPlayer.tracks();
		boolean local = MusicPlayer.isActive() && MusicPlayer.current >= 0 && MusicPlayer.current < tracks.size();
		boolean isPlaying;
		String artist, title;
		float pos = 0f, dur = 0f;
		CoverArt.Cover cover = null;
		if (local) {
			var path = tracks.get(MusicPlayer.current);
			String n = MusicPlayer.nameOf(path);
			int i = n.indexOf(" - ");
			artist = i > 0 ? n.substring(0, i) : "Arctic Player";
			title = i > 0 ? n.substring(i + 3) : n;
			isPlaying = MusicPlayer.isPlaying();
			pos = MusicPlayer.position();
			dur = MusicPlayer.duration();
			cover = CoverArt.get(path);
		} else {
			isPlaying = playing && track != null;
			if (!isPlaying && (instance.showPaused.get() == 0 || !running)) return;
			if (isPlaying) {
				String t = track;
				int i = t.indexOf(" - ");
				artist = i > 0 ? t.substring(0, i) : "Spotify";
				title = i > 0 ? t.substring(i + 3) : t;
			} else {
				artist = "Spotify";
				title = "Pausado";
			}
		}
		int lh = FontUtil.height();
		int icon = lh * 2 + 4; // capa / equalizador: altura das duas linhas de texto
		int maxText = 96;
		artist = fit(artist, maxText);
		title = fit(title, maxText);
		int tw = Math.max(FontUtil.width(artist), FontUtil.width(title));
		tw = Math.max(tw, local ? 64 : 40);
		int w = 8 + icon + 7 + tw + 9;
		int barRow = local ? lh + 2 : 0;
		int h = 6 + icon + 5 + barRow;
		int sw = mc.getWindow().getGuiScaledWidth();
		int x = (sw - w) / 2 + HudLayout.dx("spotify");
		int y = 4 + HudLayout.dy("spotify");
		drawLiquidGlass(g, x, y, w, h);

		int ix = x + 8, iy = y + 6;
		if (cover != null) {
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, cover.id(), ix, iy, 0f, 0f, icon, icon,
					cover.w(), cover.h(), cover.w(), cover.h(), 0xFFFFFFFF);
			RenderUtil.roundRing(g, ix - 1, iy - 1, icon + 2, icon + 2, 3, 0x99FFFFFF);
		} else {
			// equalizador animado (parado quando pausado)
			double t = System.nanoTime() / 1.0e9;
			int base = iy + icon - 2;
			int maxH = icon - 4;
			for (int i = 0; i < 4; i++) {
				double k = isPlaying ? 0.5 + 0.5 * Math.sin(t * (5.0 + i * 1.7) + i * 1.3) : 0.25;
				int bh = 3 + (int) (k * (maxH - 3));
				RenderUtil.roundRect(g, ix + i * 5 + 1, base - bh, 3, bh, 1, isPlaying ? 0xFF3DDC84 : 0xAAB5C9BC);
			}
		}
		int tx = ix + icon + 7;
		FontUtil.draw(g, artist, tx, iy, 0xFFBDF5D0);
		FontUtil.draw(g, title, tx, iy + lh + 1, isPlaying ? 0xFFFFFFFF : 0xFFD0DDD5);
		if (local) { // linha do tempo: 0:00 [=====-----] 3:35
			int ry = y + h - lh - 3;
			int tW = FontUtil.width("00:00");
			int bx = x + 8 + tW + 5;
			int bw = w - 16 - 2 * (tW + 5);
			float f = dur > 0 ? Math.min(1f, pos / dur) : 0f;
			FontUtil.draw(g, mmss(pos), x + 8, ry, 0xFFBDF5D0);
			String rt = mmss(dur);
			FontUtil.draw(g, rt, x + w - 8 - FontUtil.width(rt), ry, 0xFFBDF5D0);
			int by = ry + lh / 2 - 1;
			RenderUtil.roundRect(g, bx, by, bw, 3, 1, 0x55FFFFFF);
			int fw = Math.max(3, Math.round(bw * f));
			RenderUtil.roundRect(g, bx, by, fw, 3, 1, 0xFF3DDC84);
			RenderUtil.roundRect(g, bx + fw - 2, by - 1, 5, 5, 2, 0xFFFFFFFF); // bolinha
		}
		HudLayout.report("spotify", x, y, w, h);
	}

	/** Vidro liquido: base escura leve (legibilidade), tinta verde em degrade, brilho de cima, borda de luz e um reflexo que passa. */
	private static void drawLiquidGlass(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int r = Math.min(10, h / 2);
		RenderUtil.softShadow(g, x, y, w, h, 4, 0x55000000);
		RenderUtil.roundRect(g, x, y, w, h, r, 0x8A07140C);                 // base: escurece o fundo
		// degrade vertical verde: mais claro em cima, mais saturado embaixo
		for (int yy = y + 1; yy < y + h - 1; yy++) {
			float k = (yy - y) / (float) h;
			int c = RenderUtil.mix(0x3A9BFFC4, 0x4A1DB954, k);
			int dy = Math.min(yy - y, y + h - 1 - yy);
			int inset = 0;
			if (dy < r) {
				double d = r - dy - 0.5;
				inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * (double) r - d * d)));
			}
			g.fill(x + 1 + inset, yy, x + w - 1 - inset, yy + 1, c);
		}
		RenderUtil.roundRect(g, x + 2, y + 2, w - 4, Math.max(6,h*2/5),r-2,0x1AFFFFFF); // brilho (gloss) de cima
		RenderUtil.roundRing(g, x, y, w, h, r, 0xAAFFFFFF);                // borda de luz
		RenderUtil.roundRing(g, x + 1, y + 1, w - 2, h - 2, r - 1, 0x2E3DDC84); // borda interna verde
		g.fill(x + r, y + 1, x + w - r, y + 2, 0x55FFFFFF);                // fio de luz no topo
		// reflexo diagonal que atravessa devagar
		float cyc = (System.nanoTime() / 1_000_000L % 6500L) / 6500f;
		int cx = Math.round(x + (cyc * 1.5f - 0.25f) * w);
		for (int d = -10; d <= 10; d++) {
			int xx = cx + d;
			if (xx <= x + r || xx >= x + w - r) continue;
			float a = (1f - Math.abs(d) / 10f) * 0.10f;
			g.fill(xx, y + 3, xx + 1, y + h - 3, ((int) (a * 255) << 24) | 0xFFFFFF);
		}
	}
}
