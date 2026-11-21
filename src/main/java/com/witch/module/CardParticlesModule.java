package com.witch.module;

import com.witch.setting.ColorSetting;
import com.witch.setting.IntSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/** Particulas 2D (cartas, dinheiro, chapeu, pato, brilho, coracao, bolha, floco) ao acertar, ao pular ou flutuando. */
public class CardParticlesModule extends Module {
	private static CardParticlesModule instance;
	private static boolean hooked;
	private static final String[] SUITS = {"hearts", "diamonds", "clubs", "spades"};
	private static final RandomSource RNG = RandomSource.create();

	private final IntSetting onHit = add(new IntSetting("On hit (0/1)", 0, 1, 1, ""));
	private final IntSetting onJump = add(new IntSetting("On jump (0/1)", 0, 1, 0, ""));
	private final IntSetting ambient = add(new IntSetting("Ambient (0/1)", 0, 1, 0, ""));
	private final IntSetting shape = add(new IntSetting("Shape (1 cartas 2 dinheiro 3 chapeu 4 pato 5 brilho 6 coracao 7 bolha 8 floco)", 1, 8, 1, ""));
	private final IntSetting pattern = add(new IntSetting("Pattern (1 explosao 2 fonte 3 anel 4 espiral)", 1, 4, 1, ""));
	private final ColorSetting color = add(new ColorSetting("Cor (formas 5 a 8)", 7));
	private final IntSetting rainbow = add(new IntSetting("Rainbow (0/1)", 0, 1, 0, ""));
	private final IntSetting amount = add(new IntSetting("Amount", 1, 20, 8, ""));
	private final IntSetting size = add(new IntSetting("Size", 1, 10, 3, ""));

	private boolean wasGround = true;
	private int tickN = 0;

	public CardParticlesModule() {
		super("Particles 2D", Category.EFFECTS);
		instance = this;
		hook();
	}

	private static void hook() {
		if (hooked) return;
		hooked = true;
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && instance != null && instance.isEnabled() && instance.onHit.get() == 1
					&& player == Minecraft.getInstance().player && entity instanceof LivingEntity le) {
				burst(le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), instance.amount.get());
			}
			return InteractionResult.PASS;
		});
	}

	private static TextureAtlasSprite sprite(String suit) {
		String name = switch (instance == null ? 1 : instance.shape.get()) {
			case 2 -> "shape_money";
			case 3 -> "shape_witchhat";
			case 4 -> "shape_duck";
			case 5 -> "shape_sparkle";
			case 6 -> "shape_heart";
			case 7 -> "shape_glow";
			case 8 -> "shape_flake";
			default -> "card_" + suit;
		};
		return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES)
				.getSprite(Identifier.fromNamespaceAndPath("witch", name));
	}

	/** Cor da particula i de n: so as formas 5 a 8 (brancas) recebem cor; as artes prontas ficam como sao. */
	private static int tint(int i, int n) {
		if (instance == null || instance.shape.get() < 5) return 0xFFFFFFFF;
		if (instance.rainbow.get() == 1) {
			float h = (((System.nanoTime() / 1_000_000L) % 4000L) / 4000f + i / (float) Math.max(1, n) + RNG.nextFloat() * 0.08f) % 1f;
			return ColorSetting.hsvToRgb(h, 0.7f, 1f);
		}
		return instance.color.get();
	}

	private static void spawn(double x, double y, double z, double vx, double vy, double vz, float grav, int life, float sz, int i, int n) {
		var mc = Minecraft.getInstance();
		int sh = instance.shape.get();
		boolean twinkle = sh == 5 || sh == 7;
		mc.particleEngine.add(new CardParticle(mc.level, x, y, z, vx, vy, vz, sprite(SUITS[RNG.nextInt(4)]), sz, grav, life,
				tint(i, n), twinkle));
	}

	/** Explosao de particulas em volta de um ponto, no padrao escolhido (explosao, fonte, anel ou espiral). */
	public static void burst(double x, double y, double z, int n) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || instance == null) return;
		float sz = 0.07f * instance.size.get();
		int pat = instance.pattern.get();
		for (int i = 0; i < n; i++) {
			double a = RNG.nextDouble() * Math.PI * 2;
			switch (pat) {
				case 2 -> { // fonte: sobe em jato e cai em arco
					double sp = 0.015 + RNG.nextDouble() * 0.05;
					spawn(x, y, z, Math.cos(a) * sp, 0.26 + RNG.nextDouble() * 0.14, Math.sin(a) * sp, 0.3f, 42 + RNG.nextInt(16), sz, i, n);
				}
				case 3 -> { // anel: espalha num circulo plano, sem cair
					double ar = Math.PI * 2 * i / n;
					spawn(x, y, z, Math.cos(ar) * 0.17, 0.01, Math.sin(ar) * 0.17, 0.0f, 24 + RNG.nextInt(8), sz, i, n);
				}
				case 4 -> { // espiral: gira subindo, cada uma numa altura
					double ar = i * 2.399963; // angulo dourado
					double k = i / (double) n;
					spawn(x, y - 0.3, z, Math.cos(ar) * 0.11, 0.04 + k * 0.2, Math.sin(ar) * 0.11, -0.004f, 34 + RNG.nextInt(10), sz, i, n);
				}
				default -> {
					double sp = 0.07 + RNG.nextDouble() * 0.12;
					spawn(x, y, z, Math.cos(a) * sp, 0.08 + RNG.nextDouble() * 0.1, Math.sin(a) * sp, 0.18f, 30 + RNG.nextInt(20), sz, i, n);
				}
			}
		}
	}

	private static void floaty(double x, double y, double z) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || instance == null) return;
		float sz = 0.07f * instance.size.get();
		spawn(x, y, z, (RNG.nextDouble() - 0.5) * 0.02, 0.01, (RNG.nextDouble() - 0.5) * 0.02, -0.01f, 70 + RNG.nextInt(40), sz, RNG.nextInt(8), 8);
	}

	@Override
	public void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		boolean ground = p.onGround();
		if (onJump.get() == 1 && wasGround && !ground && p.getDeltaMovement().y > 0.2 && !p.getAbilities().flying) {
			burst(p.getX(), p.getY() + 0.2, p.getZ(), Math.max(3, amount.get() / 2));
		}
		wasGround = ground;
		if (ambient.get() == 1 && ++tickN % 6 == 0) {
			floaty(p.getX() + (RNG.nextDouble() - 0.5) * 6, p.getY() + RNG.nextDouble() * 2.5,
					p.getZ() + (RNG.nextDouble() - 0.5) * 6);
		}
	}
}
