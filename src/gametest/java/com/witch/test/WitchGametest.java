package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	static void set(String mod, String prefix, int v) {
		var m = com.witch.module.ModuleManager.byName(mod);
		for (var s : m.settings()) if (s.name.startsWith(prefix)) ((com.witch.setting.IntSetting) s).set(v);
	}

	static void field(Class<?> c, String n, Object v) {
		try { var f = c.getDeclaredField(n); f.setAccessible(true); f.set(null, v); } catch (Throwable t) { System.out.println("[T] field " + n + ": " + t); }
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
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setXRot(10f);
				var m = com.witch.module.ModuleManager.byName("Cosmic FX"); set("Cosmic FX", "Shader de tela (0", 1); set("Cosmic FX", "Shader - intensidade", 6); if (!m.isEnabled()) m.toggle(); });
			for (int e = 2; e <= 4; e++) {
				final int ev = e;
				context.runOnClient(mc -> set("Cosmic FX", "Efeito", ev));
				context.waitTicks(30);
				context.takeScreenshot("giant-" + e);
			}
			srv.runCommand("time set day");
			context.runOnClient(mc -> set("Cosmic FX", "Efeito", 2));
			context.waitTicks(30);
			context.takeScreenshot("giant-2-dia");
		}
	}
}
