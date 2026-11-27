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
	private final IntSetting dist1 = add(new IntSetting("Distancia das estrelas - efeito 1", 1, 10, 3, ""));
	private final IntSetting azim = add(new IntSetting("Direcao no ceu - efeitos 2/3/4 (graus)", 0, 359, 40, ""));
	private final IntSetting elev = add(new IntSetting("Altura no ceu - efeitos 2/3/4 (graus)", 5, 80, 30, ""));
	private final IntSetting ring = add(new IntSetting("Anel no chao - efeito 1 (0/1)", 0, 1, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Cor (padrao = do efeito)", 0));
	private final IntSetting warp = add(new IntSetting("Distorcao do espaco - efeito 2 (0/1)", 0, 1, 1, ""));
	private final IntSetting shader = add(new IntSetting("Shader de tela (0/1): lente, bloom, rastro azul", 0, 1, 1, ""));
	private final IntSetting shaderPower = add(new IntSetting("Shader - intensidade", 1, 10, 6, ""));
	private final IntSetting ownColor = add(new IntSetting("Usar a cor escolhida (0/1)", 0, 1, 0, ""));

	// vetores da camera (direita e cima) do quadro atual
	private double rx, ry, rz, ux, uy, uz, fx, fy, fz;

	public static CosmicFxModule instance;
	// buraco negro do quadro atual (para a lente do shader)
	private static volatile double hx, hy, hz, hR;
	private static volatile long holeNanos;
	private net.minecraft.resources.Identifier applied;

	public CosmicFxModule() {
		super("Cosmic FX", Category.EFFECTS);
		instance = this;
	}

	private static boolean ours(net.minecraft.resources.Identifier id) {
		return id != null && "witch".equals(id.getNamespace());
	}

	@Override
	public void onTick() {
		var gr = Minecraft.getInstance().gameRenderer;
		if (gr == null) return;
		var cur = gr.currentPostEffect();
		if (shader.get() == 0 || (FilterModule.instance != null && FilterModule.instance.isEnabled())) {
			if (applied != null && ours(cur)) gr.clearPostEffect();
			applied = null;
			return;
		}
		if (cur != null && !ours(cur)) return; // visao de creeper/aranha: deixa o jogo mandar
		var want = net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "cosmic");
		if (want.equals(cur) && want.equals(applied)) return;
		try {
			var m = net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("setPostEffect", net.minecraft.resources.Identifier.class);
			m.setAccessible(true);
			m.invoke(gr, want);
			applied = want;
		} catch (Throwable t) {
			applied = null;
			System.out.println("[Witch] cosmic shader: " + t);
		}
	}

	@Override
	protected void onDisable() {
		applied = null;
		try {
			var gr = Minecraft.getInstance().gameRenderer;
			if (gr != null && ours(gr.currentPostEffect())) gr.clearPostEffect();
		} catch (Throwable ignored) {
		}
	}

	/** Dados do shader (2 vec4): posicao do buraco negro na tela + parametros. Chamado pelo PostPassMixin a cada quadro. */
	public static float[] lensData() {
		var m = instance;
		if (m == null || !m.isEnabled() || m.shader.get() == 0) return null;
		float pw = m.shaderPower.get() / 10f;
		float[] d = {0.5f, 0.5f, 0f, 0f, 5.5f * pw, 0.35f + 0.9f * pw, 6f * pw, 0.18f + 0.25f * pw};
		var mc = Minecraft.getInstance();
		if (m.style.get() == 2 && System.nanoTime() - holeNanos < 300_000_000L && mc.gameRenderer != null && mc.getWindow() != null) {
			var cam = mc.gameRenderer.mainCamera();
			var pos = cam.position();
			double dx = hx - pos.x, dy = hy - pos.y, dz = hz - pos.z;
			var f = cam.forwardVector();
			var l = cam.leftVector();
			var u = cam.upVector();
			double z = dx * f.x() + dy * f.y() + dz * f.z();
			if (z > 1.0) {
				double xr = -(dx * l.x() + dy * l.y() + dz * l.z()), yu = dx * u.x() + dy * u.y() + dz * u.z();
				double th = Math.tan(Math.toRadians(cam.getFov()) * 0.5);
				double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
				double nx = xr / z / (th * aspect), ny = yu / z / th;
				d[0] = (float) (nx * 0.5 + 0.5);
				d[1] = (float) (ny * 0.5 + 0.5);
				d[2] = (float) Math.min(0.09, hR / z / th * 0.5);
				d[3] = d[2] > 0.002f ? 1f : 0f;
			}
		}
		return d;
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
		var cam = mc.gameRenderer.mainCamera();
		var l = cam.leftVector();
		var u = cam.upVector();
		rx = -l.x(); ry = -l.y(); rz = -l.z();
		ux = u.x(); uy = u.y(); uz = u.z();
		var f = cam.forwardVector();
		fx = f.x(); fy = f.y(); fz = f.z();

		float pt = esp.partial();
		double cx = Mth.lerp(pt, me.xOld, me.getX());
		double cy = Mth.lerp(pt, me.yOld, me.getY());
		double cz = Mth.lerp(pt, me.zOld, me.getZ());
		double h = me.getBbHeight();
		double t = System.nanoTime() / 1.0e9 * (0.35 + speed.get() * 0.15);
		double sc = 0.35 + size.get() * 0.13;
		switch (style.get()) {
			case 2 -> {
				double[] a = skyPos(cx, cy + me.getEyeHeight(), cz, t);
				blackHole(esp, a[0], a[1], a[2], t, sc * 18.0);
			}
			case 3 -> {
				double[] a = skyPos(cx, cy + me.getEyeHeight(), cz, t);
				pulsar(esp, a[0], a[1], a[2], t, sc * 12.0);
			}
			case 4 -> {
				double[] a = skyPos(cx, cy + me.getEyeHeight(), cz, t);
				threeMarias(esp, a[0], a[1], a[2], t, sc);
			}
			default -> stars(esp, cx, cy, cz, t, sc, h);
		}
	}

	/** Posicao no ceu: muito longe (~110 blocos) numa direcao fixa do mundo, para nao balancar com o bob da camera e nao tapar nada. */
	private double[] skyPos(double ex, double ey, double ez, double t) {
		double az = Math.toRadians(azim.get()) + t * 0.004, el = Math.toRadians(elev.get()), D = 110.0;
		if (System.getenv("CI") != null) { // teste visual no CI: na frente da camera para caber no print
			var me = Minecraft.getInstance().player;
			if (me != null) { az = Math.PI + Math.toRadians(me.getYRot()); el = Math.toRadians(15); }
		}
		return new double[] {ex + Math.sin(az) * Math.cos(el) * D, ey + Math.sin(el) * D, ez - Math.cos(az) * Math.cos(el) * D};
	}

	/** Disco chapado (alfa igual no centro e na borda), virado para a camera: serve de "bola" com borda definida. */
	private void disk(Esp esp, double x, double y, double z, double r, int argb, float a) {
		int n = 28;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
			double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
			esp.gradQuad(x, y, z,
					x + rx * c0 + ux * s0, y + ry * c0 + uy * s0, z + rz * c0 + uz * s0,
					x + rx * c1 + ux * s1, y + ry * c1 + uy * s1, z + rz * c1 + uz * s1,
					x, y, z, argb, a, a, a, a);
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

	/** Estrela brilhante: bloom em 3 camadas, miolo branco, raios longos em cruz + 8 pontas, tudo com degrade suave. */
	private void flare(Esp esp, double x, double y, double z, double s, int argb, float a, double rot) {
		glow(esp, x, y, z, s * 1.5, argb, 0.16f * a);
		glow(esp, x, y, z, s * 0.8, argb, 0.4f * a);
		glow(esp, x, y, z, s * 0.4, 0xFFFFFFFF, 0.95f * a);
		ray(esp, x, y, z, rot + Math.PI / 2, s * 3.4, s * 0.075, argb, 0.95f * a);
		ray(esp, x, y, z, rot, s * 2.3, s * 0.065, argb, 0.85f * a);
		ray(esp, x, y, z, rot + Math.PI / 4, s * 1.35, s * 0.045, argb, 0.6f * a);
		ray(esp, x, y, z, rot - Math.PI / 4, s * 1.35, s * 0.045, argb, 0.6f * a);
		ray(esp, x, y, z, rot + Math.PI / 8, s * 0.8, s * 0.03, 0xFFFFFFFF, 0.4f * a);
		ray(esp, x, y, z, rot - Math.PI / 8, s * 0.8, s * 0.03, 0xFFFFFFFF, 0.4f * a);
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
			double r = (0.9 + hash(i, 1) * 2.1) * (0.8 + sc * 0.35) * (0.4 + dist1.get() * 0.2);
			double ang = hash(i, 2) * Math.PI * 2 + t * (0.25 + hash(i, 3) * 0.5) * (i % 2 == 0 ? 1 : -1);
			double y = cy + (-0.2 + hash(i, 4) * (h + 1.2)) + Math.sin(t * 1.2 + i) * 0.12;
			double tw = 0.55 + 0.45 * Math.sin(t * 3.0 + i * 1.7);
			double s = (0.08 + hash(i, 5) * 0.2 + (i % 7 == 0 ? 0.18 : 0)) * sc * (0.75 + 0.45 * tw);
			int pal = ownColor.get() == 1 ? base : (i % 3 == 0 ? 0xFFFFE9B0 : (i % 3 == 1 ? 0xFFBFD9FF : 0xFFD8FFE6));
			flare(esp, cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r, s, pal, (float) (0.55 + 0.45 * tw), t * 0.2 * (i % 2 == 0 ? 1 : -1));
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

	private double ex1x, ex1y, ex1z, ex2x, ex2y, ex2z;

	/** Ponto do disco: base = direita da camera (levemente rolada) e um eixo quase de fundo (disco visto de lado, ~14 graus). */
	private double[] disc(double cx, double cy, double cz, double r, double a) {
		double c = Math.cos(a) * r, s = Math.sin(a) * r;
		return new double[] {cx + ex1x * c + ex2x * s, cy + ex1y * c + ex2y * s, cz + ex1z * c + ex2z * s};
	}

	private static int ramp(double k, int hot) { // 0 = interno (quase branco) ... 1 = externo (vermelho escuro)
		int[] c = {0xFFFFFAEB, 0xFFFFD98A, hot, 0xFFE8561C, 0xFF9A2410};
		double x = Math.max(0, Math.min(0.9999, k)) * (c.length - 1);
		int i = (int) x; double f = x - i;
		int r = (int) (((c[i] >> 16 & 255) * (1 - f)) + ((c[i + 1] >> 16 & 255) * f));
		int g = (int) (((c[i] >> 8 & 255) * (1 - f)) + ((c[i + 1] >> 8 & 255) * f));
		int bl = (int) (((c[i] & 255) * (1 - f)) + ((c[i + 1] & 255) * f));
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	/** Disco de acrecao liso: 14 aneis finos com degrade de cor (branco -> laranja -> vermelho), bordas suaves, filetes girando mais rapido por dentro. */
	private void discBands(Esp esp, double cx, double cy, double cz, double R, double t, boolean front, int hot) {
		final int seg = 96, steps = 14;
		final double r0 = 1.25, r1 = 4.2;
		for (int st = 0; st < steps; st++) {
			double k0 = st / (double) steps, k1 = (st + 1) / (double) steps;
			double ra = R * (r0 + (r1 - r0) * k0 * k0 * 0.6 - 0 + (r1 - r0) * k0 * 0.4), rb = R * (r0 + (r1 - r0) * k1 * k1 * 0.6 + (r1 - r0) * k1 * 0.4);
			int c = ramp((k0 + k1) * 0.5, hot);
			float env0 = (float) Math.min(Math.min(1.0, k0 * 12 + 0.25), 1.0 - Math.pow(k0, 1.6));
			float env1 = (float) Math.min(Math.min(1.0, k1 * 12 + 0.25), 1.0 - Math.pow(k1, 1.6));
			double w = 3.4 - k0 * 2.4; // velocidade angular: mais rapido por dentro
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
				if ((Math.sin((a0 + a1) * 0.5) < 0) != front) continue;
				double ph = a0 - t * w * 0.5;
				double streak = 0.62 + 0.22 * Math.sin(3 * ph + k0 * 5.0) + 0.16 * Math.sin(11 * ph + k0 * 9.0 + 1.3);
				float b0 = (float) Math.max(0.05, Math.min(1.0, streak)) * env0, b1 = (float) Math.max(0.05, Math.min(1.0, streak)) * env1;
				double[] p0 = disc(cx, cy, cz, ra, a0), p1 = disc(cx, cy, cz, rb, a0), p2 = disc(cx, cy, cz, rb, a1), p3 = disc(cx, cy, cz, ra, a1);
				esp.gradQuad(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], c, b0, b1, b1, b0);
			}
		}
	}

	/** Arco da lente gravitacional: o disco de tras dobrado por cima e por baixo do horizonte (mais forte em cima/embaixo). */
	private void lensArc(Esp esp, double cx, double cy, double cz, double r0, double r1, int argb, float peak) {
		int seg = 96;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			float w0 = (float) Math.pow(Math.abs(Math.sin(a0)), 1.4) * peak, w1 = (float) Math.pow(Math.abs(Math.sin(a1)), 1.4) * peak;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			esp.gradQuad(
					cx + (rx * c0 + ux * s0) * r0, cy + (ry * c0 + uy * s0) * r0, cz + (rz * c0 + uz * s0) * r0,
					cx + (rx * c0 + ux * s0) * r1, cy + (ry * c0 + uy * s0) * r1, cz + (rz * c0 + uz * s0) * r1,
					cx + (rx * c1 + ux * s1) * r1, cy + (ry * c1 + uy * s1) * r1, cz + (rz * c1 + uz * s1) * r1,
					cx + (rx * c1 + ux * s1) * r0, cy + (ry * c1 + uy * s1) * r0, cz + (rz * c1 + uz * s1) * r0,
					argb, w0, 0f, 0f, w1);
		}
	}

	private void ringBill(Esp esp, double cx, double cy, double cz, double r0, double r1, int argb, float in, float out, boolean upperOnly) {
		int seg = 64;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			if (upperOnly && Math.sin((a0 + a1) * 0.5) < -0.15) continue;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			esp.gradQuad(
					cx + (rx * c0 + ux * s0) * r0, cy + (ry * c0 + uy * s0) * r0, cz + (rz * c0 + uz * s0) * r0,
					cx + (rx * c0 + ux * s0) * r1, cy + (ry * c0 + uy * s0) * r1, cz + (rz * c0 + uz * s0) * r1,
					cx + (rx * c1 + ux * s1) * r1, cy + (ry * c1 + uy * s1) * r1, cz + (rz * c1 + uz * s1) * r1,
					cx + (rx * c1 + ux * s1) * r0, cy + (ry * c1 + uy * s1) * r0, cz + (rz * c1 + uz * s1) * r0,
					argb, in, out, out, in);
		}
	}

	private void blackHole(Esp esp, double cx, double cy, double cz, double t, double sc) {
		int hot = col(0xFFFF8A2A);
		double R = 0.42 * sc;
		hx = cx; hy = cy; hz = cz; hR = R * 1.05; holeNanos = System.nanoTime();
		// base do disco: direita da camera rolada ~11 graus; segundo eixo = cima*0.25 + fundo
		double roll = Math.toRadians(-11), cr = Math.cos(roll), sr = Math.sin(roll);
		double bx = rx * cr + ux * sr, by = ry * cr + uy * sr, bz = rz * cr + uz * sr;
		double vx = -rx * sr + ux * cr, vy = -ry * sr + uy * cr, vz = -rz * sr + uz * cr;
		ex1x = bx; ex1y = by; ex1z = bz;
		ex2x = vx * 0.3 + fx * 0.954; ex2y = vy * 0.3 + fy * 0.954; ex2z = vz * 0.3 + fz * 0.954;
		glow(esp, cx, cy, cz, R * 7.0, 0xFFB04A1E, 0.1f);
		glow(esp, cx, cy, cz, R * 3.6, hot, 0.14f);
		if (warp.get() == 1) spaceWarp(esp, cx, cy, cz, R, t);
		// lente gravitacional: o disco de tras "dobra" por cima e por baixo do buraco
		// jatos fracos ao longo do eixo do disco
		ray(esp, cx, cy, cz, Math.PI / 2 - Math.toRadians(11), R * 4.5, R * 0.4, 0xFF9FC8FF, 0.12f);
		lensArc(esp, cx, cy, cz, R * 1.04, R * 2.5, hot, 0.85f);
		lensArc(esp, cx, cy, cz, R * 1.04, R * 1.5, 0xFFFFF0D0, 0.95f);
		discBands(esp, cx, cy, cz, R, t, false, hot);
		// horizonte de eventos
		glow(esp, cx, cy, cz, R * 1.12, 0xFF000000, 1f);
		glow(esp, cx, cy, cz, R * 0.8, 0xFF000000, 1f);
		// anel de fotons
		ringBill(esp, cx, cy, cz, R * 1.0, R * 1.07, 0xFFFFF6E4, 0.95f, 0.95f, false);
		ringBill(esp, cx, cy, cz, R * 1.07, R * 1.45, 0xFFFFD79A, 0.6f, 0f, false);
		// parte da frente do disco passa na frente do buraco
		discBands(esp, cx, cy, cz, R, t, true, hot);
		// particulas espiralando no plano do disco, com rastro
		int n = 8 + amount.get() * 2;
		for (int i = 0; i < n; i++) {
			double ph = (t * 0.3 + hash(i, 9)) % 1.0;
			for (int k = 0; k < 7; k++) {
				double pk = Math.max(0, ph - k * 0.015);
				double r2 = R * (4.4 - 3.1 * pk * pk), a2 = hash(i, 8) * 6.283 + t * 1.2 + pk * 6.0;
				double[] p = disc(cx, cy, cz, r2, a2);
				double gr = R * 0.10 * (1.0 - k / 7.0);
				float al = 0.8f * (1f - k / 7f) * (float) Math.min(1.0, (1 - ph) * 4);
				glow(esp, p[0], p[1], p[2], gr, 0xFFFFE0B0, al);
			}
		}
	}

	private void bill(Esp esp, double cx, double cy, double cz, double x1, double y1, double x2, double y2, int argb, float a, float w) {
		esp.thinLine(cx + rx * x1 + ux * y1, cy + ry * x1 + uy * y1, cz + rz * x1 + uz * y1,
				cx + rx * x2 + ux * y2, cy + ry * x2 + uy * y2, cz + rz * x2 + uz * y2, argb, a, w);
	}

	/** Distorcao do espaco: grade que se aperta e gira perto do buraco, ondas se afastando e estrelas de fundo esticadas em arcos (lente). */
	private void spaceWarp(Esp esp, double cx, double cy, double cz, double R, double t) {
		final int nr = 6, nl = 20, seg = 56;
		double[] rad = new double[nr];
		for (int j = 0; j < nr; j++) rad[j] = R * 1.5 * Math.pow(1.38, j);
		int grid = 0xFF9FC4FF;
		// linhas radiais: giram em espiral perto do buraco (arrasto do espaco)
		for (int i = 0; i < nl; i++) {
			double a0 = Math.PI * 2 * i / nl;
			double px = 0, py = 0;
			for (int j = 0; j < nr; j++) {
				double ang = a0 + 1.5 * R * 1.5 / rad[j] * 2.2 - t * 0.12;
				double x = Math.cos(ang) * rad[j], y = Math.sin(ang) * rad[j];
				if (j > 0) bill(esp, cx, cy, cz, px, py, x, y, grid, 0.34f * (1f - j / (float) nr), 1.0f);
				px = x; py = y;
			}
		}
		// aneis concentricos, com onda saindo do buraco
		for (int j = 0; j < nr; j++) {
			double r = rad[j] * (1.0 + 0.035 * Math.sin(t * 2.2 - j * 0.9));
			float al = 0.38f * (1f - j / (float) nr);
			double px = r, py = 0;
			for (int k = 1; k <= seg; k++) {
				double an = Math.PI * 2 * k / seg;
				double x = Math.cos(an) * r, y = Math.sin(an) * r;
				bill(esp, cx, cy, cz, px, py, x, y, grid, al, 1.0f);
				px = x; py = y;
			}
		}
		// estrelas de fundo: imagem principal esticada em arco + imagem fraca do lado oposto (anel de Einstein)
		double e2 = (R * 1.7) * (R * 1.7);
		for (int i = 0; i < 40; i++) {
			double d = R * (2.2 + hash(i, 21) * 7.5);
			double an = hash(i, 22) * Math.PI * 2 + t * 0.05 * (i % 2 == 0 ? 1 : -1);
			double rp = 0.5 * (d + Math.sqrt(d * d + 4 * e2));
			double rm = 0.5 * (Math.sqrt(d * d + 4 * e2) - d);
			double arc = Math.min(0.9, (R * 1.7) / d * 0.55);
			float tw = (float) (0.6 + 0.4 * Math.sin(t * 2.5 + i));
			double[][] imgs = {{rp, 1.0}, {rm, 0.45}};
			for (int m = 0; m < 2; m++) {
				double r = imgs[m][0], ang = an + (m == 1 ? Math.PI : 0);
				double px = Math.cos(ang - arc) * r, py = Math.sin(ang - arc) * r;
				for (int k = 1; k <= 5; k++) {
					double a2 = ang - arc + 2 * arc * k / 5.0;
					double x = Math.cos(a2) * r, y = Math.sin(a2) * r;
					bill(esp, cx, cy, cz, px, py, x, y, 0xFFE8F1FF, (float) (0.85 * imgs[m][1]) * tw, 1.6f);
					px = x; py = y;
				}
			}
		}
	}

	private void pulsar(Esp esp, double cx, double cy, double cz, double t, double sc) {
		int c = col(0xFF7FC8FF);
		double ph = t * 1.3, R = 0.3 * sc;
		glow(esp, cx, cy, cz, R * 6.0, c, 0.2f);
		// anel de pulso saindo da bola
		for (int i = 0; i < 3; i++) {
			double p = (t * 0.4 + i / 3.0) % 1.0;
			double r = R * (1.5 + p * 8.0);
			ringBill(esp, cx, cy, cz, r, r + R * 0.1, c, (float) (0.6 * (1 - p)), (float) (0.6 * (1 - p)), false);
		}
		// feixes de farol: dois opostos varrendo (giram no plano da tela), mais um par fraco cruzado
		ray(esp, cx, cy, cz, ph, R * 15, R * 0.55, c, 0.9f);
		ray(esp, cx, cy, cz, ph, R * 15, R * 1.3, c, 0.25f);
		ray(esp, cx, cy, cz, ph, R * 6, R * 0.25, 0xFFFFFFFF, 0.9f);
		ray(esp, cx, cy, cz, ph + Math.PI / 2, R * 9, R * 0.3, c, 0.3f);
		// a bola
		disk(esp, cx, cy, cz, R, 0xFF3C8CE0, 1f);
		glow(esp, cx, cy, cz, R * 0.97, 0xFF9FD8FF, 1f);
		glow(esp, cx - rx * R * 0.18 + ux * R * 0.22, cy - ry * R * 0.18 + uy * R * 0.22, cz - rz * R * 0.18 + uz * R * 0.22, R * 0.55, 0xFFFFFFFF, 0.95f);
	}

	private void threeMarias(Esp esp, double cx, double cy, double cz, double t, double sc) {
		int base = col(0xFF8FC4FF);
		double u = 0.05 * 110.0 * sc;
		double tilt = Math.toRadians(32), ct = Math.cos(tilt), st = Math.sin(tilt);
		double sp = u * 2.3;
		double bx = rx * ct + ux * st, by = ry * ct + uy * st, bz = rz * ct + uz * st;     // ao longo do cinturao
		double nx = -ux * ct + rx * st, ny = -uy * ct + ry * st, nz = -uz * ct + rz * st;   // perpendicular
		double[][] p3 = new double[3][];
		for (int i = -1; i <= 1; i++) p3[i + 1] = new double[] {cx + bx * sp * i, cy + by * sp * i, cz + bz * sp * i};
		// nebulosa suave em volta do cinturao (rosa e azul)
		int[] neb = {0xFFFF6FA8, 0xFF6FA8FF, 0xFFB06FFF, 0xFFFF8FC0, 0xFF6FD0FF};
		for (int i = 0; i < 9; i++) {
			double k = (hash(i, 31) - 0.5) * 2.6, off = (hash(i, 32) - 0.5) * u * 2.2;
			glow(esp, cx + bx * sp * k + nx * off, cy + by * sp * k + ny * off, cz + bz * sp * k + nz * off,
					u * (1.6 + hash(i, 33) * 1.6), neb[i % 5], 0.09f + 0.04f * (float) Math.sin(t * 0.6 + i));
		}
		double[] ss = {0.62, 0.8, 0.55};
		int[] sc3 = {0xFFA8CCFF, 0xFFC4DCFF, 0xFF98C0FF};
		for (int i = 0; i < 3; i++) {
			double tw = 0.9 + 0.1 * Math.sin(t * 2.4 + i * 2.0);
			double s = ss[i] * u * tw;
			double[] p = p3[i];
			glow(esp, p[0], p[1], p[2], s * 4.5, base, 0.16f);
			double ph = (t * 0.35 + i * 0.33) % 1.0; // onda de brilho que se expande
			ringBill(esp, p[0], p[1], p[2], s * (1.0 + ph * 2.2), s * (1.03 + ph * 2.2), base, (float) (0.5 * (1 - ph)), (float) (0.5 * (1 - ph)), false);
			flare(esp, p[0], p[1], p[2], s, col(sc3[i]), 1f, t * 0.05 * (i % 2 == 0 ? 1 : -1));
			ray(esp, p[0], p[1], p[2], tilt, s * 7.0, s * 0.05, 0xFFFFFFFF, 0.55f);
			ray(esp, p[0], p[1], p[2], tilt + Math.PI / 2, s * 5.0, s * 0.05, 0xFFFFFFFF, 0.45f);
		}
		// estrelinhas fracas em volta
		for (int i = 0; i < 6 + amount.get(); i++) {
			double k = (hash(i, 11) - 0.5) * 6.0, off = (hash(i, 12) - 0.5) * u * 7;
			double tw = 0.5 + 0.5 * Math.sin(t * 3 + i);
			flare(esp, cx + bx * sp * k + nx * off, cy + by * sp * k + ny * off, cz + bz * sp * k + nz * off, 0.08 * u * (0.6 + tw), 0xFF9AA6BC, (float) (0.3 + 0.25 * tw), 0);
		}
	}
}
