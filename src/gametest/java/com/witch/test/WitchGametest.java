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
			srv.runCommand("time set noon");
			srv.runCommand("gamemode creative @p");
			srv.runCommand("execute at @p run fill ~2 ~ ~5 ~3 ~3 ~6 minecraft:red_concrete");
			srv.runCommand("execute at @p run fill ~-3 ~ ~6 ~-3 ~2 ~6 minecraft:lime_concrete");
			srv.runCommand("execute at @p run setblock ~0 ~ ~7 minecraft:glowstone");
			srv.runCommand("execute at @p run summon minecraft:villager ~-1 ~ ~4 {NoAI:1b}");
			srv.runCommand("execute at @p run summon minecraft:armor_stand ~1 ~ ~3");
			srv.runCommand("execute at @p run summon minecraft:iron_golem ~4 ~ ~9 {NoAI:1b}");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Mirror Floor");
				if (m != null && !m.isEnabled()) m.toggle();
				if (m != null) for (var st : m.settings()) { if (st.name.startsWith("Strength")) ((com.witch.setting.IntSetting) st).set(10); }
				if (mc.player != null) mc.player.setXRot(14f);
			});
			context.waitTicks(80);
			context.takeScreenshot("espelho-ssr-a");
			context.runOnClient(mc -> { if (mc.player != null) mc.player.setXRot(6f); });
			context.waitTicks(10);
			context.takeScreenshot("espelho-ssr-b");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Mirror Floor");
				for (var s : m.settings()) if (s.name.startsWith("Reflexo de tela")) ((com.witch.setting.IntSetting) s).set(0);
				if (mc.player != null) mc.player.setXRot(14f);
			});
			context.waitTicks(20);
			context.takeScreenshot("espelho-simples");
		}
	}
}
