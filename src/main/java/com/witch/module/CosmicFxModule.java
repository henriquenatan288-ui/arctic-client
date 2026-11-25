package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Efeitos cosmicos 3D em volta do jogador, desenhados como sprites de luz virados para a camera (brilho radial + raios em cruz):
 * 1 estrelas brilhantes (com anel de marcas no chao), 2 buraco negro (disco de acrecao), 3 pulsar (jatos girando + aneis),
 * 4 tres Marias (as tres estrelas do cinturao de Orion). Tudo original, desenhado so com quadrilateros e linhas.
 */
public class CosmicFxModule extends Module implements EspSource {
	private final IntSetting style = add(new IntSetting("Efeito (1 estrelas 2 buraco negro 3 pulsar 4 tres Marias)", 1, 4, 1, ""));
	private final IntSetting size = add(new IntSetting("Tamanho", 1, 10, 5, ""));
	private final IntSetting speed = add(new IntSetting("Velocidade", 1, 10, 4, ""));
	private final IntSetting amount = add(new IntSetting("Quantidade (estrelas)", 1, 10, 6, ""));
	private final IntSetting ring = add(new IntSetting("Anel no chao - efeito 1 (0/1)", 0, 1, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Cor (padrao = do efeito)", 0));
	private final IntSetting ownColor = add(new IntSetting("Usar a cor escolhida (0/1)", 0, 1, 0, ""));

	// vetores da camera (direita e cima) do quadro atual
	private double rx, ry, rz, ux, uy, uz;

	public CosmicFxModule() {
		super("Cosmic FX", Category.EFFECTS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	private static double hash(int i, int k) {
		double v = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
		return v - Math.floor(v);
	}

	private int col(int def) {
		return ownColor.get() == 1 ? color.get() : def;
	}

	@Override
	public void collect(Esp esp) {
		var mc = Minecraft.getInstance();
		var me = mc.player;
		if (me == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		var l = cam.leftVector();
		var u = cam.upVector();
		rx = -l.x(); ry = -l.y(); rz = -l.z();
		ux = u.x(); uy = u.y(); uz = u.z();

		float pt = esp.partial();
		double cx = Mth.lerp(pt, me.xOld, me.getX());
		double cy = Mth.lerp(pt, me.yOld, me.getY());
		double cz = Mth.lerp(pt, me.zOld, me.getZ());
		double h = me.getBbHeight();
		double t = System.nanoTime() / 1.0e9 * (0.35 + speed.get() * 0.15);
		double sc = 0.35 + size.get() * 0.13;
		switch (style.get()) {
			case 2 -> blackHole(esp, cx, cy + h + 0.9 * sc + 0.4, cz, t, sc);
			case 3 -> pulsar(esp, cx, cy + h + 1.0 * sc + 0.5, cz, t, sc);
			case 4 -> threeMarias(esp, cx, cy + h * 0.5, cz, t, sc, h);
			default -> stars(esp, cx, cy, cz, t, sc, h);
		}
	}

	// ---------- primitivas ----------

	/** Brilho radial suave (leque de triangulos: centro opaco, borda transparente), virado para a camera. */
	private void glow(Esp esp, double x, double y, double z, double r, int argb, float a) {
		int n = 14;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
			double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
			esp.gradQuad(x, y, z,
					x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0,
					x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1,
					x, y, z, argb, a, 0f, 0f, a);
		}
	}

	/** Raio fino em losango (opaco no meio, some nas pontas) na direcao (dx,dy) do plano da camera. */
	private void ray(Esp esp, double x, double y, double z, double ang, double len, double w, int argb, float a) {
		double ca = Math.cos(ang), sa = Math.sin(ang);
		// eixo do raio e perpendicular, no plano da camera
		double ax = rx * ca + ux * sa, ay = ry * ca + uy * sa, az = rz * ca + uz * sa;
		double bx = -rx * sa + ux * ca, by = -ry * sa + uy * ca, bz = -rz * sa + uz * ca;
		esp.gradQuad(x - ax * len, y - ay * len, z - az * len,
				x + bx * w, y + by * w, z + bz * w,
				x + ax * len, y + ay * len, z + az * len,
				x - bx * w, y - by * w, z - bz * w, argb, 0f, a, 0f, a);
	}

	/** Estrela brilhante: halo + miolo branco + raios em cruz (vertical mais longo) + raios diagonais curtos. */
	private void flare(Esp esp, double x, double y, double z, double s, int argb, float a, double rot) {
		glow(esp, x, y, z, s * 0.9, argb, 0.35f * a);
		glow(esp, x, y, z, s * 0.38, 0xFFFFFFFF, 0.9f * a);
		ray(esp, x, y, z, rot + Math.PI / 2, s * 2.6, s * 0.07, argb, 0.9f * a);
		ray(esp, x, y, z, rot, s * 1.7, s * 0.06, argb, 0.8f * a);
		ray(esp, x, y, z, rot + Math.PI / 4, s * 1.0, s * 0.04, argb, 0.55f * a);
		ray(esp, x, y, z, rot - Math.PI / 4, s * 1.0, s * 0.04, argb, 0.55f * a);
	}

	private void loopH(Esp esp, double cx, double cy, double cz, double r, int seg, int argb, float a, float w, double rot) {
		double px = cx + Math.cos(rot) * r, pz = cz + Math.sin(rot) * r;
		for (int i = 1; i <= seg; i++) {
			double an = rot + Math.PI * 2 * i / seg;
			double qx = cx + Math.cos(an) * r, qz = cz + Math.sin(an) * r;
			esp.thinLine(px, cy, pz, qx, cy, qz, argb, a, w);
			px = qx; pz = qz;
		}
	}

	// ---------- efeitos ----------

	private void stars(Esp esp, double cx, double cy, double cz, double t, double sc, double h) {
		int n = 6 + amount.get() * 3;
		int base = col(0xFFD8FFE6);
		for (int i = 0; i < n; i++) {
			double r = (0.9 + hash(i, 1) * 2.1) * (0.8 + sc * 0.35);
			double ang = hash(i, 2) * Math.PI * 2 + t * (0.25 + hash(i, 3) * 0.5) * (i % 2 == 0 ? 1 : -1);
			double y = cy + (-0.2 + hash(i, 4) * (h + 1.2)) + Math.sin(t * 1.2 + i) * 0.12;
			double tw = 0.55 + 0.45 * Math.sin(t * 3.0 + i * 1.7);
			double s = (0.05 + hash(i, 5) * 0.11) * sc * (0.7 + 0.5 * tw);
			flare(esp, cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r, s, base, (float) (0.5 + 0.5 * tw), 0);
		}
		if (ring.get() == 1) {
			double rr = 2.0 + sc * 0.9, y = cy + 0.03;
			loopH(esp, cx, y, cz, rr, 72, base, 0.75f, 1.2f, t * 0.15);
			loopH(esp, cx, y, cz, rr * 0.93, 72, base, 0.3f, 1.0f, -t * 0.1);
			for (int i = 0; i < 48; i++) {
				double an = t * 0.15 + Math.PI * 2 * i / 48;
				double r2 = rr + (i % 4 == 0 ? 0.28 : 0.14);
				esp.thinLine(cx + Math.cos(an) * rr, y, cz + Math.sin(an) * rr, cx + Math.cos(an) * r2, y, cz + Math.sin(an) * r2, base, 0.6f, 1.0f);
			}
		}
	}

	private void blackHole(Esp esp, double cx, double cy, double cz, double t, double sc) {
		int hot = col(0xFFFF9A3C);
		double R = 0.55 * sc;
		glow(esp, cx, cy, cz, R * 3.2, hot, 0.22f);
		// disco de acrecao: faixas inclinadas girando, laranja por fora e quase branco por dentro
		double tilt = Math.toRadians(72), ct = Math.cos(tilt), st = Math.sin(tilt);
		int seg = 72;
		for (int band = 0; band < 3; band++) {
			double r0 = R * (1.35 + band * 0.55), r1 = R * (1.85 + band * 0.55);
			int c = band == 0 ? 0xFFFFE9C0 : (band == 1 ? hot : 0xFFE0571E);
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg + t * (1.6 - band * 0.4), a1 = Math.PI * 2 * (i + 1) / seg + t * (1.6 - band * 0.4);
				float br = (float) (0.45 + 0.4 * Math.sin(a0 * 3 - t * 4 + band));
				double[] p0 = disc(cx, cy, cz, r0, a0, ct, st), p1 = disc(cx, cy, cz, r1, a0, ct, st);
				double[] p2 = disc(cx, cy, cz, r1, a1, ct, st), p3 = disc(cx, cy, cz, r0, a1, ct, st);
				esp.gradQuad(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], c, br, br * 0.25f, br * 0.25f, br);
			}
		}
		// anel de fotons (lente gravitacional): circulo fino virado para a camera
		for (int i = 0; i < 48; i++) {
			double a0 = Math.PI * 2 * i / 48, a1 = Math.PI * 2 * (i + 1) / 48;
			esp.thinLine(cx + (rx * Math.cos(a0) + ux * Math.sin(a0)) * R * 1.12, cy + (ry * Math.cos(a0) + uy * Math.sin(a0)) * R * 1.12, cz + (rz * Math.cos(a0) + uz * Math.sin(a0)) * R * 1.12,
					cx + (rx * Math.cos(a1) + ux * Math.sin(a1)) * R * 1.12, cy + (ry * Math.cos(a1) + uy * Math.sin(a1)) * R * 1.12, cz + (rz * Math.cos(a1) + uz * Math.sin(a1)) * R * 1.12,
					0xFFFFF2D8, 0.9f, 1.6f);
		}
		// particulas espiralando para dentro, com rastro
		int n = 10 + amount.get() * 2;
		for (int i = 0; i < n; i++) {
			double ph = (t * 0.35 + hash(i, 9)) % 1.0;
			double rr = R * (3.4 - 2.2 * ph);
			double an = hash(i, 8) * 6.283 + t * 1.4 + ph * 5.0;
			for (int k = 0; k < 6; k++) {
				double pk = Math.max(0, ph - k * 0.02);
				double r2 = R * (3.4 - 2.2 * pk), a2 = hash(i, 8) * 6.283 + t * 1.4 + pk * 5.0 - k * 0.05;
				double[] p = disc(cx, cy, cz, r2, a2, ct, st);
				double d = 0.02 * sc * (1.0 - k / 6.0);
				esp.solid(p[0] - d, p[1] - d, p[2] - d, p[0] + d, p[1] + d, p[2] + d, 0xFFFFD9A0, 0.8f * (1f - k / 6f));
			}
		}
		// nucleo negro por cima
		glow(esp, cx, cy, cz, R * 1.0, 0xFF000000, 1f);
		glow(esp, cx, cy, cz, R * 0.7, 0xFF000000, 1f);
	}

	private double[] disc(double cx, double cy, double cz, double r, double a, double ct, double st) {
		double x = Math.cos(a) * r, z = Math.sin(a) * r;
		return new double[] {cx + x, cy + z * st, cz + z * ct};
	}

	private void pulsar(Esp esp, double cx, double cy, double cz, double t, double sc) {
		int c = col(0xFF9FD8FF);
		double ph = t * 1.1;
		glow(esp, cx, cy, cz, 0.7 * sc, c, 0.5f);
		glow(esp, cx, cy, cz, 0.28 * sc, 0xFFFFFFFF, 1f);
		// dois jatos opostos girando (cruzados em dois planos para ter volume)
		double tiltA = Math.toRadians(25);
		for (int k = 0; k < 2; k++) {
			double a = ph + k * Math.PI / 2;
			double dx = Math.cos(a) * Math.sin(tiltA), dz = Math.sin(a) * Math.sin(tiltA), dy = Math.cos(tiltA);
			double px = -Math.sin(a), pz = Math.cos(a);
			double L = 2.6 * sc, w = 0.10 * sc;
			for (int s = -1; s <= 1; s += 2) {
				esp.gradQuad(cx, cy, cz,
						cx + dx * L * s + px * w, cy + dy * L * s, cz + dz * L * s + pz * w,
						cx + dx * L * s * 1.0, cy + dy * L * s * 1.0, cz + dz * L * s * 1.0,
						cx + dx * L * s - px * w, cy + dy * L * s, cz + dz * L * s - pz * w, c, 0.9f, 0f, 0f, 0f);
			}
		}
		// aneis de pulso se expandindo
		for (int i = 0; i < 3; i++) {
			double p = (t * 0.45 + i / 3.0) % 1.0;
			double r = (0.25 + p * 2.4) * sc;
			loopH(esp, cx, cy, cz, r, 48, c, (float) (0.85 * (1 - p)), 1.5f, 0);
		}
		// raios em cruz fixos
		flare(esp, cx, cy, cz, 0.35 * sc, c, 0.9f, 0);
	}

	private void threeMarias(Esp esp, double cx, double cy, double cz, double t, double sc, double h) {
		int base = col(0xFFB7D6FF);
		double ang = t * 0.25;
		double ox = Math.cos(ang) * (1.6 + sc * 0.5), oz = Math.sin(ang) * (1.6 + sc * 0.5);
		double tilt = Math.toRadians(22);
		double sp = 0.95 * sc;
		double[][] sp3 = new double[3][];
		for (int i = -1; i <= 1; i++) {
			// cinturao: tres estrelas em linha inclinada
			double dx = Math.cos(ang + Math.PI / 2) * Math.cos(tilt) * sp * i, dz = Math.sin(ang + Math.PI / 2) * Math.cos(tilt) * sp * i;
			sp3[i + 1] = new double[] {cx + ox + dx, cy + h + 0.9 + Math.sin(tilt) * sp * i, cz + oz + dz};
		}
		for (int i = 0; i < 2; i++)
			esp.thinLine(sp3[i][0], sp3[i][1], sp3[i][2], sp3[i + 1][0], sp3[i + 1][1], sp3[i + 1][2], base, 0.25f, 1.0f);
		double[] ss = {0.30, 0.36, 0.26};
		for (int i = 0; i < 3; i++) {
			double tw = 0.8 + 0.2 * Math.sin(t * 2.6 + i * 2.0);
			flare(esp, sp3[i][0], sp3[i][1], sp3[i][2], ss[i] * sc * 1.4 * tw, base, 1f, Math.sin(t * 0.7 + i) * 0.15);
		}
		// poeira de estrelas pequenas em volta
		for (int i = 0; i < 10 + amount.get(); i++) {
			double an = hash(i, 11) * 6.283 + t * 0.3;
			double r = 1.2 + hash(i, 12) * 2.4;
			double tw = 0.5 + 0.5 * Math.sin(t * 3 + i);
			flare(esp, cx + Math.cos(an) * r, cy + (hash(i, 13) - 0.3) * (h + 1.4), cz + Math.sin(an) * r, 0.045 * sc * (0.6 + tw), base, (float) (0.4 + 0.5 * tw), 0);
		}
	}
}
