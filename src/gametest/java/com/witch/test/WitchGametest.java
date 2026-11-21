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
					setStatic(com.witch.module.SpotifyModule.class, "stop", true);
					setStatic(com.witch.module.SpotifyModule.class, "track", "Daft Punk - Harder, Better, Faster, Stronger");
					setStatic(com.witch.module.SpotifyModule.class, "running", true);
					setStatic(com.witch.module.SpotifyModule.class, "playing", true);
				} catch (Throwable t) { System.out.println("[WDBG] spotify fail " + t); }
			});
			context.waitTicks(10);
			context.takeScreenshot("spotify-playing");
			context.runOnClient(mc -> { try { setStatic(com.witch.module.SpotifyModule.class, "track", "Um Artista Com Nome Bem Muito Longo Mesmo - Uma Musica Com Titulo Gigante Para Testar Corte"); } catch (Throwable t) {} });
			context.waitTicks(4);
			context.takeScreenshot("spotify-long");
			context.runOnClient(mc -> { try { setStatic(com.witch.module.SpotifyModule.class, "playing", false); setStatic(com.witch.module.SpotifyModule.class, "track", null); } catch (Throwable t) {} });
			context.waitTicks(4);
			context.takeScreenshot("spotify-paused");
		}
	}
}
