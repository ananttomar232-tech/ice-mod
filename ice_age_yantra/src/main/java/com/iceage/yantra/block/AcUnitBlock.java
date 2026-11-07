package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Tier 4 - Split AC unit (driven by the Turbo Remote). */
public class AcUnitBlock extends YantraBlock {
    public AcUnitBlock(Properties props) {
        super(props);
    }

    @Override
    protected BlockEntityType<? extends YantraBlockEntity> beType() {
        return ModBlockEntities.AC.get();
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return rotated(state.getValue(FACING), 0, 0, 1.5, 16, 12, 14.5);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcUnitBlockEntity(pos, state);
    }
}
