package com.witch.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public class WitchGametest implements FabricClientGameTest {
	private static void dbg(ClientGameTestContext c, String tag) {
		c.runOnClient(mc -> {
			var p = mc.player;
			var v = p.getVehicle();
			System.out.println("[WDBG] " + tag + " player=" + String.format("%.2f,%.2f,%.2f", p.getX(), p.getY(), p.getZ())
					+ " boatClient=" + (v == null ? "null" : String.format("%.2f,%.2f,%.2f noPhys=%s", v.getX(), v.getY(), v.getZ(), v.noPhysics)));
		});
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(100);
		try (var sp = context.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			context.waitTicks(20);
			var srv = sp.getServer();
			srv.runCommand("time set noon");
			srv.runCommand("gamemode creative @p");
			// ---- katana ----
			srv.runCommand("give @p minecraft:netherite_sword");
			srv.runCommand("give @p minecraft:diamond_sword");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Katana 3D");
				if (!m.isEnabled()) m.toggle();
				mc.player.setXRot(20f);
			});
			context.waitTicks(20);
			context.takeScreenshot("katana-1p");
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("t")) {
				@Override
				public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float d) {
					g.fill(0, 0, this.width, this.height, 0xFF30343C);
					var items = new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.NETHERITE_SWORD, net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.GOLDEN_SWORD};
					for (int k = 0; k < items.length; k++) {
						g.pose().pushMatrix();
						g.pose().translate(30f + k * 130f, 70f);
						g.pose().scale(7f, 7f);
						g.item(new net.minecraft.world.item.ItemStack(items[k]), 0, 0);
						g.pose().popMatrix();
					}
				}
			}));
			context.waitTicks(15);
			context.takeScreenshot("katana-gui");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			// ---- noclip do barco ----
			srv.runCommand("execute as @p at @p run fill ~-4 ~ ~3 ~4 ~5 ~5 minecraft:stone");
			srv.runCommand("execute as @p at @p run summon minecraft:oak_boat ~ ~ ~");
			context.waitTicks(10);
			srv.runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1]");
			context.waitTicks(10);
			context.runOnClient(mc -> {
				mc.player.setXRot(0f);
				var m = com.witch.module.ModuleManager.byName("Boat Fly");
				if (!m.isEnabled()) m.toggle();
				for (var st : m.settings()) if (st.name.startsWith("No clip")) ((com.witch.setting.IntSetting) st).set(1);
			});
			context.waitTicks(10);
			dbg(context, "antes");
			net.minecraft.client.KeyMapping kUp = context.computeOnClient(mc -> mc.options.keyUp);
			context.getInput().holdKeyFor(kUp, 60);
			dbg(context, "depois-frente");
			context.takeScreenshot("noclip-parede");
			net.minecraft.client.KeyMapping kSp = context.computeOnClient(mc -> mc.options.keySprint);
			context.getInput().holdKeyFor(kSp, 40);
			dbg(context, "depois-descer");
			context.takeScreenshot("noclip-chao");
		}
	}
}
