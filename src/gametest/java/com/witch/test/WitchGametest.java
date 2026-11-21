package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	private static void setStatic(Class<?> c, String f, Object v) throws Exception { var fl = c.getDeclaredField(f); fl.setAccessible(true); fl.set(null, v); }

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Spotify");
				if (m != null && !m.isEnabled()) m.toggle();
				try {
					var dir = java.nio.file.Path.of("musicas"); java.nio.file.Files.createDirectories(dir);
					var img = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
					for (int y = 0; y < 200; y++) for (int x = 0; x < 200; x++) img.setRGB(x, y, ((x * 255 / 200) << 16) | ((y * 255 / 200) << 8) | 160);
					javax.imageio.ImageIO.write(img, "png", dir.resolve("Test Artist - Minha Musica Teste.png").toFile());
					setStatic(com.witch.module.SpotifyModule.class, "stop", true);
					setStatic(com.witch.music.MusicPlayer.class, "tracks", java.util.List.of(dir.resolve("Test Artist - Minha Musica Teste.mp3")));
					com.witch.music.MusicPlayer.current = 0;
					setStatic(com.witch.music.MusicPlayer.class, "active", true);
					setStatic(com.witch.music.MusicPlayer.class, "duration", 215f);
				} catch (Throwable t) { System.out.println("[WDBG] fail " + t); }
			});
			context.waitTicks(40);
			context.takeScreenshot("local-with-cover");
		}
	}
}
