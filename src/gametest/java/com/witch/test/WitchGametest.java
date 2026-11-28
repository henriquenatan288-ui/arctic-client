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
			// ---- pulsar em 3 instantes (noite)
			srv.runCommand("time set midnight");
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setXRot(10f);
				var m = com.witch.module.ModuleManager.byName("Cosmic FX"); set("Cosmic FX", "Efeito", 3); set("Cosmic FX", "Shader de tela (0", 0); if (!m.isEnabled()) m.toggle(); });
			for (int i = 0; i < 3; i++) { context.waitTicks(i == 0 ? 20 : 14); context.takeScreenshot("pulsar-" + i); }
			context.runOnClient(mc -> { var sm = com.witch.module.ModuleManager.byName("Spotify"); if (!sm.isEnabled()) sm.toggle(); com.witch.module.SpotifyModule.previewMode = 1; });
			context.waitTicks(10);
			context.takeScreenshot("widget-local");
			context.runOnClient(mc -> com.witch.module.SpotifyModule.previewMode = 2);
			context.waitTicks(6);
			context.takeScreenshot("widget-pausado");
			context.runOnClient(mc -> com.witch.module.SpotifyModule.previewMode = 0);
			srv.runCommand("time set day");
			context.runOnClient(mc -> { var m = com.witch.module.ModuleManager.byName("Cosmic FX"); set("Cosmic FX", "Efeito", 2); set("Cosmic FX", "Shader de tela (0", 1); set("Cosmic FX", "Shader - intensidade", 10); mc.player.setXRot(-5f); if (!m.isEnabled()) m.toggle(); });
			context.waitTicks(40);
			context.takeScreenshot("shader10-buraco-dia");
			context.runOnClient(mc -> set("Cosmic FX", "Efeito", 1));
			context.waitTicks(25);
			context.takeScreenshot("shader10-estrelas-dia");
		}
	}
}
