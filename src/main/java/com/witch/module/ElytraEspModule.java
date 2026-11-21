package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Procura item frames (normais e luminosos) com elytra dentro e marca com uma linha vermelha ate eles, atraves das paredes. */
public class ElytraEspModule extends Module implements EspSource {
	private final IntSetting range = add(new IntSetting("Range", 16, 256, 128, ""));
	private final IntSetting tracer = add(new IntSetting("Linha (0/1)", 0, 1, 1, ""));
	private final IntSetting box = add(new IntSetting("Caixa (0/1)", 0, 1, 1, ""));
	private final IntSetting fill = add(new IntSetting("Fill opacity", 0, 40, 12, "%"));
	private final ColorSetting color = add(new ColorSetting("Color", 5));

	public ElytraEspModule() {
		super("Elytra ESP", Category.VISUALS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void collect(Esp esp) {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		var me = mc.player;
		if (level == null || me == null) return;

		double r2 = (double) range.get() * range.get();
		int argb = color.get();
		float fillA = fill.get() / 100f;
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof ItemFrame f) || f.isRemoved()) continue;
			if (!f.getItem().is(Items.ELYTRA)) continue;
			double dx = f.getX() - me.getX(), dy = f.getY() - me.getY(), dz = f.getZ() - me.getZ();
			if (dx * dx + dy * dy + dz * dz > r2) continue;
			AABB b = f.getBoundingBox();
			if (box.get() == 1) {
				double g = 0.06;
				esp.box(b.minX - g, b.minY - g, b.minZ - g, b.maxX + g, b.maxY + g, b.maxZ + g, argb, fillA);
			}
			if (tracer.get() == 1) esp.tracer(b.getCenter().x, b.getCenter().y, b.getCenter().z, argb, 0.9f, 1.6f);
		}
	}
}
