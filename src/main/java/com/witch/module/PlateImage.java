package com.witch.module;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Carrega a primeira imagem (png/jpg/jpeg) de uma pasta e reduz para n x n pixels ARGB (media por caixa, alfa pre-multiplicado). */
final class PlateImage {
	private PlateImage() {}

	/** Arquivo escolhido: o primeiro por nome. null se a pasta nao existe ou esta vazia. */
	static Path find(Path dir) {
		try {
			if (!Files.isDirectory(dir)) return null;
			List<Path> l = new ArrayList<>();
			try (var st = Files.list(dir)) {
				st.filter(p -> {
					String n = p.getFileName().toString().toLowerCase();
					return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
				}).forEach(l::add);
			}
			l.sort(null);
			return l.isEmpty() ? null : l.get(0);
		} catch (Throwable t) {
			return null;
		}
	}

	/** n*n ARGB ou null se nao deu para ler (jpg depende de javax.imageio no aparelho). */
	static int[] load(Path file, int n) {
		try {
			String name = file.getFileName().toString().toLowerCase();
			int w, h;
			int[] px;
			if (name.endsWith(".png")) {
				try (InputStream in = Files.newInputStream(file); NativeImage ni = NativeImage.read(in)) {
					w = ni.getWidth();
					h = ni.getHeight();
					px = new int[w * h];
					for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = ni.getPixel(x, y);
				}
			} else {
				int[] wh = new int[2];
				px = Jpg.read(file, wh);
				if (px == null) return null;
				w = wh[0];
				h = wh[1];
			}
			return reduce(px, w, h, n);
		} catch (Throwable t) {
			return null;
		}
	}

	/** Corta o centro quadrado e reduz para n x n. */
	private static int[] reduce(int[] px, int w, int h, int n) {
		int s = Math.min(w, h);
		int ox = (w - s) / 2, oy = (h - s) / 2;
		int[] out = new int[n * n];
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int x0 = ox + i * s / n, x1 = Math.max(x0 + 1, ox + (i + 1) * s / n);
				int y0 = oy + j * s / n, y1 = Math.max(y0 + 1, oy + (j + 1) * s / n);
				long a = 0, r = 0, g = 0, b = 0;
				int cnt = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int c = px[y * w + x];
						int ca = c >>> 24;
						a += ca;
						r += (long) ((c >> 16) & 255) * ca;
						g += (long) ((c >> 8) & 255) * ca;
						b += (long) (c & 255) * ca;
						cnt++;
					}
				}
				if (a == 0) { out[j * n + i] = 0; continue; }
				int aa = (int) (a / cnt);
				out[j * n + i] = (aa << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
			}
		}
		return out;
	}

	/** Isolado: se o aparelho nao tem javax.imageio, so esta classe falha (e a PNG continua funcionando). */
	private static final class Jpg {
		static int[] read(Path file, int[] wh) {
			try (InputStream in = Files.newInputStream(file)) {
				var img = javax.imageio.ImageIO.read(in);
				if (img == null) return null;
				wh[0] = img.getWidth();
				wh[1] = img.getHeight();
				int[] px = new int[wh[0] * wh[1]];
				img.getRGB(0, 0, wh[0], wh[1], px, 0, wh[0]);
				return px;
			} catch (Throwable t) {
				return null;
			}
		}
	}
}
