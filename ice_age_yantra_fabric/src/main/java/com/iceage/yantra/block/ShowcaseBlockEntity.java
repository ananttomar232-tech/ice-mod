package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client animation clock of a shop pedestal (floating, spinning Yantra above it). */
public class ShowcaseBlockEntity extends BlockEntity {
    public int age;

    public ShowcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SHOWCASE_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, ShowcaseBlockEntity be) {
        be.age++;
    }
}
