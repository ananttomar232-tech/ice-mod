package com.iceage.yantra.block;

import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShowcasePedestalBlockEntity extends BlockEntity {
    public int age;

    public ShowcasePedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEDESTAL.get(), pos, state);
    }

    public Tier tier() {
        BlockState s = getBlockState();
        Tier t = s.hasProperty(ShowcasePedestalBlock.TIER) ? Tier.byNumber(s.getValue(ShowcasePedestalBlock.TIER)) : null;
        return t == null ? Tier.GOLA : t;
    }

    public void clientTick() {
        age++;
        if (level != null && age % 5 == 0) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = tier() == Tier.VAN ? 1.6 : 0.55;
            level.addParticle(ModParticles.SPARKLE.get(), worldPosition.getX() + 0.5 + Math.cos(a) * r, worldPosition.getY() + 1.1 + level.random.nextDouble() * 1.2,
                    worldPosition.getZ() + 0.5 + Math.sin(a) * r, 0, 0.015, 0);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.0, 0, 3.0).expandTowards(0, 5, 0);
    }
}
