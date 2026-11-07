package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IceAgeYantra.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ice_age_yantra"))
            .icon(() -> new ItemStack(ModItems.YANTRA_SHOP_PORTAL.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.YANTRA_SHOP_PORTAL.get());
                out.accept(ModItems.GOLA_MACHINE.get());
                out.accept(ModItems.FARRATA_FAN.get());
                out.accept(ModItems.RETRO_FRIDGE.get());
                out.accept(ModItems.AC_UNIT.get());
                out.accept(ModItems.AC_REMOTE.get());
                out.accept(ModItems.ICE_CREAM_VAN.get());
                out.accept(ModItems.ICE_PORTAL.get());
                out.accept(ModItems.CHILL_VILLAGER_SPAWN_EGG.get());
                out.accept(ModItems.ICE_CREAM_VAN_SPAWN_EGG.get());
                out.accept(ModItems.SHOWCASE_PEDESTAL.get());
                out.accept(ModItems.ICICLE_CHANDELIER.get());
                out.accept(ModItems.PERMAFROST_ICE.get());
                out.accept(ModItems.FROST_BRICKS.get());
                out.accept(ModItems.FROSTED_GLASS.get());
                out.accept(ModItems.GLACIER_TILES.get());
                out.accept(ModItems.AURORA_ICE.get());
                out.accept(ModItems.PACKED_FROST.get());
                out.accept(ModItems.ICE_PILLAR.get());
                out.accept(ModItems.SNOW_FLOOR.get());
            })
            .build());

    private ModCreativeTab() {
    }
}
