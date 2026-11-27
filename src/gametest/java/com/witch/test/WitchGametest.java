package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	static float ti, prog, exit = 0f, alpha = 1f;

	static class Preview extends net.minecraft.client.gui.screens.Screen {
		Preview() { super(net.minecraft.network.chat.Component.empty()); }

		@Override
		public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float delta) {
			com.witch.gui.LoadingAnim.snap(prog);
			com.witch.gui.LoadingAnim.draw(g, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight(), ti, prog, alpha, exit, 100.0 + ti);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		context.runOnClient(mc -> mc.gui.setScreen(new Preview()));
		float[][] k = {{0.15f, 0f, 0f, 1f}, {0.6f, 0.05f, 0f, 1f}, {1.1f, 0.2f, 0f, 1f}, {1.8f, 0.45f, 0f, 1f}, {3.2f, 0.8f, 0f, 1f}, {4.5f, 1f, 0.55f, 0.75f}};
		for (int i = 0; i < k.length; i++) {
			final int n = i;
			context.runOnClient(mc -> { ti = k[n][0]; prog = k[n][1]; exit = k[n][2]; alpha = k[n][3]; });
			context.waitTicks(3);
			context.takeScreenshot("load-" + i);
		}
	}
}
