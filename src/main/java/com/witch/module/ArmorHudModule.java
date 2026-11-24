package com.witch.module;

import com.witch.gui.FontUtil;
import com.witch.gui.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Durabilidade (%) da armadura e do item na mao, no canto direito. */
public class ArmorHudModule extends Module {
	private static ArmorHudModule instance;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final String[] NAMES = {"Helmet", "Chest", "Legs", "Boots"};

	public ArmorHudModule() {
		super("Armor HUD", Category.UTILITY);
		instance = this;
	}

	private static int pct(ItemStack st) {
		int max = st.getMaxDamage();
		return max <= 0 ? 100 : Math.max(0, 100 - st.getDamageValue() * 100 / max);
	}

	public static void drawHud(GuiGraphicsExtractor g) {
		if (instance == null || !instance.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null) return;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int h = com.witch.gui.Hud.rowH() + 1;
		int y = sh / 2 - 20 + com.witch.gui.HudLayout.dy("armor");
		int ox = com.witch.gui.HudLayout.dx("armor");
		int top = y;
		int minX = Integer.MAX_VALUE;
		int maxW = 0;
		for (int i = 0; i < 5; i++) {
			ItemStack st = i < 4 ? p.getItemBySlot(SLOTS[i]) : p.getMainHandItem();
			if (st.isEmpty() || !st.isDamageableItem()) continue;
			int v = pct(st);
			int col = v > 25 ? com.witch.gui.Hud.WHITE : 0xFFE5646C;
			String label = i < 4 ? NAMES[i] : "Hand";
			String val = v + "%";
			int bw = com.witch.gui.Hud.tw(label) + com.witch.gui.Hud.tw(val) + 18;
			int x = sw - 5 - bw + ox;
			minX = Math.min(minX, x);
			maxW = Math.max(maxW, bw);
			com.witch.gui.Hud.pill(g, x, y, bw, h, 1f);
			int fw = Math.round((bw - 12) * v / 100f);
			g.fill(x + 6, y + h - 3, x + 6 + fw, y + h - 2, RenderUtil.alpha(col, 0.6f));
			int ty = y + 2;
			com.witch.gui.Hud.text(g, label, x + 6, ty, com.witch.gui.Hud.DIM);
			com.witch.gui.Hud.text(g, val, x + bw - 6 - com.witch.gui.Hud.tw(val), ty, col);
			y += h + 2;
		}
		if (maxW > 0) com.witch.gui.HudLayout.report("armor", minX, top, maxW, y - top);
	}
}
