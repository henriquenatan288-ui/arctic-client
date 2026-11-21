package com.witch.module;

import com.witch.render.EspRenderer.Esp;
import com.witch.render.EspSource;
import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Procura item frames (normais e luminosos) com elytra dentro: marca com caixa pulsante, linha ate eles e um feixe
 * vertical (da para ver de longe, atraves das paredes) e toca um alarme proprio quando acha uma nova.
 */
public class ElytraEspModule extends Module implements EspSource {
	private final IntSetting range = add(new IntSetting("Range", 16, 256, 128, ""));
	private final IntSetting tracer = add(new IntSetting("Linha (0/1)", 0, 1, 1, ""));
	private final IntSetting box = add(new IntSetting("Caixa (0/1)", 0, 1, 1, ""));
	private final IntSetting beam = add(new IntSetting("Feixe vertical (0/1)", 0, 1, 1, ""));
	private final IntSetting fill = add(new IntSetting("Fill opacity", 0, 40, 12, "%"));
	private final IntSetting alarm = add(new IntSetting("Alarme (0/1)", 0, 1, 1, ""));
	private final IntSetting alarmVol = add(new IntSetting("Volume do alarme", 1, 100, 80, "%"));
	private final IntSetting chat = add(new IntSetting("Aviso no chat (0/1)", 0, 1, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Color", 5));

	private final List<AABB> found = new ArrayList<>();
	private final Set<Integer> seen = new HashSet<>();
	private Object lastLevel;

	public ElytraEspModule() {
		super("Elytra ESP", Category.VISUALS);
	}

	@Override
	public boolean active() {
		return isEnabled();
	}

	@Override
	public void onDisable() {
		found.clear();
		seen.clear();
	}

	/** Varre as molduras 20x por segundo: monta a lista e avisa (som + chat) quando aparece uma elytra nova. */
	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		var me = mc.player;
		if (level == null || me == null) {
			found.clear();
			return;
		}
		if (level != lastLevel) { // mundo/servidor novo: esquece o que ja avisou
			lastLevel = level;
			seen.clear();
		}
		double r2 = (double) range.get() * range.get();
		found.clear();
		Set<Integer> now = new HashSet<>();
		AABB newest = null;
		double newestD = 0;
		int fresh = 0;
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof ItemFrame f) || f.isRemoved() || !f.getItem().is(Items.ELYTRA)) continue;
			double dx = f.getX() - me.getX(), dy = f.getY() - me.getY(), dz = f.getZ() - me.getZ();
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r2) continue;
			found.add(f.getBoundingBox());
			now.add(f.getId());
			if (seen.add(f.getId())) {
				fresh++;
				if (newest == null || d2 < newestD) { newest = f.getBoundingBox(); newestD = d2; }
			}
		}
		seen.retainAll(now); // se a moldura sumiu ou esvaziou e voltar, avisa de novo
		if (fresh > 0) {
			if (alarm.get() == 1) Sfx.play("elytra_alarm", 1.0f, alarmVol.get() / 100f);
			if (chat.get() == 1 && newest != null) {
				var c = newest.getCenter();
				me.sendSystemMessage(Component.literal("§c[Elytra ESP] §fElytra em moldura! §7x " + Math.round(c.x)
						+ " y " + Math.round(c.y) + " z " + Math.round(c.z) + " (" + Math.round(Math.sqrt(newestD)) + " blocos)"
						+ (fresh > 1 ? " +" + (fresh - 1) + " outras" : "")));
			}
		}
	}

	@Override
	public void collect(Esp esp) {
		if (found.isEmpty()) return;
		int argb = color.get();
		float fillA = fill.get() / 100f;
		double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 1.0e9 * 4.0);
		var me = Minecraft.getInstance().player;
		for (AABB b : found) {
			var c = b.getCenter();
			if (box.get() == 1) {
				double g = 0.05 + 0.05 * pulse;
				esp.box(b.minX - g, b.minY - g, b.minZ - g, b.maxX + g, b.maxY + g, b.maxZ + g, argb, fillA * (0.6f + 0.8f * (float) pulse));
			}
			if (beam.get() == 1) { // feixe vertical: marca o lugar mesmo longe, e em cima/embaixo da parede
				esp.thinLine(c.x, b.minY - 30, c.z, c.x, b.maxY + 60, c.z, argb, 0.55f, 1.6f);
			}
			if (tracer.get() == 1) {
				float w = 1.6f;
				if (me != null) { // mais grossa quanto mais perto
					double d = Math.sqrt(me.distanceToSqr(c.x, c.y, c.z));
					w = (float) Math.max(1.2, Math.min(3.0, 3.2 - d / 60.0));
				}
				esp.tracer(c.x, c.y, c.z, argb, 0.95f, w);
			}
		}
	}
}
