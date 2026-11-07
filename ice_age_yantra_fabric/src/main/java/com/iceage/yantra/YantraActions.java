package com.iceage.yantra;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** What a Yantra does when activated: 20-30 flying ice blocks, an instant freeze of the ground, points. */
public final class YantraActions {
    private static final Map<Long, Long> COOLDOWN = new HashMap<>();

    private YantraActions() {
    }

    /** Fires the flying ice blocks (real falling-block entities) that land and become packed/blue ice. */
    public static void launch(ServerWorld world, BlockPos pos, Direction facing, Tier tier, int count) {
        BlockPos spawn = pos.up();
        if (!world.getBlockState(spawn).isAir()) spawn = pos.up(2);
        if (!world.getBlockState(spawn).isAir()) return;
        double fx = facing.getOffsetX(), fz = facing.getOffsetZ();
        double baseAngle = Math.atan2(fz, fx);
        for (int i = 0; i < count; i++) {
            double angle;
            double speed;
            double vy;
            switch (tier) {
                case GOLA -> {
                    angle = world.random.nextDouble() * Math.PI * 2;
                    speed = 0.7 + world.random.nextDouble() * 0.5;
                    vy = 0.45 + world.random.nextDouble() * 0.35;
                }
                case FAN -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 1.2;
                    speed = 0.9 + world.random.nextDouble() * 0.5;
                    vy = 0.25 + world.random.nextDouble() * 0.3;
                }
                case FRIDGE -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 2.0;
                    speed = 0.6 + world.random.nextDouble() * 0.4;
                    vy = 0.6 + world.random.nextDouble() * 0.3;
                }
                default -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 2.6;
                    speed = 1.0 + world.random.nextDouble() * 0.5;
                    vy = 0.2 + world.random.nextDouble() * 0.3;
                }
            }
            BlockState ice = world.random.nextInt(3) == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(world, spawn, ice);
            fb.dropItem = false;
            fb.setHurtEntities(2.0f, 20);
            fb.setPosition(pos.getX() + 0.5 + Math.cos(angle) * 0.7, spawn.getY() + 0.2, pos.getZ() + 0.5 + Math.sin(angle) * 0.7);
            fb.setVelocity(Math.cos(angle) * speed, vy, Math.sin(angle) * speed);
            fb.velocityModified = true;
        }
    }

    /** Instantly freezes the ground around the Yantra into packed ice. Returns the number of changed blocks. */
    private static int freeze(ServerWorld world, BlockPos pos, Direction facing, Tier tier) {
        int changed = 0;
        int y = pos.getY();
        switch (tier) {
            case GOLA -> changed += circle(world, pos, 4, y);
            case FRIDGE -> changed += circle(world, pos, 6, y);
            case AC -> changed += circle(world, pos, 9, y);
            case FAN -> {
                Direction side = facing.rotateYClockwise();
                for (int d = 0; d <= 11; d++) {
                    int half = Math.max(1, (int) Math.round(d * 0.55));
                    for (int l = -half; l <= half; l++) {
                        int x = pos.getX() + facing.getOffsetX() * d + side.getOffsetX() * l;
                        int z = pos.getZ() + facing.getOffsetZ() * d + side.getOffsetZ() * l;
                        changed += FreezeEngine.freezeColumn(world, x, z, y, 4, 6);
                    }
                }
            }
            default -> {
            }
        }
        return changed;
    }

    private static int circle(ServerWorld world, BlockPos c, int r, int y) {
        int changed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                changed += FreezeEngine.freezeColumn(world, c.getX() + dx, c.getZ() + dz, y, 4, 6);
            }
        }
        return changed;
    }

    public static void effects(ServerWorld world, BlockPos pos, Direction facing, Tier tier) {
        Vec3d c = Vec3d.ofCenter(pos).add(0, 0.6, 0);
        world.spawnParticles(ModParticles.SNOWFLAKE, c.x, c.y, c.z, 60, 1.2, 0.8, 1.2, 0.15);
        world.spawnParticles(ModParticles.ICE_SHARD, c.x, c.y, c.z, 40, 0.8, 0.6, 0.8, 0.35);
        world.spawnParticles(ModParticles.FROST_MIST, c.x, c.y, c.z, 14, 0.8, 0.5, 0.8, 0.05);
        world.playSound(null, pos, ModSounds.FREEZE, SoundCategory.BLOCKS, 1.4f, 0.8f + 0.1f * tier.number);
        world.playSound(null, pos, tier == Tier.GOLA ? ModSounds.CRANK : tier == Tier.FAN ? ModSounds.FAN_WHOOSH : tier == Tier.FRIDGE ? ModSounds.FRIDGE_OPEN : ModSounds.AC_BLIZZARD, SoundCategory.BLOCKS, 1.2f, 1.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        int ringPoints = 36;
        double r = 0.5 + tier.number;
        for (int i = 0; i < ringPoints; i++) {
            double a = i * Math.PI * 2 / ringPoints;
            world.spawnParticles(ModParticles.SPARKLE, c.x + Math.cos(a) * 0.4, c.y, c.z + Math.sin(a) * 0.4, 0,
                    Math.cos(a), 0.05, Math.sin(a), 0.2 * MathHelper.clamp(r, 1, 4));
        }
    }
}
