package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.AcUnitBlockEntity;
import com.iceage.yantra.block.FarrataFanBlockEntity;
import com.iceage.yantra.block.GolaMachineBlockEntity;
import com.iceage.yantra.block.IcePortalBlockEntity;
import com.iceage.yantra.block.RetroFridgeBlockEntity;
import com.iceage.yantra.block.ShopSignBlockEntity;
import com.iceage.yantra.block.ShowcasePedestalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IceAgeYantra.MODID);

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<GolaMachineBlockEntity>> GOLA = BLOCK_ENTITIES.register("gola_machine",
            () -> BlockEntityType.Builder.of(GolaMachineBlockEntity::new, ModBlocks.GOLA_MACHINE.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<FarrataFanBlockEntity>> FAN = BLOCK_ENTITIES.register("farrata_fan",
            () -> BlockEntityType.Builder.of(FarrataFanBlockEntity::new, ModBlocks.FARRATA_FAN.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<RetroFridgeBlockEntity>> FRIDGE = BLOCK_ENTITIES.register("retro_fridge",
            () -> BlockEntityType.Builder.of(RetroFridgeBlockEntity::new, ModBlocks.RETRO_FRIDGE.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AcUnitBlockEntity>> AC = BLOCK_ENTITIES.register("ac_unit",
            () -> BlockEntityType.Builder.of(AcUnitBlockEntity::new, ModBlocks.AC_UNIT.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<IcePortalBlockEntity>> PORTAL = BLOCK_ENTITIES.register("ice_portal",
            () -> BlockEntityType.Builder.of(IcePortalBlockEntity::new, ModBlocks.ICE_PORTAL.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ShowcasePedestalBlockEntity>> PEDESTAL = BLOCK_ENTITIES.register("showcase_pedestal",
            () -> BlockEntityType.Builder.of(ShowcasePedestalBlockEntity::new, ModBlocks.SHOWCASE_PEDESTAL.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ShopSignBlockEntity>> SHOP_SIGN = BLOCK_ENTITIES.register("shop_sign",
            () -> BlockEntityType.Builder.of(ShopSignBlockEntity::new, ModBlocks.SHOP_SIGN.get()).build(null));

    private ModBlockEntities() {
    }
}
