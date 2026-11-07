package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, IceAgeYantra.MODID);

    public static final RegistryObject<SimpleParticleType> FROST_SWIRL = PARTICLES.register("frost_swirl", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> ICE_SHARD = PARTICLES.register("ice_shard", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SNOWFLAKE = PARTICLES.register("snowflake", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> FROST_MIST = PARTICLES.register("frost_mist", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SPARKLE = PARTICLES.register("sparkle", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
