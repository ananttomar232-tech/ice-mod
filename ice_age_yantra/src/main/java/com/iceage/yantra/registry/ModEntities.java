package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.entity.ChillVillagerEntity;
import com.iceage.yantra.entity.IceCreamTruckEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, IceAgeYantra.MODID);

    public static final RegistryObject<EntityType<IceCreamTruckEntity>> ICE_CREAM_TRUCK = ENTITIES.register("ice_cream_truck",
            () -> EntityType.Builder.<IceCreamTruckEntity>of(IceCreamTruckEntity::new, MobCategory.MISC)
                    .sized(2.0f, 2.1f).clientTrackingRange(12).updateInterval(1).fireImmune()
                    .build(IceAgeYantra.id("ice_cream_truck").toString()));

    public static final RegistryObject<EntityType<ChillVillagerEntity>> CHILL_VILLAGER = ENTITIES.register("chill_villager",
            () -> EntityType.Builder.<ChillVillagerEntity>of(ChillVillagerEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f).clientTrackingRange(10)
                    .build(IceAgeYantra.id("chill_villager").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CHILL_VILLAGER.get(), ChillVillagerEntity.createAttributes().build());
    }

    private ModEntities() {
    }
}
