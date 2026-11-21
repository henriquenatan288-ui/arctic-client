package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Cena de revisao: particulas 2D/3D, Elytra ESP, kill effect (po) e intro. */
public class WitchGametest implements FabricClientGameTest {
	private static void on(String n) { var m = com.witch.module.ModuleManager.byName(n); if (m != null && !m.isEnabled()) m.toggle(); }
	private static void off(String n) { var m = com.witch.module.ModuleManager.byName(n); if (m != null && m.isEnabled()) m.toggle(); }
	private static void set(String mod, String prefix, int v) {
		var m = com.witch.module.ModuleManager.byName(mod);
		if (m == null) { System.out.println("[WDBG] sem modulo " + mod); return; }
		for (var s : m.settings()) if (s.name.startsWith(prefix)) { ((com.witch.setting.IntSetting) s).set(v); return; }
		System.out.println("[WDBG] sem opcao " + mod + "/" + prefix);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.takeScreenshot("00-boot");
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0f); mc.player.setXRot(8f); });

			// ---- Particles 3D ----
			context.runOnClient(mc -> { on("Particles 3D"); });
			int[][] cfg = {{2, 1, 0}, {6, 2, 0}, {5, 3, 1}, {3, 4, 1}, {7, 1, 0}, {1, 2, 1}};
			int n = 0;
			for (int[] c : cfg) {
				final int[] cc = c;
				context.runOnClient(mc -> { set("Particles 3D", "Shape", cc[0]); set("Particles 3D", "Path", cc[1]); set("Particles 3D", "Rainbow", cc[2]); set("Particles 3D", "Size", 6); });
				context.waitTicks(30);
				context.takeScreenshot("p3d-" + (++n) + "-shape" + c[0] + "-path" + c[1]);
			}
			context.runOnClient(mc -> off("Particles 3D"));

			// ---- Particles 2D ----
			context.runOnClient(mc -> on("Particles 2D"));
			int[][] c2 = {{5, 1, 0}, {6, 2, 0}, {7, 3, 1}, {8, 4, 0}, {1, 1, 0}};
			n = 0;
			for (int[] c : c2) {
				final int[] cc = c;
				context.runOnClient(mc -> { set("Particles 2D", "Shape", cc[0]); set("Particles 2D", "Pattern", cc[1]); set("Particles 2D", "Rainbow", cc[2]);
					com.witch.module.CardParticlesModule.burst(mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ() + 3.0, 28); });
				context.waitTicks(14);
				context.takeScreenshot("p2d-" + (++n) + "-shape" + c[0] + "-pat" + c[1]);
			}
			context.runOnClient(mc -> off("Particles 2D"));

			// ---- Elytra ESP ----
			var srv = sp.getServer();
			srv.runCommand("setblock ~3 ~1 ~6 minecraft:stone");
			srv.runCommand("summon minecraft:item_frame ~3 ~1 ~5 {Facing:3b,Item:{id:\"minecraft:elytra\",count:1}}");
			srv.runCommand("summon minecraft:glow_item_frame ~-4 ~2 ~14 {Facing:3b,Item:{id:\"minecraft:elytra\",count:1}}");
			srv.runCommand("summon minecraft:item_frame ~0 ~1 ~8 {Facing:3b,Item:{id:\"minecraft:diamond\",count:1}}");
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setYRot(0f); mc.player.setXRot(5f); });
			context.waitTicks(10);
			context.runOnClient(mc -> on("Elytra ESP"));
			context.waitTicks(30);
			context.takeScreenshot("elytra-esp-1");
			context.runOnClient(mc -> mc.player.setYRot(160f));
			context.waitTicks(4);
			context.takeScreenshot("elytra-esp-2-behind");
			context.runOnClient(mc -> { mc.player.setYRot(0f); mc.player.setXRot(-30f); });
			context.waitTicks(4);
			context.takeScreenshot("elytra-esp-3-up");
			context.runOnClient(mc -> off("Elytra ESP"));

			// ---- Kill effect: po ----
			context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0f); mc.player.setXRot(10f); });
			srv.runCommand("summon minecraft:zombie ~ ~ ~4 {NoAI:1b,PersistenceRequired:1b,Silent:1b,equipment:{head:{id:\"minecraft:iron_helmet\",count:1}}}");
			srv.runCommand("summon minecraft:villager ~2 ~ ~4 {NoAI:1b,Silent:1b}");
			context.waitTicks(10);
			context.takeScreenshot("kill-0-before");
			context.runOnClient(mc -> {
				var ke = com.witch.module.ModuleManager.byName("Kill Effect");
				try {
					var m = ke.getClass().getDeclaredMethod("startDust", net.minecraft.world.entity.LivingEntity.class);
					m.setAccessible(true);
					for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.Mob mob) m.invoke(ke, mob);
				} catch (Throwable t) { System.out.println("[WDBG] startDust fail " + t); }
				on("Kill Effect");
			});
			srv.runCommand("kill @e[type=minecraft:zombie]");
			srv.runCommand("kill @e[type=minecraft:villager]");
			for (int t : new int[] {1, 1, 1, 2, 2}) {
				context.waitTicks(t);
				context.takeScreenshot("kill-" + t + "-" + System.nanoTime() % 1000);
			}
		}
	}
}
