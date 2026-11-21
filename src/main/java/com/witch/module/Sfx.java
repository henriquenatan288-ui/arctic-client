package com.witch.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/** Efeitos sonoros proprios do Witch (assets/witch/sounds/*.ogg, gerados por tools/gen_sounds.py). */
public final class Sfx {
	private static final Map<String, SoundEvent> CACHE = new HashMap<>();

	private Sfx() {}

	private static SoundEvent ev(String name) {
		return CACHE.computeIfAbsent(name, n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("witch", n)));
	}

	/** Toca um som do Witch como som de interface (nao depende de posicao). */
	public static void play(String name, float pitch, float volume) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || volume <= 0f) return;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ev(name), pitch, Math.min(1f, volume)));
		} catch (Throwable ignored) { }
	}

	/** Liga/desliga de modulo, no estilo escolhido em GUI Settings. */
	public static void toggle(boolean on) {
		if (!GuiSettingsModule.sounds()) return;
		play("toggle_" + (on ? "on" : "off") + "_" + GuiSettingsModule.soundStyle(), 1f, GuiSettingsModule.soundVolume());
	}

	public static void alert(float pitch) {
		play("alert", pitch, GuiSettingsModule.soundVolume());
	}
}
