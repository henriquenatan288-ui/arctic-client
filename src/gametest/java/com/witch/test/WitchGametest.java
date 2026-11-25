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
			srv.runCommand("give @p minecraft:diamond_sword");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Galaxy");
				if (!m.isEnabled()) m.toggle();
				if (mc.player != null) mc.player.setXRot(15f);
			});
			context.waitTicks(30);
			context.takeScreenshot("espada-1p");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("espada-3p");
			context.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			});
			context.waitTicks(20);
			context.takeScreenshot("espada-inv");
		}
	}
}
