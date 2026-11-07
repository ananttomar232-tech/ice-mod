package com.iceage.yantra;

import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModBlocks;
import com.iceage.yantra.registry.ModCreativeTab;
import com.iceage.yantra.registry.ModEntities;
import com.iceage.yantra.registry.ModItems;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Ice Age Yantra Mod - an escalating "freeze the world" progression built around five comedic machines
 * (Yantras). Every Yantra generates Ice Points ("blocks frozen") and switches itself off the moment the
 * owner can afford the next Yantra in the shop.
 */
@Mod(IceAgeYantra.MODID)
public class IceAgeYantra {
    public static final String MODID = "ice_age_yantra";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IceAgeYantra() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModCreativeTab.TABS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModEntities::registerAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
