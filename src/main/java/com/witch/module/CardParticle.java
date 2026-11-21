package com.witch.module;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;

/** Carta que gira (vira sobre o proprio eixo), cai devagar e some. Usa a textura do atlas de particulas. */
public class CardParticle extends SingleQuadParticle {
	private final float spin;
	private final float phase;
	private final float baseSize;
	private final boolean twinkle;

	public CardParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
			TextureAtlasSprite sprite, float size, float gravity, int life) {
		this(level, x, y, z, vx, vy, vz, sprite, size, gravity, life, 0xFFFFFFFF, false);
	}

	/** tint = cor (ARGB) que multiplica o sprite; twinkle = pulsa o tamanho (brilhos). */
	public CardParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
			TextureAtlasSprite sprite, float size, float gravity, int life, int tint, boolean twinkle) {
		super(level, x, y, z, sprite);
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.hasPhysics = false;
		this.gravity = gravity;
		this.friction = 0.94f;
		this.lifetime = life;
		this.quadSize = size;
		this.baseSize = size;
		this.twinkle = twinkle;
		this.rCol = ((tint >> 16) & 255) / 255f;
		this.gCol = ((tint >> 8) & 255) / 255f;
		this.bCol = (tint & 255) / 255f;
		this.spin = (this.random.nextFloat() * 0.35f + 0.18f) * (this.random.nextBoolean() ? 1f : -1f);
		this.phase = this.random.nextFloat() * 6.28f;
		this.roll = this.random.nextFloat() * 0.6f - 0.3f;
		this.oRoll = this.roll;
	}

	@Override
	public void tick() {
		super.tick();
		float k = this.age / (float) this.lifetime;
		this.alpha = k < 0.65f ? 1f : Math.max(0f, 1f - (k - 0.65f) / 0.35f);
		float grow = k < 0.1f ? 0.6f + 4f * k : 1f;
		if (twinkle) grow *= 0.8f + 0.25f * (float) Math.sin(this.age * 0.9f + this.phase);
		this.quadSize = this.baseSize * grow;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partial) {
		Quaternionf q = new Quaternionf(camera.rotation());
		q.rotateZ(this.roll);
		if (!this.twinkle) q.rotateY((this.age + partial) * this.spin + this.phase);
		this.extractRotatedQuad(state, camera, q, partial);
	}

	@Override
	protected Layer getLayer() {
		return Layer.bySprite(this.sprite);
	}
}
