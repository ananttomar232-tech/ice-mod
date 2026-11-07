package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShopSignBlockEntity extends BlockEntity {
    public ShopSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP_SIGN.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(8, 3, 8);
    }
}
