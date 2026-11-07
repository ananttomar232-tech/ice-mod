package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hanging icicle chandelier with cyan frost orbs (light 15). */
public class IcicleChandelierBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 2, 1, 15, 16, 15);

    public IcicleChandelierBlock(Properties props) {
        super(props);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ModParticles.SPARKLE.get(), pos.getX() + 0.5 + Math.cos(a) * 0.42, pos.getY() + 0.5, pos.getZ() + 0.5 + Math.sin(a) * 0.42,
                    0, -0.01, 0);
        }
        if (random.nextInt(9) == 0) {
            level.addParticle(ModParticles.SNOWFLAKE.get(), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, -0.02, 0);
        }
    }
}
