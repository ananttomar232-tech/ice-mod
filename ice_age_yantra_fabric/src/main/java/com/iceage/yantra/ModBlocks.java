package com.iceage.yantra;

import com.iceage.yantra.block.ExitPortalBlock;
import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.block.ShowcaseBlock;
import com.iceage.yantra.block.PortalBlockEntity;
import com.iceage.yantra.block.ShowcaseBlockEntity;
import com.iceage.yantra.block.YantraBlockEntity;
import com.iceage.yantra.block.YantraBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    private static FabricBlockSettings yantra() {
        return FabricBlockSettings.create().mapColor(MapColor.LIGHT_BLUE).strength(2.0f).nonOpaque().sounds(BlockSoundGroup.METAL);
    }

    public static final Block GOLA_MACHINE = register("gola_machine",
            new YantraBlock(yantra(), Tier.GOLA, Block.createCuboidShape(1, 0, 1, 15, 16, 15)));
    public static final Block FARRATA_FAN = register("farrata_fan",
            new YantraBlock(yantra(), Tier.FAN, Block.createCuboidShape(2, 0, 2, 14, 16, 14)));
    public static final Block RETRO_FRIDGE = register("retro_fridge",
            new YantraBlock(yantra(), Tier.FRIDGE, Block.createCuboidShape(2, 0, 1, 14, 16, 15)));
    public static final Block AC_UNIT = register("ac_unit",
            new YantraBlock(yantra(), Tier.AC, Block.createCuboidShape(0, 0, 1, 16, 15, 11)));
    public static final Block VILLAGE_PORTAL = register("village_portal", new PortalBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(2.0f).nonOpaque().noCollision().luminance(s -> 11).sounds(BlockSoundGroup.GLASS)));
    public static final Block EXIT_PORTAL = register("exit_portal", new ExitPortalBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(-1.0f, 3600000.0f).nonOpaque().noCollision().luminance(s -> 12).dropsNothing()
            .sounds(BlockSoundGroup.GLASS)));
    public static final Block SHOWCASE = register("showcase", new ShowcaseBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(-1.0f, 3600000.0f).nonOpaque().luminance(s -> 8).dropsNothing().sounds(BlockSoundGroup.GLASS)));

    public static BlockEntityType<ShowcaseBlockEntity> SHOWCASE_BE;
    public static BlockEntityType<YantraBlockEntity> YANTRA_BE;
    public static BlockEntityType<PortalBlockEntity> PORTAL_BE;

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(IceAgeYantra.MODID, name), block);
    }

    public static Block forTier(Tier t) {
        return switch (t) {
            case GOLA -> GOLA_MACHINE;
            case FAN -> FARRATA_FAN;
            case FRIDGE -> RETRO_FRIDGE;
            case AC -> AC_UNIT;
            case VAN -> SHOWCASE;
        };
    }

    public static void init() {
        YANTRA_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "yantra"),
                FabricBlockEntityTypeBuilder.create(YantraBlockEntity::new, GOLA_MACHINE, FARRATA_FAN, RETRO_FRIDGE, AC_UNIT).build());
        PORTAL_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "portal"),
                FabricBlockEntityTypeBuilder.create(PortalBlockEntity::new, VILLAGE_PORTAL, EXIT_PORTAL).build());
        SHOWCASE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "showcase"),
                FabricBlockEntityTypeBuilder.create(ShowcaseBlockEntity::new, SHOWCASE).build());
    }
}
