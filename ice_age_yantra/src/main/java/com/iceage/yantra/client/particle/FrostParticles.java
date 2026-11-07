package com.iceage.yantra.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Frost particle family: vortex swirls, crushed-ice shards, snowflakes, freezing fog and sparkles. */
public final class FrostParticles {
    private FrostParticles() {
    }

    public enum Kind { SWIRL, SHARD, SNOW, MIST, SPARKLE }

    public static class Frost extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Kind kind;
        private final float spin;
        private final float baseSize;
        private final boolean glow;

        Frost(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.kind = kind;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.spin = (random.nextFloat() - 0.5f) * 0.3f;
            switch (kind) {
                case SWIRL -> {
                    lifetime = 18 + random.nextInt(14);
                    quadSize = 0.12f + random.nextFloat() * 0.14f;
                    friction = 0.94f;
                    gravity = 0f;
                    glow = true;
                    setSpriteFromAge(sprites);
                }
                case SHARD -> {
                    lifetime = 18 + random.nextInt(22);
                    quadSize = 0.05f + random.nextFloat() * 0.07f;
                    friction = 0.96f;
                    gravity = 0.9f;
                    glow = false;
                    pickSprite(sprites);
                }
                case SNOW -> {
                    lifetime = 40 + random.nextInt(50);
                    quadSize = 0.045f + random.nextFloat() * 0.06f;
                    friction = 0.93f;
                    gravity = 0.03f;
                    glow = false;
                    pickSprite(sprites);
                }
                case MIST -> {
                    lifetime = 40 + random.nextInt(40);
                    quadSize = 0.45f + random.nextFloat() * 0.6f;
                    friction = 0.95f;
                    gravity = 0.004f;
                    glow = false;
                    alpha = 0f;
                    pickSprite(sprites);
                }
                default -> {
                    lifetime = 10 + random.nextInt(14);
                    quadSize = 0.06f + random.nextFloat() * 0.08f;
                    friction = 0.9f;
                    gravity = 0f;
                    glow = true;
                    setSpriteFromAge(sprites);
                }
            }
            baseSize = quadSize;
            hasPhysics = kind == Kind.SHARD || kind == Kind.SNOW;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            float life = (float) age / lifetime;
            switch (kind) {
                case SWIRL, SPARKLE -> {
                    setSpriteFromAge(sprites);
                    alpha = 1f - life * life;
                    roll += spin * 2f;
                }
                case SHARD -> {
                    roll += spin * 3f;
                    if (onGround) {
                        xd *= 0.6;
                        zd *= 0.6;
                    }
                }
                case SNOW -> {
                    roll += spin;
                    xd += Mth.sin(age * 0.2f + baseSize * 100) * 0.0025;
                    zd += Mth.cos(age * 0.17f + baseSize * 50) * 0.0025;
                    alpha = life > 0.8f ? (1f - life) * 5f : 1f;
                }
                case MIST -> {
                    roll += spin * 0.15f;
                    alpha = (life < 0.2f ? life / 0.2f : 1f - (life - 0.2f) / 0.8f) * 0.42f;
                    quadSize = baseSize * (1f + life * 0.9f);
                }
            }
        }

        @Override
        protected int getLightColor(float pt) {
            if (glow) return 0xF000F0;
            int base = super.getLightColor(pt);
            int bl = Math.max(base & 0xFF, 0x80);
            return (base & 0xFF0000) | bl;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Frost(level, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
