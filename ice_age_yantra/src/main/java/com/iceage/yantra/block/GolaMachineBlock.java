package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Tier 1 - Desi Baraf Gola / Chuski machine. */
public class GolaMachineBlock extends YantraBlock {
    public GolaMachineBlock(Properties props) {
        super(props);
    }

    @Override
    protected BlockEntityType<? extends YantraBlockEntity> beType() {
        return ModBlockEntities.GOLA.get();
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        var f = state.getValue(FACING);
        return Shapes.or(rotated(f, 4, 0, 2, 12, 2, 13), rotated(f, 6, 2, 1, 10, 12, 15), rotated(f, 5, 8, 0, 11, 15, 16));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GolaMachineBlockEntity(pos, state);
    }
}
