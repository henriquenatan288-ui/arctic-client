package com.witch.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Cantos de anel gerados por codigo no tamanho EXATO da tela (1 pixel de GUI = guiScale pixels reais),
 * com a espessura da linha sempre igual a 1 pixel de GUI e antialiasing por cobertura de area.
 * Substitui a textura de 40px espremida para 3-10px, que deixava a linha com espessura irregular e tremendo.
 * Pura Java (PNG montado a mao): nao depende de java.desktop nem de shader customizado.
 */
public final class Corners {
	private Corners() {}

	private static final Map<Long, Identifier> RINGS = new HashMap<>();
	private static int counter;
	private static final int MAX = 48;

	/** Textura (2R x 2R, R = r*scale) com um circulo de linha de 1 px de GUI; null se nao deu para criar. */
	public static Identifier ring(int r, int scale) {
		long key = ((long) r << 16) | scale;
		Identifier id = RINGS.get(key);
		if (id != null) return id;
		if (RINGS.size() >= MAX || r < 1 || scale < 1 || r * scale > 512) return null;
		try {
			byte[] png = png(buildRing(r, scale));
			NativeImage ni = NativeImage.read(png);
			id = Identifier.fromNamespaceAndPath("witch", "corner/ring_" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "witch corner", ni));
			RINGS.put(key, id);
			return id;
		} catch (Throwable t) {
			RINGS.put(key, null);
			return null;
		}
	}

	/** Largura/altura da textura em texels para (r, scale). */
	public static int size(int r, int scale) {
		return 2 * r * scale;
	}

	/** alpha (0..255) por texel: anel com raio externo R e espessura "scale" texels, cobertura por supersampling 8x8. */
	private static int[][] buildRing(int r, int scale) {
		int R = r * scale, S = 2 * R, SS = 8;
		double outer = R, inner = Math.max(0, R - scale);
		int[][] a = new int[S][S];
		for (int y = 0; y < S; y++) {
			for (int x = 0; x < S; x++) {
				int hit = 0;
				for (int sy = 0; sy < SS; sy++) {
					for (int sx = 0; sx < SS; sx++) {
						double px = x + (sx + 0.5) / SS - R, py = y + (sy + 0.5) / SS - R;
						double d = Math.sqrt(px * px + py * py);
						if (d <= outer && d >= inner) hit++;
					}
				}
				a[y][x] = Math.round(255f * hit / (SS * SS));
			}
		}
		return a;
	}

	/** PNG RGBA 8 bits, branco com o alpha dado (compressao zlib do JDK). */
	private static byte[] png(int[][] alpha) throws Exception {
		int h = alpha.length, w = alpha[0].length;
		byte[] raw = new byte[h * (1 + w * 4)];
		int p = 0;
		for (int y = 0; y < h; y++) {
			raw[p++] = 0;
			for (int x = 0; x < w; x++) {
				raw[p++] = (byte) 255; raw[p++] = (byte) 255; raw[p++] = (byte) 255; raw[p++] = (byte) alpha[y][x];
			}
		}
		Deflater df = new Deflater(9);
		df.setInput(raw);
		df.finish();
		ByteArrayOutputStream z = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		while (!df.finished()) z.write(buf, 0, df.deflate(buf));
		df.end();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		DataOutputStream d = new DataOutputStream(ihdr);
		d.writeInt(w); d.writeInt(h); d.writeByte(8); d.writeByte(6); d.writeByte(0); d.writeByte(0); d.writeByte(0);
		chunk(out, "IHDR", ihdr.toByteArray());
		chunk(out, "IDAT", z.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
		DataOutputStream d = new DataOutputStream(out);
		d.writeInt(data.length);
		byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		d.write(t);
		d.write(data);
		CRC32 c = new CRC32();
		c.update(t);
		c.update(data);
		d.writeInt((int) c.getValue());
	}
}
