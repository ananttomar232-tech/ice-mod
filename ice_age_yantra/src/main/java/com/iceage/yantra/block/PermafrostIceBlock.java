package com.iceage.yantra.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Permafrost Ice: the slippery, crystal-clear ice every Yantra freezes the world into.
 * Unlike vanilla ice it has NO random ticks, so it never melts - not next to torches, lanterns, fire,
 * lava or in sunlight - and it never turns into water when broken. The frozen world stays frozen.
 */
public class PermafrostIceBlock extends HalfTransparentBlock {
    public PermafrostIceBlock(Properties props) {
        super(props);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }
}
