package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Formas 3D (cubos, diamantes, aneis ou infinito) girando em volta do jogador. */
public class ParticlesModule extends Module implements EspSource {
	private static final int DOTS = 18;
	private static final double A = 0.28;   // tamanho do simbolo
	private static final double D = 0.012;  // tamanho de cada pontinho

	private final IntSetting shape = add(new IntSetting("Shape (1 infinito 2 cubo 3 diamante 4 aneis)", 1, 4, 2, ""));
	private final IntSetting size = add(new IntSetting("Size", 1, 10, 4, ""));
	private final IntSetting speed = add(new IntSetting("Speed", 1, 10, 4, ""));
	private final IntSetting distance = add(new IntSetting("Distance", 1, 8, 3, ""));
	private final IntSetting amount = add(new IntSetting("Amount", 1, 5, 3, ""));
	private final ColorSetting color = add(new ColorSetting("Color", 0));

	public ParticlesModule() {
		super("Particles 3D", Category.EFFECTS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void collect(Esp esp) {
		var me = Minecraft.getInstance().player;
		if (me == null) return;

		float pt = esp.partial();
		double cx = Mth.lerp(pt, me.xOld, me.getX());
		double cy = Mth.lerp(pt, me.yOld, me.getY()) + me.getBbHeight() * 0.5;
		double cz = Mth.lerp(pt, me.zOld, me.getZ());

		double t = System.nanoTime() / 1.0e9;
		double base = t * speed.get() * 0.4;
		double radius = 0.5 + distance.get() * 0.4;
		int n = amount.get();
		int argb = color.get();

		for (int i = 0; i < n; i++) {
			double ang = base + i * (Math.PI * 2.0 / n);
			double px = cx + Math.cos(ang) * radius;
			double pz = cz + Math.sin(ang) * radius;
			double py = cy + Math.sin(t * 1.3 + i * 2.1) * 0.25;
			double tx = -Math.sin(ang);
			double tz = Math.cos(ang);

			if (shape.get() != 1) {
				wire(esp, px, py, pz, t, i, argb);
				continue;
			}

			for (int k = 0; k < DOTS; k++) {
				double s = Math.PI * 2.0 * k / DOTS;
				double sn = Math.sin(s);
				double den = 1.0 + sn * sn;
				double cu = A * Math.cos(s) / den;
				double cv = A * sn * Math.cos(s) / den;
				double x = px + tx * cu;
				double y = py + cv;
				double z = pz + tz * cu;
				esp.box(x - D, y - D, z - D, x + D, y + D, z + D, argb, 0f);
			}
		}
	}

	private static final double[][] CUBE = {
			{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
	private static final int[][] CUBE_E = {
			{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
	private static final double[][] OCTA = {
			{1, 0, 0}, {-1, 0, 0}, {0, 1.3, 0}, {0, -1.3, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final int[][] OCTA_E = {
			{0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {4, 3}, {3, 5}, {5, 2}};

	/** Forma de arestas girando nos tres eixos (cubo, diamante) ou dois aneis cruzados. */
	private void wire(Esp esp, double cx, double cy, double cz, double t, int i, int argb) {
		double s = 0.04 * size.get();
		double ax = t * 0.9 + i * 1.7, ay = t * 1.3 + i * 0.9, az = t * 0.6 + i * 2.3;
		float alpha = 0.95f;
		int sh = shape.get();
		if (sh == 4) {
			int seg = 20;
			for (int ring = 0; ring < 2; ring++) {
				double[] prev = null;
				for (int k = 0; k <= seg; k++) {
					double a = Math.PI * 2.0 * k / seg;
					double x = Math.cos(a) * s * 1.2, y = Math.sin(a) * s * 1.2, z = 0;
					if (ring == 1) { z = y; y = 0; }
					double[] p = rot(x, y, z, ax, ay, az);
					if (prev != null) esp.thinLine(cx + prev[0], cy + prev[1], cz + prev[2], cx + p[0], cy + p[1], cz + p[2], argb, alpha, 1.4f);
					prev = p;
				}
			}
			return;
		}
		double[][] v = sh == 3 ? OCTA : CUBE;
		int[][] e = sh == 3 ? OCTA_E : CUBE_E;
		double[][] rv = new double[v.length][];
		for (int k = 0; k < v.length; k++) rv[k] = rot(v[k][0] * s, v[k][1] * s, v[k][2] * s, ax, ay, az);
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
