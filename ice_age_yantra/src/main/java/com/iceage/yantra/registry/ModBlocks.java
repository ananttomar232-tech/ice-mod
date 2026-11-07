package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.AcUnitBlock;
import com.iceage.yantra.block.FarrataFanBlock;
import com.iceage.yantra.block.GolaMachineBlock;
import com.iceage.yantra.block.IcePortalBlock;
import com.iceage.yantra.block.IcicleChandelierBlock;
import com.iceage.yantra.block.PermafrostIceBlock;
import com.iceage.yantra.block.RetroFridgeBlock;
import com.iceage.yantra.block.ShopSignBlock;
import com.iceage.yantra.block.ShowcasePedestalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, IceAgeYantra.MODID);

    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 6.0f).sound(SoundType.METAL)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties frost(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.2f, 4.0f).sound(SoundType.GLASS)
                .instrument(NoteBlockInstrument.CHIME);
    }

    public static final RegistryObject<Block> GOLA_MACHINE = BLOCKS.register("gola_machine",
            () -> new GolaMachineBlock(machine(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> FARRATA_FAN = BLOCKS.register("farrata_fan",
            () -> new FarrataFanBlock(machine(MapColor.SAND)));
    public static final RegistryObject<Block> RETRO_FRIDGE = BLOCKS.register("retro_fridge",
            () -> new RetroFridgeBlock(machine(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> AC_UNIT = BLOCKS.register("ac_unit",
            () -> new AcUnitBlock(machine(MapColor.SNOW)));
    public static final RegistryObject<Block> ICE_PORTAL = BLOCKS.register("ice_portal",
            () -> new IcePortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(3.0f, 1200f)
                    .sound(SoundType.AMETHYST).noOcclusion().noCollission().lightLevel(s -> 12).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> SHOWCASE_PEDESTAL = BLOCKS.register("showcase_pedestal",
            () -> new ShowcasePedestalBlock(frost(MapColor.ICE).noOcclusion().lightLevel(s -> 10).strength(-1.0f, 3600000f)));
    public static final RegistryObject<Block> SHOP_SIGN = BLOCKS.register("shop_sign",
            () -> new ShopSignBlock(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1.0f, 3600000f)
                    .lightLevel(s -> 15).noLootTable()));
    public static final RegistryObject<Block> ICICLE_CHANDELIER = BLOCKS.register("icicle_chandelier",
            () -> new IcicleChandelierBlock(frost(MapColor.ICE).noOcclusion().noCollission().lightLevel(s -> 15).strength(0.6f)));

    /** Non-melting slippery ice used by every Yantra (never use vanilla ice: it melts). */
    public static final RegistryObject<Block> PERMAFROST_ICE = BLOCKS.register("permafrost_ice",
            () -> new PermafrostIceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98f).strength(0.5f)
                    .sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> FROST_BRICKS = BLOCKS.register("frost_bricks", () -> new Block(frost(MapColor.ICE)));
    public static final RegistryObject<Block> GLACIER_TILES = BLOCKS.register("glacier_tiles",
            () -> new Block(frost(MapColor.ICE).friction(0.9f)));
    public static final RegistryObject<Block> AURORA_ICE = BLOCKS.register("aurora_ice",
            () -> new Block(frost(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 12)));
    public static final RegistryObject<Block> PACKED_FROST = BLOCKS.register("packed_frost", () -> new Block(frost(MapColor.ICE)));
    public static final RegistryObject<Block> ICE_PILLAR = BLOCKS.register("ice_pillar", () -> new RotatedPillarBlock(frost(MapColor.ICE)));
    public static final RegistryObject<Block> SNOW_FLOOR = BLOCKS.register("snow_floor",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.6f).sound(SoundType.SNOW)));
    public static final RegistryObject<Block> FROSTED_GLASS = BLOCKS.register("frosted_glass",
            () -> new GlassBlock(frost(MapColor.ICE).noOcclusion().isValidSpawn((s, l, p, t) -> false)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    private ModBlocks() {
    }
}
