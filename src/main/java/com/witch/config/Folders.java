package com.witch.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Pastas de conteudo do jogador ("musicas" e "placas"). Sao criadas na abertura do jogo, so se nao existirem,
 * e ficam fora do jar: trocar o mod nao apaga nem recria nada. Se a pasta do jogo nao for gravavel
 * (alguns launchers de celular), cai para a pasta config.
 */
public final class Folders {
	private Folders() {}

	private static final Map<String, Path> CACHE = new HashMap<>();

	public static synchronized Path get(String name) {
		Path c = CACHE.get(name);
		if (c != null) return c;
		Path p = FabricLoader.getInstance().getGameDir().resolve(name);
		if (!ensure(p)) {
			Path alt = FabricLoader.getInstance().getConfigDir().resolve(name);
			if (ensure(alt)) p = alt;
		}
		CACHE.put(name, p);
		return p;
	}

	private static boolean ensure(Path p) {
		try {
			Files.createDirectories(p);
			return Files.isDirectory(p) && Files.isWritable(p);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Cria as pastas e um LEIA-ME em cada (so se ainda nao existirem). */
	public static void init() {
		readme(get("musicas"), "Coloque aqui suas musicas (.mp3, .ogg, .wav). Elas aparecem na tela de musicas do Arctic e no widget Spotify.\n"
				+ "Pode trocar o mod a vontade: esta pasta e seus arquivos nao sao apagados.\n");
		readme(get("placas"), "Coloque aqui um .png ou .jpg. O Sus Solo Finder transforma a imagem em textura e desenha sobre a placa de solo\n"
				+ "(ligue 'Imagem na placa' no modulo). Se houver varios, usa o primeiro em ordem alfabetica.\n"
				+ "Pode trocar o mod a vontade: esta pasta e seus arquivos nao sao apagados.\n");
		System.out.println("[WDBG] pastas: musicas=" + get("musicas").toAbsolutePath() + " placas=" + get("placas").toAbsolutePath());
	}

	private static void readme(Path dir, String text) {
		try {
			Path f = dir.resolve("LEIA-ME.txt");
			if (!Files.exists(f)) Files.writeString(f, text);
		} catch (Throwable ignored) {
		}
	}
}
