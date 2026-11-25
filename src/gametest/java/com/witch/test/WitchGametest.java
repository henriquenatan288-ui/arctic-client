package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
			context.waitTicks(15);
			context.takeScreenshot("pausa");
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.gui.screen(), mc.options, true)));
			context.waitTicks(15);
			context.takeScreenshot("opcoes");
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.SoundOptionsScreen(mc.gui.screen(), mc.options)));
			context.waitTicks(15);
			context.takeScreenshot("som-sliders");
		}
	}
}
