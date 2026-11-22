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
			context.runOnClient(mc -> {
				try {
					Class<?> c = Class.forName("com.witch.gui.ArcticGuiScreen");
					var cur = c.getDeclaredField("current"); cur.setAccessible(true);
					cur.set(null, com.witch.module.ModuleManager.byName("Particles 3D").category);
					var ex = c.getDeclaredField("EXPANDED"); ex.setAccessible(true);
					((java.util.Set<Object>) ex.get(null)).add(com.witch.module.ModuleManager.byName("Particles 3D"));
				} catch (Throwable t) { System.out.println("[WDBG] arctic fail " + t); }
				mc.gui.setScreen(com.witch.gui.ClickGui.create());
			});
			context.waitTicks(40);
			context.takeScreenshot("arctic-ligados");
			context.runOnClient(mc -> mc.gui.setScreen(new com.witch.gui.WitchTitleScreen()));
			context.waitTicks(60);
			context.takeScreenshot("titulo");
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("m")) {
				@Override
				public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float d) {
					g.fill(0, 0, this.width, this.height, 0xFF0A0A0C);
					int w = this.width, h = this.height;
					com.witch.gui.WitchLogo.draw(g, w / 2 - 60, h / 2 - 70, 120, 1f);
					com.witch.gui.Minimal.wordmark(g, w / 2f, h / 2f + 40, 3f, 8f, 1f, 999f);
					com.witch.gui.WitchLogo.draw(g, 20, h - 40, 32, 1f);
					com.witch.gui.WitchLogo.draw(g, 70, h - 32, 16, 1f);
				}
			}));
			context.waitTicks(20);
			context.takeScreenshot("marca");
		}
	}
}
