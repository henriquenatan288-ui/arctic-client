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
			srv.runCommand("time set midnight");
			srv.runCommand("gamemode creative @p");
			context.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(12f);
				var m = com.witch.module.ModuleManager.byName("Cosmic FX");
				if (!m.isEnabled()) m.toggle();
			});
			String[] names = {"buraco-negro", "buraco-negro-b"};
			for (int i = 0; i < 2; i++) {
				final int st = 2;
				context.runOnClient(mc -> {
					var m = com.witch.module.ModuleManager.byName("Cosmic FX");
					for (var s : m.settings()) if (s.name.startsWith("Efeito")) ((com.witch.setting.IntSetting) s).set(st);
				});
				context.waitTicks(25);
				context.takeScreenshot("cosmic-" + names[i]);
			}
		}
	}
}
