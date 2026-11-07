package com.iceage.yantra;

import com.iceage.yantra.item.IceVanItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item GOLA_MACHINE = block("gola_machine", ModBlocks.GOLA_MACHINE);
    public static final Item FARRATA_FAN = block("farrata_fan", ModBlocks.FARRATA_FAN);
    public static final Item RETRO_FRIDGE = block("retro_fridge", ModBlocks.RETRO_FRIDGE);
    public static final Item AC_UNIT = block("ac_unit", ModBlocks.AC_UNIT);
    public static final Item VILLAGE_PORTAL = block("village_portal", ModBlocks.VILLAGE_PORTAL);
    public static final Item ICE_CREAM_VAN = register("ice_cream_van", new IceVanItem(new FabricItemSettings().maxCount(1)));
    public static final Item GOTIYA_SPAWN_EGG = register("gotiya_spawn_egg",
            new SpawnEggItem(ModEntities.GOTIYA, 0xD8A878, 0x55E6FF, new FabricItemSettings()));

    private ModItems() {
    }

    private static Item block(String name, net.minecraft.block.Block block) {
        return register(name, new BlockItem(block, new FabricItemSettings()));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(IceAgeYantra.MODID, name), item);
    }

    public static Item forTier(Tier t) {
        return switch (t) {
            case GOLA -> GOLA_MACHINE;
            case FAN -> FARRATA_FAN;
            case FRIDGE -> RETRO_FRIDGE;
            case AC -> AC_UNIT;
            case VAN -> ICE_CREAM_VAN;
        };
    }

    public static void init() {
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(GOLA_MACHINE))
                .displayName(Text.translatable("itemGroup.ice_age_yantra"))
                .entries((ctx, entries) -> {
                    entries.add(VILLAGE_PORTAL);
                    entries.add(GOLA_MACHINE);
                    entries.add(FARRATA_FAN);
                    entries.add(RETRO_FRIDGE);
                    entries.add(AC_UNIT);
                    entries.add(ICE_CREAM_VAN);
                    entries.add(GOTIYA_SPAWN_EGG);
                }).build();
        Registry.register(Registries.ITEM_GROUP, new Identifier(IceAgeYantra.MODID, "main"), group);
    }
}
