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
			context.runOnClient(mc -> mc.gui.setScreen(new com.witch.gui.MusicScreen()));
			context.waitTicks(10); context.takeScreenshot("anel-music");
			context.runOnClient(mc -> mc.gui.setScreen(new com.witch.gui.ArcticGuiScreen()));
			context.waitTicks(25); context.takeScreenshot("anel-arctic");
			context.runOnClient(mc -> mc.gui.setScreen(new com.witch.gui.ClickGuiScreen()));
			context.waitTicks(25); context.takeScreenshot("anel-click");
		}
	}
}
