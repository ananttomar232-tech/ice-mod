package com.iceage.yantra.block;

import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Holds no state: drives the vortex particles client side; the renderer draws the swirling gateway. */
public class IcePortalBlockEntity extends BlockEntity {
    public int age;

    public IcePortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORTAL.get(), pos, state);
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(IcePortalBlock.FACING) ? s.getValue(IcePortalBlock.FACING) : Direction.NORTH;
    }

    public void clientTick() {
        age++;
        if (level == null) return;
        Direction f = facing();
        Direction side = f.getClockWise();
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 1.0, cz = worldPosition.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 0.45 + level.random.nextDouble() * 0.25;
            double ox = Mth.cos((float) a) * r * 0.55;
            double oy = Mth.sin((float) a) * r * 1.05;
            double px = cx + side.getStepX() * ox;
            double pz = cz + side.getStepZ() * ox;
            double py = cy + oy;
            // spiral inward + slight drift out of the gateway
            double vx = -side.getStepX() * ox * 0.06 + f.getStepX() * 0.01 - side.getStepX() * oy * 0.03;
            double vz = -side.getStepZ() * ox * 0.06 + f.getStepZ() * 0.01 - side.getStepZ() * oy * 0.03;
            double vy = -oy * 0.06 + ox * 0.03;
            level.addParticle(ModParticles.FROST_SWIRL.get(), px, py, pz, vx, vy, vz);
        }
        if (age % 4 == 0) {
            level.addParticle(ModParticles.SPARKLE.get(), cx + (level.random.nextDouble() - 0.5) * 1.6, cy + (level.random.nextDouble() - 0.5) * 2.2,
                    cz + (level.random.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
        }
        if (age % 3 == 0) {
            level.addParticle(ModParticles.FROST_MIST.get(), cx + (level.random.nextDouble() - 0.5) * 0.8, worldPosition.getY() + 0.05,
                    cz + (level.random.nextDouble() - 0.5) * 0.8, f.getStepX() * 0.02, 0.0, f.getStepZ() * 0.02);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 3, 0);
    }
}
