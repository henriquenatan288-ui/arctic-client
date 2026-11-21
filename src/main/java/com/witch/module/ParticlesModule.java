package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Formas 3D girando em volta do jogador, com rastro, brilho e varios caminhos de orbita. */
public class ParticlesModule extends Module implements EspSource {
	private static final int DOTS = 18;
	private static final double A = 0.28;   // tamanho do simbolo infinito
	private static final double D = 0.012;  // tamanho de cada pontinho

	private final IntSetting shape = add(new IntSetting("Shape (1 infinito 2 cubo 3 diamante 4 aneis 5 tetraedro 6 estrela 7 esfera)", 1, 7, 2, ""));
	private final IntSetting path = add(new IntSetting("Path (1 anel 2 giro 3 espiral 4 coroa)", 1, 4, 1, ""));
	private final IntSetting size = add(new IntSetting("Size", 1, 10, 4, ""));
	private final IntSetting speed = add(new IntSetting("Speed", 1, 10, 4, ""));
	private final IntSetting distance = add(new IntSetting("Distance", 1, 8, 3, ""));
	private final IntSetting amount = add(new IntSetting("Amount", 1, 5, 3, ""));
	private final IntSetting trail = add(new IntSetting("Trail", 0, 8, 6, ""));
	private final IntSetting glow = add(new IntSetting("Fill and glow (0/1)", 0, 1, 1, ""));
	private final IntSetting rainbow = add(new IntSetting("Rainbow (0/1)", 0, 1, 0, ""));
	private final ColorSetting color = add(new ColorSetting("Color", 0));

	public ParticlesModule() {
		super("Particles 3D", Category.EFFECTS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	/** Posicao (relativa ao centro do jogador) da particula i de n no instante tt, conforme o caminho escolhido. */
	private double[] pos(int i, int n, double tt, double radius, double h) {
		double ang = tt * speed.get() * 0.4 + i * (Math.PI * 2.0 / n);
		double c = Math.cos(ang), s = Math.sin(ang);
		switch (path.get()) {
			case 2 -> { // giro: orbita inclinada, cada uma num plano diferente (efeito giroscopio)
				double tilt = 0.55 + 0.25 * Math.sin(tt * 0.5 + i);
				double x = c * radius, z = s * radius;
				double y1 = -z * Math.sin(tilt), z1 = z * Math.cos(tilt);
				double ph = i * Math.PI / n;
				double cp = Math.cos(ph), sp = Math.sin(ph);
				return new double[] {x * cp + z1 * sp, y1, -x * sp + z1 * cp};
			}
			case 3 -> { // espiral: sobe e desce o corpo todo, abrindo e fechando
				double r2 = radius * (0.55 + 0.45 * Math.sin(tt * 0.7 + i * 1.9));
				return new double[] {c * r2, Math.sin(tt * 0.9 + i * 2.4) * h * 0.55, s * r2};
			}
			case 4 -> { // coroa: circulo pequeno em cima da cabeca
				double r2 = 0.3 + distance.get() * 0.08;
				return new double[] {c * r2, h * 0.5 + 0.35 + Math.sin(tt * 2.0 + i) * 0.05, s * r2};
			}
			default -> {
				return new double[] {c * radius, Math.sin(tt * 1.3 + i * 2.1) * 0.25, s * radius};
			}
		}
	}

	@Override
	public void collect(Esp esp) {
		var me = Minecraft.getInstance().player;
		if (me == null) return;

		float pt = esp.partial();
		double cx = Mth.lerp(pt, me.xOld, me.getX());
		double cy = Mth.lerp(pt, me.yOld, me.getY()) + me.getBbHeight() * 0.5;
		double cz = Mth.lerp(pt, me.zOld, me.getZ());
		double h = me.getBbHeight();

		double t = System.nanoTime() / 1.0e9;
		double radius = 0.5 + distance.get() * 0.4;
		int n = amount.get();
		int tr = trail.get();
		boolean fx = glow.get() == 1;

		for (int i = 0; i < n; i++) {
			int argb = rainbow.get() == 1
					? ColorSetting.hsvToRgb((float) (((t * 0.12) + i / (double) n) % 1.0), 0.72f, 1f)
					: color.get();
			double[] p = pos(i, n, t, radius, h);
			double px = cx + p[0], py = cy + p[1], pz = cz + p[2];

			// rastro: segmentos ligando as posicoes passadas, sumindo e afinando
			if (tr > 0) {
				double[] prev = p;
				for (int k = 1; k <= tr; k++) {
					double[] q = pos(i, n, t - k * 0.07, radius, h);
					float a = 0.9f * (1f - (k - 1f) / tr);
					float w = 1.0f + 2.4f * (1f - k / (tr + 1f));
					esp.thinLine(cx + prev[0], cy + prev[1], cz + prev[2], cx + q[0], cy + q[1], cz + q[2], argb, a, w);
					// pontinhos de luz que diminuem: cauda de cometa
					double ds = 0.04 * size.get() * 0.38 * (1.0 - k / (tr + 1.0));
					esp.solid(cx + q[0] - ds, cy + q[1] - ds, cz + q[2] - ds, cx + q[0] + ds, cy + q[1] + ds, cz + q[2] + ds, argb, 0.55f * (1f - (k - 1f) / tr));
					prev = q;
				}
			}

			// brilho central (ponto de luz)
			double s = 0.04 * size.get() * (1.0 + 0.1 * Math.sin(t * 3.0 + i * 1.3));
			if (fx) {
				double c1 = s * 0.2, c2 = s * 0.42;
				esp.solid(px - c1, py - c1, pz - c1, px + c1, py + c1, pz + c1, 0xFFFFFFFF, 0.85f);
				esp.solid(px - c2, py - c2, pz - c2, px + c2, py + c2, pz + c2, argb, 0.22f);
			}

			if (shape.get() == 1) {
				double[] q = pos(i, n, t + 0.03, radius, h);
				double tx = q[0] - p[0], tz = q[2] - p[2];
				double len = Math.hypot(tx, tz);
				if (len < 1.0E-5) { tx = 1; tz = 0; } else { tx /= len; tz /= len; }
				for (int k = 0; k < DOTS; k++) {
					double u = Math.PI * 2.0 * k / DOTS;
					double sn = Math.sin(u);
					double den = 1.0 + sn * sn;
					double cu = A * Math.cos(u) / den;
					double cv = A * sn * Math.cos(u) / den;
					double x = px + tx * cu, y = py + cv, z = pz + tz * cu;
					esp.box(x - D, y - D, z - D, x + D, y + D, z + D, argb, 0f);
				}
			} else {
				wire(esp, px, py, pz, s, t, i, argb, fx);
			}
		}
	}

	// ---------- malhas ----------
	private static final double[][] CUBE = {
			{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
	private static final int[][] CUBE_E = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
	private static final int[][] CUBE_F = {
			{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}};
	private static final double[][] OCTA = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1.3, 0}, {0, -1.3, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final int[][] OCTA_E = {
			{0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {4, 3}, {3, 5}, {5, 2}};
	private static final int[][] OCTA_F = {
			{2, 0, 4}, {2, 4, 1}, {2, 1, 5}, {2, 5, 0}, {3, 0, 4}, {3, 4, 1}, {3, 1, 5}, {3, 5, 0}};
	private static final double[][] TETRA = {
			{0.9, 0.9, 0.9}, {0.9, -0.9, -0.9}, {-0.9, 0.9, -0.9}, {-0.9, -0.9, 0.9}};
	private static final int[][] TETRA_E = {{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};
	private static final int[][] TETRA_F = {{0, 1, 2}, {0, 1, 3}, {0, 2, 3}, {1, 2, 3}};

	// estrela: 6 pontas longas (indices 0..5: +x -x +y -y +z -z) e 8 vertices internos (6..13)
	private static final double[][] STAR = new double[14][];
	private static final int[][] STAR_E;
	private static final int[][] STAR_F;

	static {
		double tip = 1.7, in = 0.45;
		double[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int k = 0; k < 6; k++) STAR[k] = new double[] {axes[k][0] * tip, axes[k][1] * tip, axes[k][2] * tip};
		for (int k = 0; k < 8; k++) {
			STAR[6 + k] = new double[] {(k & 1) == 0 ? -in : in, (k & 2) == 0 ? -in : in, (k & 4) == 0 ? -in : in};
		}
		java.util.List<int[]> edges = new java.util.ArrayList<>();
		java.util.List<int[]> faces = new java.util.ArrayList<>();
		for (int tp = 0; tp < 6; tp++) {
			int axis = tp / 2;
			boolean pos = tp % 2 == 0;
			java.util.List<Integer> ring = new java.util.ArrayList<>();
			for (int k = 0; k < 8; k++) {
				boolean bit = (k & (1 << axis)) != 0;
				if (bit == pos) ring.add(6 + k);
			}
			// ordena os 4 vertices em volta da ponta (ordem ciclica: troca os dois ultimos)
			int[] r = {ring.get(0), ring.get(1), ring.get(3), ring.get(2)};
			for (int k = 0; k < 4; k++) {
				edges.add(new int[] {tp, r[k]});
				faces.add(new int[] {tp, r[k], r[(k + 1) % 4]});
			}
		}
		STAR_E = edges.toArray(new int[0][]);
		STAR_F = faces.toArray(new int[0][]);
	}

	/** Desenha a forma escolhida girando nos tres eixos, com faces translucidas quando fx esta ligado. */
	private void wire(Esp esp, double cx, double cy, double cz, double s, double t, int i, int argb, boolean fx) {
		double ax = t * 0.9 + i * 1.7, ay = t * 1.3 + i * 0.9, az = t * 0.6 + i * 2.3;
		float alpha = 0.95f;
		int sh = shape.get();
		if (sh == 4 || sh == 7) {
			int seg = 24;
			int rings = sh == 4 ? 2 : 3;
			double rad = s * (sh == 4 ? 1.2 : 1.35);
			for (int ring = 0; ring < rings; ring++) {
				double[] prev = null;
				for (int k = 0; k <= seg; k++) {
					double a = Math.PI * 2.0 * k / seg;
					double x = Math.cos(a) * rad, y = Math.sin(a) * rad, z = 0;
					if (ring == 1) { z = y; y = 0; }
					else if (ring == 2) { z = x; x = 0; }
					double[] p = rot(x, y, z, ax, ay, az);
					if (prev != null) esp.thinLine(cx + prev[0], cy + prev[1], cz + prev[2], cx + p[0], cy + p[1], cz + p[2], argb, alpha, 1.4f);
					prev = p;
				}
			}
			return;
		}
		double[][] v;
		int[][] e, f;
		double sc = s;
		switch (sh) {
			case 3 -> { v = OCTA; e = OCTA_E; f = OCTA_F; }
			case 5 -> { v = TETRA; e = TETRA_E; f = TETRA_F; sc = s * 1.1; }
			case 6 -> { v = STAR; e = STAR_E; f = STAR_F; sc = s * 0.8; }
			default -> { v = CUBE; e = CUBE_E; f = CUBE_F; }
		}
		double[][] rv = new double[v.length][];
		for (int k = 0; k < v.length; k++) rv[k] = rot(v[k][0] * sc, v[k][1] * sc, v[k][2] * sc, ax, ay, az);
		if (fx) {
			for (int[] fc : f) {
				double[] a = rv[fc[0]], b = rv[fc[1]], c = rv[fc[2]], d = rv[fc.length > 3 ? fc[3] : fc[2]];
				esp.gradQuad(cx + a[0], cy + a[1], cz + a[2], cx + b[0], cy + b[1], cz + b[2],
						cx + c[0], cy + c[1], cz + c[2], cx + d[0], cy + d[1], cz + d[2], argb, 0.16f, 0.16f, 0.16f, 0.16f);
			}
		}
		for (int[] ed : e) {
			double[] a = rv[ed[0]], b = rv[ed[1]];
			esp.thinLine(cx + a[0], cy + a[1], cz + a[2], cx + b[0], cy + b[1], cz + b[2], argb, alpha, 1.4f);
		}
	}

	private static double[] rot(double x, double y, double z, double ax, double ay, double az) {
		double c = Math.cos(ax), sn = Math.sin(ax);
		double y1 = y * c - z * sn, z1 = y * sn + z * c;
		c = Math.cos(ay); sn = Math.sin(ay);
		double x2 = x * c + z1 * sn, z2 = -x * sn + z1 * c;
		c = Math.cos(az); sn = Math.sin(az);
		return new double[] {x2 * c - y1 * sn, x2 * sn + y1 * c, z2};
	}
}
