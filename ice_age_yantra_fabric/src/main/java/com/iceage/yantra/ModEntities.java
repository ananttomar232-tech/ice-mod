package com.iceage.yantra;

import com.iceage.yantra.entity.GotiyaEntity;
import com.iceage.yantra.entity.IceVanEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<IceVanEntity> ICE_VAN = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(IceAgeYantra.MODID, "ice_cream_van"),
            FabricEntityTypeBuilder.<IceVanEntity>create(SpawnGroup.MISC, IceVanEntity::new)
                    .dimensions(EntityDimensions.fixed(2.8f, 2.9f))
                    .trackRangeBlocks(160).trackedUpdateRate(1).forceTrackedVelocityUpdates(true).fireImmune().build());

    public static final EntityType<GotiyaEntity> GOTIYA = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(IceAgeYantra.MODID, "gotiya"),
            FabricEntityTypeBuilder.<GotiyaEntity>create(SpawnGroup.CREATURE, GotiyaEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(128).trackedUpdateRate(2).build());

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(GOTIYA, GotiyaEntity.createGotiyaAttributes());
    }
}
