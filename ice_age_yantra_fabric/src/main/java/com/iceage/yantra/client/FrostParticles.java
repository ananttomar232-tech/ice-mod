package com.iceage.yantra.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/** Frost particle family: vortex swirls, crushed-ice shards, snowflakes, freezing fog and sparkles (ported from Forge). */
public final class FrostParticles {
    private FrostParticles() {
    }

    public enum Kind { SWIRL, SHARD, SNOW, MIST, SPARKLE }

    public static class Frost extends SpriteBillboardParticle {
        private final SpriteProvider sprites;
        private final Kind kind;
        private final float spin;
        private final float baseSize;
        private final boolean glow;

        Frost(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites, Kind kind) {
            super(world, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.kind = kind;
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
            this.spin = (random.nextFloat() - 0.5f) * 0.3f;
            switch (kind) {
                case SWIRL -> {
                    maxAge = 18 + random.nextInt(14);
                    scale = 0.12f + random.nextFloat() * 0.14f;
                    velocityMultiplier = 0.94f;
                    gravityStrength = 0f;
                    glow = true;
                    setSpriteForAge(sprites);
                }
                case SHARD -> {
                    maxAge = 18 + random.nextInt(22);
                    scale = 0.05f + random.nextFloat() * 0.07f;
                    velocityMultiplier = 0.96f;
                    gravityStrength = 0.9f;
                    glow = false;
                    setSprite(sprites);
                }
                case SNOW -> {
                    maxAge = 40 + random.nextInt(50);
                    scale = 0.045f + random.nextFloat() * 0.06f;
                    velocityMultiplier = 0.93f;
                    gravityStrength = 0.03f;
                    glow = false;
                    setSprite(sprites);
                }
                case MIST -> {
                    maxAge = 40 + random.nextInt(40);
                    scale = 0.45f + random.nextFloat() * 0.6f;
                    velocityMultiplier = 0.95f;
                    gravityStrength = 0.004f;
                    glow = false;
                    alpha = 0f;
                    setSprite(sprites);
                }
                default -> {
                    maxAge = 10 + random.nextInt(14);
                    scale = 0.06f + random.nextFloat() * 0.08f;
                    velocityMultiplier = 0.9f;
                    gravityStrength = 0f;
                    glow = true;
                    setSpriteForAge(sprites);
                }
            }
            baseSize = scale;
            collidesWithWorld = kind == Kind.SHARD || kind == Kind.SNOW;
            angle = random.nextFloat() * MathHelper.TAU;
            prevAngle = angle;
        }

        @Override
        public void tick() {
            super.tick();
            prevAngle = angle;
            float life = (float) age / maxAge;
            switch (kind) {
                case SWIRL, SPARKLE -> {
                    setSpriteForAge(sprites);
                    alpha = 1f - life * life;
                    angle += spin * 2f;
                }
                case SHARD -> {
                    angle += spin * 3f;
                    if (onGround) {
                        velocityX *= 0.6;
                        velocityZ *= 0.6;
                    }
                }
                case SNOW -> {
                    angle += spin;
                    velocityX += MathHelper.sin(age * 0.2f + baseSize * 100) * 0.0025;
                    velocityZ += MathHelper.cos(age * 0.17f + baseSize * 50) * 0.0025;
                    alpha = life > 0.8f ? (1f - life) * 5f : 1f;
                }
                case MIST -> {
                    angle += spin * 0.15f;
                    alpha = (life < 0.2f ? life / 0.2f : 1f - (life - 0.2f) / 0.8f) * 0.42f;
                    scale = baseSize * (1f + life * 0.9f);
                }
            }
        }

        @Override
        protected int getBrightness(float tint) {
            if (glow) return 0xF000F0;
            int base = super.getBrightness(tint);
            int bl = Math.max(base & 0xFF, 0x80);
            return (base & 0xFF0000) | bl;
        }

        @Override
        public ParticleTextureSheet getType() {
            return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static class Provider implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        private final Kind kind;

        public Provider(SpriteProvider sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            return new Frost(world, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
