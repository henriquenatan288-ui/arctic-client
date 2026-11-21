package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	private static void set(String mod, String prefix, int v) {
		var m = com.witch.module.ModuleManager.byName(mod);
		for (var s : m.settings()) if (s.name.startsWith(prefix)) { ((com.witch.setting.IntSetting) s).set(v); return; }
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> {
				for (String n : new String[] {"Particles 3D", "Elytra ESP", "Spotify", "Kill Effect", "Coords HUD"}) {
					var m = com.witch.module.ModuleManager.byName(n);
					if (m != null && !m.isEnabled()) m.toggle();
				}
				try {
					var f = Class.forName("com.witch.gui.MeteorGuiScreen").getDeclaredField("EXPANDED");
					f.setAccessible(true);
					((java.util.Set<Object>) f.get(null)).add(com.witch.module.ModuleManager.byName("Particles 3D"));
				} catch (Throwable t) { System.out.println("[WDBG] expand fail " + t); }
				mc.gui.setScreen(com.witch.gui.ClickGui.create());
			});
			context.waitTicks(40);
			context.takeScreenshot("gui-padrao");
			context.runOnClient(mc -> { set("GUI Settings", "Liquid glass", 1); });
			context.waitTicks(30);
			context.takeScreenshot("gui-liquid-glass");
			context.waitTicks(20);
			context.takeScreenshot("gui-liquid-glass-2");
		}
	}
}
