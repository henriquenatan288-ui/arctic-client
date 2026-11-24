package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	private static void set(String mod, String prefix, int v) {
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
			srv.runCommand("time set noon");
			srv.runCommand("gamemode creative @p");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Wings");
				if (!m.isEnabled()) m.toggle();
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				if (mc.player != null) mc.player.setXRot(10f);
			});
			context.waitTicks(40);
			context.takeScreenshot("asas-costas");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("asas-frente");
			context.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				set("Wings", "Visual", 1);
				set("Wings", "Tamanho", 8);
				if (mc.player != null) mc.player.setYRot(60f);
			});
			context.waitTicks(30);
			context.takeScreenshot("asas-galaxy");
		}
	}
}
