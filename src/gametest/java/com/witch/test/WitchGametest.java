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
			var srv = sp.getServer();
			srv.runCommand("time set noon");
			srv.runCommand("gamemode creative @p");
			srv.runCommand("give @p minecraft:netherite_sword");
			context.runOnClient(mc -> { if (mc.player != null) mc.player.setXRot(20f); });
			context.waitTicks(20);
			context.takeScreenshot("golpe-0-parado");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Straight Swing");
				if (!m.isEnabled()) m.toggle();
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			});
			context.takeScreenshot("golpe-1-reto");
			context.waitTicks(1);
			context.takeScreenshot("golpe-2-reto");
			context.waitTicks(10);
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Straight Swing");
				if (m.isEnabled()) m.toggle();
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			});
			context.takeScreenshot("golpe-3-vanilla");
		}
	}
}
