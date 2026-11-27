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
			srv.runCommand("gamemode creative @p");
			srv.runCommand("time set midnight");
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setXRot(10f); });
			context.runOnClient(mc -> { var m = com.witch.module.ModuleManager.byName("Cosmic FX"); set("Cosmic FX", "Efeito", 2); set("Cosmic FX", "Shader de tela (0", 1); if (!m.isEnabled()) m.toggle(); });
			context.waitTicks(40);
			context.takeScreenshot("cosmic-shader-on");
			context.runOnClient(mc -> set("Cosmic FX", "Shader de tela (0", 0));
			context.waitTicks(15);
			context.takeScreenshot("cosmic-shader-off");
			context.runOnClient(mc -> { set("Cosmic FX", "Shader de tela (0", 1); set("Cosmic FX", "Efeito", 1); });
			context.waitTicks(30);
			context.takeScreenshot("cosmic-estrelas-shader");
		}
	}
}
