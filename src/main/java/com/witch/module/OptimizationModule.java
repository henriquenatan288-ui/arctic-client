package com.witch.module;

import com.witch.setting.IntSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Otimizacao: baixa as opcoes de video que mais pesam com muitas chunks (particulas, mistura de bioma,
 * sombras, nuvens, distancia de entidades...). Guarda os valores originais num arquivo de backup e devolve
 * tudo ao desligar (ou ao abrir o jogo de novo, se ele fechou com o modulo ligado).
 * Nao mexe na distancia de renderizacao.
 */
public class OptimizationModule extends Module {
	public final IntSetting level = add(new IntSetting("Nivel (1 leve, 2 medio, 3 maximo)", 1, 3, 2, ""));

	private boolean applied;
	private int appliedLevel;
	private final Properties orig = new Properties();

	public OptimizationModule() {
		super("Optimization", Category.UTILITY);
	}

	private static Path backupFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("witch-optimization-backup.properties");
	}

	@Override
	public void onTick() {
		if (Minecraft.getInstance().options == null) return;
		if (!applied || appliedLevel != level.get()) apply();
	}

	@Override
	protected void onDisable() {
		restore(true);
		applied = false;
	}

	private void capture(Options o) {
		orig.setProperty("particles", o.particles().get().name());
		orig.setProperty("clouds", o.cloudStatus().get().name());
		orig.setProperty("shadows", String.valueOf(o.entityShadows().get()));
		orig.setProperty("fade", String.valueOf(o.chunkSectionFadeInTime().get()));
		orig.setProperty("blend", String.valueOf(o.biomeBlendRadius().get()));
		orig.setProperty("weather", String.valueOf(o.weatherRadius().get()));
		orig.setProperty("entdist", String.valueOf(o.entityDistanceScaling().get()));
		orig.setProperty("ao", String.valueOf(o.ambientOcclusion().get()));
		try (OutputStream out = Files.newOutputStream(backupFile())) {
			orig.store(out, "Valores originais (Witch Optimization)");
		} catch (Throwable ignored) {
		}
	}

	private void loadBackup() {
		orig.clear();
		try (InputStream in = Files.newInputStream(backupFile())) {
			orig.load(in);
		} catch (Throwable ignored) {
		}
	}

	/** Devolve os valores originais; deleteBackup = true quando o modulo foi desligado de vez. */
	private void restore(boolean deleteBackup) {
		Options o = Minecraft.getInstance().options;
		if (o == null) return;
		if (orig.isEmpty()) loadBackup();
		if (orig.isEmpty()) return;
		try {
			o.particles().set(ParticleStatus.valueOf(orig.getProperty("particles")));
			o.cloudStatus().set(CloudStatus.valueOf(orig.getProperty("clouds")));
			o.entityShadows().set(Boolean.parseBoolean(orig.getProperty("shadows")));
			o.chunkSectionFadeInTime().set(Double.parseDouble(orig.getProperty("fade")));
			o.biomeBlendRadius().set(Integer.parseInt(orig.getProperty("blend")));
			o.weatherRadius().set(Integer.parseInt(orig.getProperty("weather")));
			o.entityDistanceScaling().set(Double.parseDouble(orig.getProperty("entdist")));
			o.ambientOcclusion().set(Boolean.parseBoolean(orig.getProperty("ao")));
		} catch (Throwable t) {
			System.out.println("[Witch] optimization: nao consegui restaurar: " + t);
		}
		if (deleteBackup) {
			orig.clear();
			try {
				Files.deleteIfExists(backupFile());
			} catch (Throwable ignored) {
			}
		}
	}

	private void apply() {
		Options o = Minecraft.getInstance().options;
		if (o == null) return;
		if (!applied) {
			loadBackup(); // se o jogo fechou ligado, os originais estao no backup (nao nas opcoes atuais)
			if (orig.isEmpty()) capture(o);
		} else {
			restore(false); // troca de nivel: volta ao original antes de aplicar o novo
		}
		int lv = level.get();
		try {
			o.entityShadows().set(false);
			o.cloudStatus().set(CloudStatus.OFF);
			o.chunkSectionFadeInTime().set(0.0);
			o.particles().set(lv >= 3 ? ParticleStatus.MINIMAL : ParticleStatus.DECREASED);
			if (lv >= 2) {
				o.biomeBlendRadius().set(lv >= 3 ? 0 : 1);
				o.weatherRadius().set(lv >= 3 ? 3 : 5);
				o.entityDistanceScaling().set(lv >= 3 ? 0.5 : 0.75);
			}
			if (lv >= 3) o.ambientOcclusion().set(false);
		} catch (Throwable t) {
			System.out.println("[Witch] optimization: " + t);
		}
		applied = true;
		appliedLevel = lv;
	}
}
