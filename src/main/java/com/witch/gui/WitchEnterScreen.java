package com.witch.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Tela "Press to enter" minimalista: "W I T C H" com letras finas que se abrem devagar, uma linha de 1px e uma
 * dica discreta. Ao entrar, a marca sobe e encolhe suavemente ate o topo, onde o titulo a recebe.
 */
public class WitchEnterScreen extends Screen {
	private static boolean shown = false;
	/** Quando o logo chegou (ms); o titulo usa para sair do preto. 0 = nao veio da tela de entrada. */
	public static long handoffMs = 0L;

	private static final long TEXT_OUT = 300L, MOVE_DELAY = 150L, MOVE = 1100L, HOLD = 80L;
	/** Posicao final da marca (igual no titulo): escala e espacamento. */
	static final float END_SCALE = 0.62f, END_SPACING = 7f;
	static final int END_CY = 20;
	private static final float START_SCALE = 1.9f, START_SPACING = 16f;

	private final long born = System.currentTimeMillis();
	private long startMs = 0L;
	private boolean done = false;

	public WitchEnterScreen() {
		super(Component.literal("Arctic Client"));
		shown = true;
	}

	/** So na primeira vez que o titulo aparece em cada sessao. */
	public static boolean pending() {
		return !shown;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void enter() {
		if (startMs == 0L) startMs = System.currentTimeMillis();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		enter();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE) return true;
		enter();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		long now = System.currentTimeMillis();
		int w = this.width, h = this.height;
		double sec = now / 1000.0;
		float age = (now - born) / 1000f;

		Minimal.background(g, w, h, sec, true);
		float fadeIn = Mth.clamp(age / 0.8f, 0f, 1f);
		g.fill(0, 0, w, h, RenderUtil.alpha(Minimal.light() ? 0xFFF1F1F2 : 0xFF000000, 1f - fadeIn)); // sai do preto

		float tm = startMs == 0L ? -1f : (now - startMs - MOVE_DELAY) / (float) MOVE;
		float e = tm <= 0f ? 0f : Minimal.easeInOut(tm);
		float textA = startMs == 0L ? Minimal.easeOut(age / 1.2f) : Mth.clamp(1f - (now - startMs) / (float) TEXT_OUT, 0f, 1f);

		// abertura lenta do espacamento + entrada letra por letra
		float open = Minimal.easeOut(age / 2.2f);
		float spacing = Mth.lerp(open, START_SPACING + 12f, START_SPACING);
		float scale = Mth.lerp(e, START_SCALE, END_SCALE);
		spacing = Mth.lerp(e, spacing, END_SPACING);
		float cx = w / 2f;
		float cy0 = h / 2f - 10f;
		float cy = Mth.lerp(e, cy0, END_CY);
		float reveal = startMs == 0L ? age * 5f : 999f;
		Minimal.wordmark(g, cx, cy, scale, spacing, 1f, reveal);

		if (textA > 0.01f) {
			int fh = FontUtil.height();
			int ly = Math.round(cy0 + FontUtil.height() * START_SCALE / 2f + 16f);
			Minimal.hairline(g, w / 2, ly, Math.round(150f * Minimal.easeOut((age - 0.6f) / 1.2f)), textA);
			float subA = Minimal.easeOut((age - 1.0f) / 1.0f) * textA;
			Minimal.spaced(g, "C L I E N T", cx, ly + 10, 0.8f, 3f, Minimal.dim(), subA, true);
			float breathe = 0.35f + 0.35f * (0.5f + 0.5f * (float) Math.sin(sec * 1.6));
			float hintA = Minimal.easeOut((age - 2.0f) / 1.0f) * textA * breathe;
			Minimal.spaced(g, "PRESS ANY KEY", cx, h - 46 - fh, 0.8f, 3f, Minimal.dim(), hintA, true);
		}

		if (!done && startMs != 0L && now - startMs > MOVE_DELAY + MOVE + HOLD) {
			done = true;
			handoffMs = now;
			Minecraft.getInstance().gui.setScreen(new WitchTitleScreen());
		}
	}
}
