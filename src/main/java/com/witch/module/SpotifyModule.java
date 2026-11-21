package com.witch.module;

import com.witch.gui.FontUtil;
import com.witch.gui.HudLayout;
import com.witch.gui.RenderUtil;
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

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		boolean isPlaying = playing && track != null;
		if (!isPlaying && (instance.showPaused.get() == 0 || !running)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		String artist, title;
		if (isPlaying) {
			String t = track;
			int i = t.indexOf(" - ");
			artist = i > 0 ? t.substring(0, i) : "Spotify";
			title = i > 0 ? t.substring(i + 3) : t;
		} else {
			artist = "Spotify";
			title = "Pausado";
		}
		int maxText = 150;
		artist = fit(artist, maxText);
		title = fit(title, maxText);
		int lh = FontUtil.height();
		int tw = Math.max(FontUtil.width(artist), FontUtil.width(title));
		int w = 30 + tw + 12;
		int h = lh * 2 + 10;
		int sw = mc.getWindow().getGuiScaledWidth();
		int x = (sw - w) / 2 + HudLayout.dx("spotify");
		int y = 4 + HudLayout.dy("spotify");

		RenderUtil.softShadow(g, x, y, w, h, 3, 0x66000000);
		RenderUtil.roundRect(g, x, y, w, h, 5, 0xF00E3B1E);
		RenderUtil.roundRing(g, x, y, w, h, 5, 0xCC1DB954);

		// equalizador animado (parado quando pausado)
		double t = System.nanoTime() / 1.0e9;
		int bx = x + 9;
		int base = y + h - 6;
		for (int i = 0; i < 4; i++) {
			double k = isPlaying ? 0.5 + 0.5 * Math.sin(t * (5.0 + i * 1.7) + i * 1.3) : 0.25;
			int bh = 3 + (int) (k * (h - 14));
			RenderUtil.roundRect(g, bx + i * 4, base - bh, 3, bh, 1, isPlaying ? GREEN : 0xFF6A8F78);
		}
		int tx = x + 30;
		FontUtil.draw(g, artist, tx, y + 4, 0xFF9FE3B5);
		FontUtil.draw(g, title, tx, y + 5 + lh, isPlaying ? 0xFFFFFFFF : 0xFFB5C9BC);
		HudLayout.report("spotify", x, y, w, h);
	}
}
