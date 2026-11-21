package com.witch.music;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Capa da musica: imagem embutida no MP3 (ID3v2, quadro APIC/PIC) ou arquivo ao lado
 * (mesmo nome .png/.jpg, ou cover/folder/capa na pasta). Carrega em segundo plano.
 */
public final class CoverArt {
	public record Cover(Identifier id, int w, int h) {}

	private static final Map<Path, Object> CACHE = new LinkedHashMap<>(); // Cover, ou NONE se nao tem / carregando
	private static final Object NONE = new Object();
	private static int counter = 0;

	private CoverArt() {}

	/** Capa pronta da faixa, ou null (sem capa ou ainda carregando). */
	public static Cover get(Path track) {
		if (track == null) return null;
		synchronized (CACHE) {
			Object o = CACHE.get(track);
			if (o instanceof Cover c) return c;
			if (o != null) return null;
			CACHE.put(track, NONE);
		}
		Thread t = new Thread(() -> load(track), "witch-cover");
		t.setDaemon(true);
		t.start();
		return null;
	}

	private static void load(Path track) {
		try {
			byte[] img = find(track);
			if (img == null) return;
			NativeImage ni = NativeImage.read(img);
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				try {
					Identifier id = Identifier.fromNamespaceAndPath("witch", "cover/" + (counter++));
					mc.getTextureManager().register(id, new DynamicTexture(() -> "witch cover", ni));
					synchronized (CACHE) {
						CACHE.put(track, new Cover(id, ni.getWidth(), ni.getHeight()));
						trim(mc);
					}
				} catch (Throwable ignored) {
				}
			});
		} catch (Throwable ignored) {
		}
	}

	/** Guarda so as 4 capas mais recentes na memoria. */
	private static void trim(Minecraft mc) {
		int covers = 0;
		for (Object o : CACHE.values()) if (o instanceof Cover) covers++;
		var it = CACHE.entrySet().iterator();
		while (covers > 4 && it.hasNext()) {
			var e = it.next();
			if (e.getValue() instanceof Cover c) {
				mc.getTextureManager().release(c.id());
				it.remove();
				covers--;
			}
		}
	}

	private static byte[] find(Path track) throws Exception {
		String base = MusicPlayer.nameOf(track);
		Path dir = track.getParent();
		for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
			Path p = dir.resolve(base + ext);
			if (Files.isRegularFile(p)) return Files.readAllBytes(p);
		}
		if (track.getFileName().toString().toLowerCase().endsWith(".mp3")) {
			byte[] e = id3Cover(track);
			if (e != null) return e;
		}
		for (String n : new String[] {"cover", "folder", "capa"}) {
			for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
				Path p = dir.resolve(n + ext);
				if (Files.isRegularFile(p)) return Files.readAllBytes(p);
			}
		}
		return null;
	}

	private static int synchsafe(byte[] b, int o) {
		return ((b[o] & 0x7F) << 21) | ((b[o + 1] & 0x7F) << 14) | ((b[o + 2] & 0x7F) << 7) | (b[o + 3] & 0x7F);
	}

	/** Le a imagem embutida (ID3v2.2/2.3/2.4). Le so o cabecalho da tag, nao o arquivo todo. */
	private static byte[] id3Cover(Path p) throws Exception {
		try (var ch = Files.newInputStream(p)) {
			byte[] h = ch.readNBytes(10);
			if (h.length < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') return null;
			int ver = h[3] & 0xFF;
			int size = synchsafe(h, 6);
			if (size <= 0 || size > 16 * 1024 * 1024) return null;
			byte[] t = ch.readNBytes(size);
			int pos = 0;
			if ((h[5] & 0x40) != 0 && ver >= 3 && t.length >= 4) { // cabecalho estendido
				pos = ver == 4 ? synchsafe(t, 0) : 4 + (((t[0] & 0xFF) << 24) | ((t[1] & 0xFF) << 16) | ((t[2] & 0xFF) << 8) | (t[3] & 0xFF));
			}
			int idLen = ver == 2 ? 3 : 4, hdr = ver == 2 ? 6 : 10;
			while (pos + hdr <= t.length) {
				if (t[pos] == 0) break;
				String id = new String(t, pos, idLen, java.nio.charset.StandardCharsets.ISO_8859_1);
				int fs;
				if (ver == 2) fs = ((t[pos + 3] & 0xFF) << 16) | ((t[pos + 4] & 0xFF) << 8) | (t[pos + 5] & 0xFF);
				else if (ver == 4) fs = synchsafe(t, pos + 4);
				else fs = ((t[pos + 4] & 0xFF) << 24) | ((t[pos + 5] & 0xFF) << 16) | ((t[pos + 6] & 0xFF) << 8) | (t[pos + 7] & 0xFF);
				int start = pos + hdr;
				if (fs <= 0 || start + fs > t.length) break;
				if (id.equals("APIC") || id.equals("PIC")) {
					int enc = t[start] & 0xFF;
					int q = start + 1;
					if (ver == 2) q += 3; // formato de 3 letras
					else { while (q < start + fs && t[q] != 0) q++; q++; } // mime
					q++; // tipo da imagem
					boolean wide = enc == 1 || enc == 2;
					if (wide) { while (q + 1 < start + fs && !(t[q] == 0 && t[q + 1] == 0)) q += 2; q += 2; }
					else { while (q < start + fs && t[q] != 0) q++; q++; }
					if (q < start + fs) return java.util.Arrays.copyOfRange(t, q, start + fs);
				}
				pos = start + fs;
			}
		}
		return null;
	}
}
