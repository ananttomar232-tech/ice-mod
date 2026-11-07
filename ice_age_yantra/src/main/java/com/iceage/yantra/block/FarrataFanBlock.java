package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Tier 2 - Farrata high-speed 3-speed table fan. */
public class FarrataFanBlock extends YantraBlock {
    public FarrataFanBlock(Properties props) {
        super(props);
    }

    @Override
    protected BlockEntityType<? extends YantraBlockEntity> beType() {
        return ModBlockEntities.FAN.get();
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        var f = state.getValue(FACING);
        return Shapes.or(rotated(f, 2, 0, 2, 14, 3, 14), rotated(f, 6, 3, 6, 10, 12, 11), rotated(f, 0, 6, 0, 16, 16, 9));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarrataFanBlockEntity(pos, state);
    }
}
