package com.iceage.yantra;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The frost particle family of the Forge version (same textures). */
public final class ModParticles {
    public static final DefaultParticleType FROST_SWIRL = reg("frost_swirl");
    public static final DefaultParticleType ICE_SHARD = reg("ice_shard");
    public static final DefaultParticleType SNOWFLAKE = reg("snowflake");
    public static final DefaultParticleType FROST_MIST = reg("frost_mist");
    public static final DefaultParticleType SPARKLE = reg("sparkle");

    private ModParticles() {
    }

    private static DefaultParticleType reg(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, new Identifier(IceAgeYantra.MODID, name), FabricParticleTypes.simple());
    }

    public static void init() {
    }
}
