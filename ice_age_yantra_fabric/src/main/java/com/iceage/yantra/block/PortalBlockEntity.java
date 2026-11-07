package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client animation clock of a portal arch (the swirling vortex). Lives in the lower half only. */
public class PortalBlockEntity extends BlockEntity {
    public int age;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PORTAL_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, PortalBlockEntity be) {
        be.age++;
    }
}
