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
			// ---- shader no maximo, de dia
			srv.runCommand("time set day");
			context.runOnClient(mc -> { set("Cosmic FX", "Efeito", 2); set("Cosmic FX", "Shader de tela (0", 1); set("Cosmic FX", "Shader - intensidade", 10); mc.player.setXRot(-5f); });
			context.waitTicks(40);
			context.takeScreenshot("shader10-buraco-dia");
			context.runOnClient(mc -> set("Cosmic FX", "Efeito", 1));
			context.waitTicks(25);
			context.takeScreenshot("shader10-estrelas-dia");
			context.runOnClient(mc -> { var m = com.witch.module.ModuleManager.byName("Cosmic FX"); if (m.isEnabled()) m.toggle(); });
			context.waitTicks(10);
			// ---- musicas falsas + player + widget
			context.runOnClient(mc -> {
				try {
					var d = java.nio.file.Path.of(mc.gameDirectory.getPath(), "musicas");
					java.nio.file.Files.createDirectories(d);
					String[] n = {"CRISE_EXISTENCIAL_-_DJ_DUARTE_MC_DAVI.mp3", "Mc_Sid_Nog_-_Sitio_do_Tio_Harry.mp3", "SE_CONSELHO_FOSSE_BOM_(+_GRAVE).mp3", "VMZ_-_Insuficiencia_Cosmica.mp3"};
					for (String s : n) java.nio.file.Files.write(d.resolve(s), new byte[] {0});
				} catch (Throwable t) { System.out.println("[T] " + t); }
				com.witch.music.MusicPlayer.refresh();
				com.witch.music.MusicPlayer.current = 1;
				field(com.witch.music.MusicPlayer.class, "active", true);
				field(com.witch.music.MusicPlayer.class, "paused", false);
				field(com.witch.music.MusicPlayer.class, "duration", 272f);
				var sm = com.witch.module.ModuleManager.byName("Spotify"); if (!sm.isEnabled()) sm.toggle();
			});
			context.waitTicks(10);
			context.takeScreenshot("widget-local");
			context.runOnClient(mc -> mc.gui.setScreen(new com.witch.gui.MusicScreen()));
			context.waitTicks(10);
			context.takeScreenshot("music-screen");
			context.runOnClient(mc -> { mc.gui.setScreen(null); field(com.witch.music.MusicPlayer.class, "paused", true); });
			context.waitTicks(8);
			context.takeScreenshot("widget-pausado");
		}
	}
}
