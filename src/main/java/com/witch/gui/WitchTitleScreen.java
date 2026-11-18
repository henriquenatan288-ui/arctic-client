package com.witch.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Tela inicial minimalista do Witch Client: fundo suave, marca fina no topo, menu em texto e o personagem 3D. */
public class WitchTitleScreen extends Screen {
	private static final String[] LABELS = {"SINGLEPLAYER", "MULTIPLAYER", "OPTIONS", "CLIENT", "QUIT"};
	private static final String[] SUBS = {"Your worlds, offline and private", "Servers and LAN games", "Video, audio and controls", "Witch modules and HUD", "Close the game"};
	private long last = 0L;
	private final long born = System.currentTimeMillis();
	private int menuX, menuY, itemW = 170, itemH = 32;
	private final float[] hoverAnim = new float[5];

	public WitchTitleScreen() {
		super(Component.literal("Witch Client"));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		long nowNs = System.nanoTime();
		float speed = last == 0 ? 1f : Math.min(3f, (nowNs - last) / 16_666_667f);
		last = nowNs;
		long now = System.currentTimeMillis();
		double sec = now / 1000.0; // mesma base de tempo da tela de entrada (o fundo continua sem pulo)
		int w = this.width;
		int h = this.height;
		float age = (now - born) / 1000f;

		Minimal.background(g, w, h, sec, true);

		// marca no topo (vindo da entrada ela ja esta aqui; senao aparece com fade)
		float wa = WitchEnterScreen.handoffMs != 0L ? 1f : Minimal.easeOut(age / 0.8f);
		Minimal.wordmark(g, w / 2f, WitchEnterScreen.END_CY, WitchEnterScreen.END_SCALE, WitchEnterScreen.END_SPACING, wa, 999f);

		menuX = Math.max(44, w / 11);
		itemH = Math.max(22, Math.min(32, (h - 28 - 58 - 22) / 5)); // telas baixas: itens mais juntos pra nao cobrir o rodape
		menuY = Math.max(86, h / 2 - itemH * 5 / 2 + 12);

		float ia = Minimal.easeOut((age - 0.1f) / 0.7f);
		Minimal.spaced(g, "WELCOME BACK", menuX, menuY - 58 + (1f - ia) * 6f, 0.7f, 3f, Minimal.dim(), ia, false);
		g.pose().pushMatrix();
		g.pose().translate((float) menuX, menuY - 44 + (1f - ia) * 6f);
		g.pose().scale(1.25f, 1.25f);
		FontUtil.draw(g, TitleSkin.nick(), 0, 0, RenderUtil.alpha(Minimal.fg(), ia));
		g.pose().popMatrix();

		if (!model3d(g, w, h, sec, mx, my)) { /* sem modelo 3D: fica so o menu */ }

		// guia vertical fina atras do menu
		int gx = menuX - 18;
		g.fill(gx, menuY - 2, gx + 1, menuY + itemH * LABELS.length - 8, RenderUtil.alpha(Minimal.line(), 0.12f * ia));

		for (int i = 0; i < LABELS.length; i++) {
			int y = menuY + i * itemH;
			boolean hover = RenderUtil.inside(mx, my, menuX - 8, y, itemW + 16, itemH - 4);
			hoverAnim[i] += ((hover ? 1f : 0f) - hoverAnim[i]) * Math.min(1f, 0.18f * speed);
			float ha = hoverAnim[i];
			float ent = Minimal.easeOut((now - born - 250L - i * 90L) / 600f);
			float shift = (1f - ent) * 14f + 5f * ha;
			int fg = RenderUtil.mix(Minimal.dim(), Minimal.fg(), ha);
			// trecho aceso da guia
			int segH = Math.round((itemH - 10) * ha);
			if (segH > 1) g.fill(gx, y + (itemH - 8) / 2 - segH / 2, gx + 1, y + (itemH - 8) / 2 + segH / 2, RenderUtil.alpha(Minimal.accent(), ha * ent));
			Minimal.spaced(g, String.format("%02d", i + 1), menuX + shift, y + 3, 0.7f, 1f, Minimal.faint(), ent, false);
			Minimal.spaced(g, LABELS[i], menuX + 22 + shift, y + 2, 0.9f, 3.2f, fg, ent, false);
			if (ha > 0.02f) {
				Minimal.spaced(g, SUBS[i], menuX + 22 + shift, y + 15, 0.7f, 0.5f, Minimal.dim(), ha * ent, false);
			}
		}

		float fa = Minimal.easeOut((age - 0.6f) / 0.8f);
		Minimal.spaced(g, "WITCH CLIENT  /  MINECRAFT 26.2", 14, h - 16, 0.62f, 2f, Minimal.faint(), fa, false);
		String credit = "MADE BY BISTECK";
		Minimal.spaced(g, credit, w - 14 - Minimal.spacedWidth(credit, 0.62f, 2f), h - 16, 0.62f, 2f, Minimal.faint(), fa, false);
	}

	private static long walkStart = 0L;
	private float userYaw = 0f;

	private static final float DAB_HEAD_X = 0.55f, DAB_HEAD_Y = -0.6f;
	private static final float DAB_R_X = -1.5f, DAB_R_Y = 0.9f, DAB_R_Z = 0.1f;
	private static final float DAB_L_X = -0.2f, DAB_L_Y = 0f, DAB_L_Z = -2.2f;

	private java.util.Map<String, java.util.List<net.minecraft.client.model.geom.ModelPart>> dancers = null;

	private java.util.List<net.minecraft.client.model.geom.ModelPart> dp(String n) {
		return dancers.getOrDefault(n, java.util.List.of());
	}

	/** Anda ate a posicao (pernas/bracos balancando) e depois faz um Dab. */
	private static final boolean DAB = false; // o Dab parecia travado olhando pra baixo
	private static final float WALK_YAW = -85f, FRONT_YAW = 20f;
	private static java.lang.reflect.Field rotY;

	private void setYaw(float deg) {
		try {
			if (rotY == null) { rotY = PlayerSkinWidget.class.getDeclaredField("rotationY"); rotY.setAccessible(true); }
			rotY.setFloat(skinWidget, deg);
		} catch (Throwable ignored) { }
	}

	private void wave(double ph0, double amp, boolean arrived) {
		try {
			if (dancers == null) {
				dancers = new java.util.HashMap<>();
				String[] names = {"body", "jacket", "left_leg", "left_pants", "right_leg", "right_pants",
						"left_arm", "left_sleeve", "right_arm", "right_sleeve", "head", "hat"};
				for (java.lang.reflect.Field f : PlayerSkinWidget.class.getDeclaredFields()) {
					if (!net.minecraft.client.model.Model.class.isAssignableFrom(f.getType())) continue;
					f.setAccessible(true);
					Object o = f.get(skinWidget);
					if (!(o instanceof net.minecraft.client.model.Model mdl)) continue;
					for (String n : names) {
						try { dancers.computeIfAbsent(n, k -> new java.util.ArrayList<>()).add(mdl.root().getChild(n)); }
						catch (Throwable ignored) { }
					}
				}
			}
			for (var l : dancers.values()) for (var part : l) part.resetPose();
			if (!arrived) {
				// andando: pernas e bracos balancam em oposicao
				float ph = (float) ph0, sw = (float) Math.sin(ph) * 0.8f * (float) amp;
				for (var q : dp("right_leg")) q.xRot = sw;
				for (var q : dp("right_pants")) q.xRot = sw;
				for (var q : dp("left_leg")) q.xRot = -sw;
				for (var q : dp("left_pants")) q.xRot = -sw;
				for (var q : dp("right_arm")) q.xRot = -sw * 0.8f;
				for (var q : dp("right_sleeve")) q.xRot = -sw * 0.8f;
				for (var q : dp("left_arm")) q.xRot = sw * 0.8f;
				for (var q : dp("left_sleeve")) q.xRot = sw * 0.8f;
				return;
			}
			if (!DAB) return;
			double t = ph0;
			double wt = t - 2.0;
			if (wt < 0 || wt > 4.2) return;
			float in = (float) Math.min(1.0, wt / 0.18), out = (float) Math.min(1.0, (4.2 - wt) / 0.25);
			float b = Math.min(in, out);
			b = b * b * (3 - 2 * b);
			float pop = (float) (Math.sin(Math.min(wt, 0.3) / 0.3 * Math.PI) * 0.08);
			float hx = DAB_HEAD_X * b, hy = DAB_HEAD_Y * b;
			for (var q : dp("head")) { q.xRot = hx; q.yRot = hy; q.zRot = 0.12f * b; }
			for (var q : dp("hat")) { q.xRot = hx; q.yRot = hy; q.zRot = 0.12f * b; }
			for (var q : dp("body")) { q.yRot = -0.25f * b; q.xRot = 0.08f * b + pop; }
			for (var q : dp("jacket")) { q.yRot = -0.25f * b; q.xRot = 0.08f * b + pop; }
			for (var q : dp("right_arm")) { q.xRot = DAB_R_X * b; q.yRot = DAB_R_Y * b; q.zRot = DAB_R_Z * b; }
			for (var q : dp("right_sleeve")) { q.xRot = DAB_R_X * b; q.yRot = DAB_R_Y * b; q.zRot = DAB_R_Z * b; }
			for (var q : dp("left_arm")) { q.xRot = DAB_L_X * b; q.yRot = DAB_L_Y * b; q.zRot = DAB_L_Z * b; }
			for (var q : dp("left_sleeve")) { q.xRot = DAB_L_X * b; q.yRot = DAB_L_Y * b; q.zRot = DAB_L_Z * b; }
			for (var q : dp("left_leg")) { q.zRot = -0.08f * b; }
			for (var q : dp("left_pants")) { q.zRot = -0.08f * b; }
			for (var q : dp("right_leg")) { q.zRot = 0.08f * b; }
			for (var q : dp("right_pants")) { q.zRot = 0.08f * b; }
		} catch (Throwable ignored) {
			dancers = new java.util.HashMap<>();
		}
	}

	private PlayerSkinWidget skinWidget;
	private boolean skinWidgetFailed = false;
	private int skinWidgetH = -1;

	/** Modelo 3D de verdade (o mesmo do jogo): desliza da esquerda, para na direita e mostra o nick. */
	private boolean model3d(GuiGraphicsExtractor g, int w, int h, double sec, int mx, int my) {
		if (skinWidgetFailed) return false;
		try {
			int mh = Math.max(90, Math.min(h - 70, (int) (h * 0.62)));
			int mw = (int) (mh * 0.62);
			if (skinWidget == null || skinWidgetH != mh) {
				skinWidget = new PlayerSkinWidget(mw, mh, Minecraft.getInstance().getEntityModels(), TitleSkin::skin);
				skinWidgetH = mh;
			}
			if (walkStart == 0L) walkStart = System.nanoTime();
			double t = (System.nanoTime() - walkStart) / 1_000_000_000.0;
			double k = Math.min(1.0, t / 1.6);
			double ease = 1.0 - Math.pow(1.0 - k, 2);
			int stopX = (int) (w * 0.68) - mw / 2;
			int x = (int) (w + (stopX - w) * ease);
			int y = h - 24 - mh;
			skinWidget.setX(x);
			skinWidget.setY(y);

			int groundY = h - 20;
			int cx = x + mw / 2;
			RenderUtil.softShadow(g, cx - mw / 2, groundY - 3, mw, 6, 12, RenderUtil.alpha(0xFF000000, Minimal.light() ? 0.22f : 0.55f));
			Minimal.hairline(g, cx, groundY, mw * 2, 0.9f);
			double travel = Math.max(1, w - stopX);
				double cycles = Math.max(2.0, travel / (mw * 0.5));
				wave(ease * cycles * Math.PI * 2.0, k >= 1.0 ? 0.0 : Math.min(1.0, (1.0 - k) / 0.15), k >= 1.0);
				float turn = (float) Math.max(0.0, Math.min(1.0, (k - 0.62) / 0.38));
				turn = turn * turn * (3 - 2 * turn);
				setYaw(WALK_YAW + (FRONT_YAW - WALK_YAW) * turn);
			skinWidget.extractRenderState(g, mx, my, 0f);
			if (k >= 1.0) {
				float na = (float) Math.min(1.0, Math.max(0.0, (t - 1.8) / 0.4));
				if (na > 0f) {
					String nick = TitleSkin.nick().toUpperCase();
					int ny = y - 16 + Math.round((1f - na) * 6f);
					Minimal.spaced(g, nick, cx, ny, 0.8f, 3f, Minimal.dim(), na, true);
				}
			}
			return true;
		} catch (Throwable e) {
			skinWidgetFailed = true;
			return false;
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (click.x() > this.width * 0.55) {
			userYaw += (float) (offsetX * 0.03);
			if (skinWidget != null) {
				try { skinWidget.mouseDragged(click, offsetX, offsetY); } catch (Throwable ignored) { }
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			for (int i = 0; i < LABELS.length; i++) {
				int y = menuY + i * itemH;
				if (RenderUtil.inside(click.x(), click.y(), menuX - 8, y, itemW + 16, itemH - 4)) {
					Minecraft mc = Minecraft.getInstance();
					switch (i) {
						case 0 -> mc.gui.setScreen(new SelectWorldScreen(this));
						case 1 -> mc.gui.setScreen(new JoinMultiplayerScreen(this));
						case 2 -> mc.gui.setScreen(new OptionsScreen(this, mc.options, false));
						case 3 -> mc.gui.setScreen(ClickGui.create());
						default -> mc.stop();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}
}
