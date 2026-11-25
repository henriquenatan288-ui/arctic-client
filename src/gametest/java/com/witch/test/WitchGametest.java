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
			srv.runCommand("give @p minecraft:netherite_sword");
			srv.runCommand("give @p minecraft:diamond_sword");
			context.runOnClient(mc -> {
				var m = com.witch.module.ModuleManager.byName("Galaxy");
				for (var st : m.settings()) if (st.name.startsWith("Hotbar")) ((com.witch.setting.IntSetting) st).set(1);
				if (!m.isEnabled()) m.toggle();
				if (mc.player != null) mc.player.setXRot(15f);
			});
			context.waitTicks(30);
			context.takeScreenshot("espada-1p");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("espada-3p");
			context.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("t")) {
					@Override
					public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float d) {
						var a = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
						a.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "galaxy_netherite_sword"));
						var b = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
						b.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath("witch", "galaxy_diamond_sword"));
						g.fill(0, 0, this.width, this.height, 0xFF30343C);
						for (int k = 0; k < 2; k++) {
							g.pose().pushMatrix();
							g.pose().translate(40f + k * 190f, 60f);
							g.pose().scale(11f, 11f);
							g.item(k == 0 ? a : b, 0, 0);
							g.pose().popMatrix();
						}
					}
				});
			});
			context.waitTicks(20);
			context.takeScreenshot("espada-inv");
		}
	}
}
