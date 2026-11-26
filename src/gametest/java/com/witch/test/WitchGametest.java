package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	static void set(String mod, String prefix, int v) {
		var m = com.witch.module.ModuleManager.byName(mod);
		for (var s : m.settings()) if (s.name.startsWith(prefix)) ((com.witch.setting.IntSetting) s).set(v);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			var srv = sp.getServer();
			srv.runCommand("time set midnight");
			srv.runCommand("gamemode creative @p");
			srv.runCommand("give @p netherite_sword");
			context.runOnClient(mc -> {
				var k = com.witch.module.ModuleManager.byName("Katana 3D");
				if (!k.isEnabled()) k.toggle();
				mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				mc.player.setXRot(10f);
			});
			context.waitTicks(15);
			context.takeScreenshot("katana-normal");
			for (int f = 1; f <= 2; f++) {
				final int fv = f;
				context.runOnClient(mc -> set("Katana 3D", "Fogo", fv));
				context.waitTicks(25);
				context.takeScreenshot("katana-fogo-" + f);
			}
			context.runOnClient(mc -> { set("Katana 3D", "Fogo", 1); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
			context.waitTicks(25);
			context.takeScreenshot("katana-fogo-3a");
			context.runOnClient(mc -> { set("Katana 3D", "Fogo", 0); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setXRot(35f); });
			context.waitTicks(10);
			for (int t = 0; t < 3; t++) {
				context.runOnClient(mc -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
				context.waitTicks(2 + t * 3);
				context.takeScreenshot("golpe-" + t);
				context.waitTicks(15);
			}
		}
	}
}
