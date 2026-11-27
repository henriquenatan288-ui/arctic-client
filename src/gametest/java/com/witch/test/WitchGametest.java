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
			srv.runCommand("time set day");
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setXRot(55f); });
			context.waitTicks(10);
			context.takeScreenshot("outline-vanilla");
			context.runOnClient(mc -> { var m = com.witch.module.ModuleManager.byName("Block Outline"); if (!m.isEnabled()) m.toggle(); });
			context.waitTicks(8);
			context.takeScreenshot("outline-tema");
			context.runOnClient(mc -> set("Block Outline", "Cor (1", 3));
			context.waitTicks(8);
			context.takeScreenshot("outline-arcoiris");
			context.runOnClient(mc -> { set("Block Outline", "Cor (1", 4); set("Block Outline", "Espessura", 8); });
			context.waitTicks(8);
			context.takeScreenshot("outline-pulso-grosso");
		}
	}
}
