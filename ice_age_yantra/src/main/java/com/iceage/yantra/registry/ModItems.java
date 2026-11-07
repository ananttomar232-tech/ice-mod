package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.item.AcRemoteItem;
import com.iceage.yantra.item.IceCreamVanItem;
import com.iceage.yantra.item.YantraBlockItem;
import com.iceage.yantra.item.YantraShopPortalItem;
import com.iceage.yantra.progress.Tier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IceAgeYantra.MODID);

    public static final RegistryObject<Item> YANTRA_SHOP_PORTAL = ITEMS.register("yantra_shop_portal",
            () -> new YantraShopPortalItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> AC_REMOTE = ITEMS.register("ac_remote",
            () -> new AcRemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> GOLA_MACHINE = ITEMS.register("gola_machine",
            () -> new YantraBlockItem(ModBlocks.GOLA_MACHINE.get(), Tier.GOLA, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FARRATA_FAN = ITEMS.register("farrata_fan",
            () -> new YantraBlockItem(ModBlocks.FARRATA_FAN.get(), Tier.FAN, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> RETRO_FRIDGE = ITEMS.register("retro_fridge",
            () -> new YantraBlockItem.Tall(ModBlocks.RETRO_FRIDGE.get(), Tier.FRIDGE, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> AC_UNIT = ITEMS.register("ac_unit",
            () -> new YantraBlockItem(ModBlocks.AC_UNIT.get(), Tier.AC, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ICE_CREAM_VAN = ITEMS.register("ice_cream_van",
            () -> new IceCreamVanItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), false));

    public static final RegistryObject<Item> ICE_PORTAL = ITEMS.register("ice_portal",
            () -> new DoubleHighBlockItem(ModBlocks.ICE_PORTAL.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SHOWCASE_PEDESTAL = ITEMS.register("showcase_pedestal",
            () -> new BlockItem(ModBlocks.SHOWCASE_PEDESTAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> ICICLE_CHANDELIER = ITEMS.register("icicle_chandelier",
            () -> new BlockItem(ModBlocks.ICICLE_CHANDELIER.get(), new Item.Properties()));
    public static final RegistryObject<Item> PERMAFROST_ICE = block("permafrost_ice", ModBlocks.PERMAFROST_ICE);
    public static final RegistryObject<Item> FROST_BRICKS = block("frost_bricks", ModBlocks.FROST_BRICKS);
    public static final RegistryObject<Item> FROSTED_GLASS = block("frosted_glass", ModBlocks.FROSTED_GLASS);
    public static final RegistryObject<Item> GLACIER_TILES = block("glacier_tiles", ModBlocks.GLACIER_TILES);
    public static final RegistryObject<Item> AURORA_ICE = block("aurora_ice", ModBlocks.AURORA_ICE);
    public static final RegistryObject<Item> PACKED_FROST = block("packed_frost", ModBlocks.PACKED_FROST);
    public static final RegistryObject<Item> ICE_PILLAR = block("ice_pillar", ModBlocks.ICE_PILLAR);
    public static final RegistryObject<Item> SNOW_FLOOR = block("snow_floor", ModBlocks.SNOW_FLOOR);

    public static final RegistryObject<Item> CHILL_VILLAGER_SPAWN_EGG = ITEMS.register("chill_villager_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CHILL_VILLAGER, 0x5AA8E8, 0xE8F7FF, new Item.Properties()));
    public static final RegistryObject<Item> ICE_CREAM_VAN_SPAWN_EGG = ITEMS.register("ice_cream_van_spawn_egg",
            () -> new IceCreamVanItem(new Item.Properties().stacksTo(16), true));

    private static RegistryObject<Item> block(String name, RegistryObject<? extends net.minecraft.world.level.block.Block> b) {
        return ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
    }

    private ModItems() {
    }
}
