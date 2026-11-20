package com.witch.render;

import com.witch.render.EspRenderer.Esp;

/** Formas planas com degrade para efeitos no chao (aneis com brilho, discos, tracos radiais). */
public final class FxShapes {
	private FxShapes() {}

	public static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1f - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1f - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1f - t) + (b & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	public static int segs(double r) {
		return (int) Math.max(24, Math.min(64, 20 + r * 6));
	}

	/** Faixa circular de r0 a r1 (alpha a0 na borda de dentro, a1 na de fora). */
	public static void band(Esp esp, double cx, double y, double cz, double r0, double r1, int argb, float a0, float a1) {
		if (r1 <= r0 || (a0 <= 0.004f && a1 <= 0.004f)) return;
		int n = segs(r1);
		for (int i = 0; i < n; i++) {
			double t0 = i * Math.PI * 2 / n, t1 = (i + 1) * Math.PI * 2 / n;
			double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
			esp.gradQuad(cx + c0 * r0, y, cz + s0 * r0, cx + c1 * r0, y, cz + s1 * r0,
					cx + c1 * r1, y, cz + s1 * r1, cx + c0 * r1, y, cz + s0 * r1,
					argb, a0, a0, a1, a1);
		}
	}

	/** Anel com brilho: nucleo fino e claro, halo suave para dentro e para fora. */
	public static void glowRing(Esp esp, double cx, double y, double cz, double r, double halo, int argb, float a) {
		int core = mix(argb, 0xFFFFFFFF, 0.6f);
		band(esp, cx, y, cz, Math.max(0.02, r - halo), r, argb, 0f, 0.55f * a);   // halo de dentro
		band(esp, cx, y + 0.004, cz, r, r + halo * 0.45, argb, 0.55f * a, 0f);     // halo de fora (mais curto)
		band(esp, cx, y + 0.008, cz, r - 0.03, r + 0.03, core, 0.95f * a, 0.95f * a); // nucleo
	}

	/** Disco com degrade: transparente no centro, mais forte na borda. */
	public static void disk(Esp esp, double cx, double y, double cz, double r, int argb, float aCenter, float aEdge) {
		band(esp, cx, y, cz, 0.0, r, argb, aCenter, aEdge);
	}

	/** Tracos em arco (tipo runa) girando: n tracos de largura w, cada um ocupando 'fill' do seu setor. */
	public static void dashes(Esp esp, double cx, double y, double cz, double r, double w, int n, double rot, double fill, int argb, float a) {
		int sub = 4;
		for (int d = 0; d < n; d++) {
			double start = rot + d * Math.PI * 2 / n;
			double span = Math.PI * 2 / n * fill;
			for (int k = 0; k < sub; k++) {
				double t0 = start + span * k / sub, t1 = start + span * (k + 1) / sub;
				// ponta mais fraca no fim do traco (cometa)
				float f0 = 1f - (float) k / sub, f1 = 1f - (float) (k + 1) / sub;
				double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
				esp.gradQuad(cx + c0 * (r - w), y, cz + s0 * (r - w), cx + c1 * (r - w), y, cz + s1 * (r - w),
						cx + c1 * (r + w), y, cz + s1 * (r + w), cx + c0 * (r + w), y, cz + s0 * (r + w),
						argb, a * f0, a * f1, a * f1, a * f0);
			}
		}
	}

	/** Tracos radiais (raios) que saem do centro: de r0 a r1, mais fortes na ponta externa. */
	public static void spokes(Esp esp, double cx, double y, double cz, double r0, double r1, double halfAngle, int n, double rot, int argb, float a) {
		for (int d = 0; d < n; d++) {
			double t = rot + d * Math.PI * 2 / n;
			double c0 = Math.cos(t - halfAngle), s0 = Math.sin(t - halfAngle), c1 = Math.cos(t + halfAngle), s1 = Math.sin(t + halfAngle);
			double cm = Math.cos(t), sm = Math.sin(t);
			// triangulo-agulha: base larga em r0 (fraca), ponta fina em r1 (forte)
			esp.gradQuad(cx + c0 * r0, y, cz + s0 * r0, cx + c1 * r0, y, cz + s1 * r0,
					cx + cm * r1, y, cz + sm * r1, cx + cm * r1, y, cz + sm * r1,
					argb, 0f, 0f, a, a);
		}
	}
}
