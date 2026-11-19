package com.witch.module;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.function.Predicate;

/**
 * Varredura de blocos por secao de chunk: pula secoes so de ar ou sem nenhum bloco desejado
 * (a paleta responde sem olhar bloco a bloco). Bem mais barato que getBlockState em cada posicao.
 */
final class SectionScan {
	private SectionScan() {}

	interface Hit {
		void at(int x, int y, int z, BlockState state);
	}

	/** Uma chunk inteira entre y0 e y1 (inclusive); coordenadas absolutas no callback. Pula secoes sem nenhum bloco desejado. */
	static void chunk(ClientLevel level, int cx, int cz, int y0, int y1, Predicate<BlockState> wants, Hit hit) {
		LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return;
		var sections = chunk.getSections();
		int minSec = chunk.getMinSectionY();
		for (int i = 0; i < sections.length; i++) {
			int baseY = (minSec + i) * 16;
			if (baseY + 15 < y0 || baseY > y1) continue;
			var sec = sections[i];
			if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(wants)) continue;
			int ya = Math.max(y0, baseY), yb = Math.min(y1, baseY + 15);
			for (int x = 0; x < 16; x++) {
				for (int y = ya; y <= yb; y++) {
					for (int z = 0; z < 16; z++) {
						BlockState st = sec.getBlockState(x, y & 15, z);
						if (wants.test(st)) hit.at(cx * 16 + x, y, cz * 16 + z, st);
					}
				}
			}
		}
	}

	static void scan(ClientLevel level, int cx, int cy, int cz, int r, int vr, Predicate<BlockState> wants, Hit hit) {
		int x0 = cx - r, x1 = cx + r, z0 = cz - r, z1 = cz + r, y0 = cy - vr, y1 = cy + vr;
		for (int chx = x0 >> 4; chx <= x1 >> 4; chx++) {
			for (int chz = z0 >> 4; chz <= z1 >> 4; chz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chx, chz, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				var sections = chunk.getSections();
				int minSec = chunk.getMinSectionY();
				for (int i = 0; i < sections.length; i++) {
					var sec = sections[i];
					if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(wants)) continue;
					int baseY = (minSec + i) * 16;
					int ya = Math.max(y0, baseY), yb = Math.min(y1, baseY + 15);
					int xa = Math.max(x0, chx * 16), xb = Math.min(x1, chx * 16 + 15);
					int za = Math.max(z0, chz * 16), zb = Math.min(z1, chz * 16 + 15);
					for (int x = xa; x <= xb; x++) {
						for (int y = ya; y <= yb; y++) {
							for (int z = za; z <= zb; z++) {
								BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
								if (wants.test(st)) hit.at(x, y, z, st);
							}
						}
					}
				}
			}
		}
	}
}
