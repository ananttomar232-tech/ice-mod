package com.iceage.yantra.progress;

import com.iceage.yantra.Config;
import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Frost chain: natural block -> Permafrost Ice -> packed ice -> blue ice. Every step that changes the world is
 * one frozen block for the pacing engine. ICE MUST NEVER MELT: vanilla ice / frosted ice / snow layers are
 * never produced - only Permafrost Ice (no random ticks), packed ice and blue ice, none of which can melt.
 */
public final class FreezeEngine {
    public static final int NATURAL = 0, ICE = 1, PACKED = 2, BLUE = 3;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private FreezeEngine() {
    }

    public static int stageOf(BlockState s) {
        if (s.is(Blocks.BLUE_ICE)) return BLUE;
        if (s.is(Blocks.PACKED_ICE)) return PACKED;
        if (s.is(ModBlocks.PERMAFROST_ICE.get())) return ICE;
        return NATURAL;
    }

    /** Never returns a meltable block. */
    public static BlockState stateFor(int stage) {
        return switch (stage) {
            case ICE -> ModBlocks.PERMAFROST_ICE.get().defaultBlockState();
            case PACKED -> Blocks.PACKED_ICE.defaultBlockState();
            default -> Blocks.BLUE_ICE.defaultBlockState();
        };
    }

    public static boolean isModBlock(BlockState s) {
        if (s.is(ModBlocks.PERMAFROST_ICE.get())) return false;
        var key = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return key != null && IceAgeYantra.MODID.equals(key.getNamespace());
    }

    /** Blocks that must never be touched (bedrock-like, containers, our own machines, portals). */
    public static boolean isProtected(Level level, BlockPos pos, BlockState s) {
        if (s.isAir()) return true;
        if (s.getDestroySpeed(level, pos) < 0) return true;
        if (Config.PROTECT_CONTAINERS.get() && s.hasBlockEntity()) return true;
        if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)) return true;
        return isModBlock(s);
    }

    public static boolean isPlant(BlockState s) {
        return s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.CROPS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.SUGAR_CANE));
    }

    /** Advance one step along the frost chain, never beyond maxStage. Returns 1 when the world changed. */
    public static int advance(ServerLevel level, BlockPos pos, int maxStage) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return 0;
        FluidState f = s.getFluidState();
        if (s.getBlock() instanceof LiquidBlock) {
            if (f.is(FluidTags.WATER)) {
                level.setBlock(pos, stateFor(Math.max(ICE, Math.min(maxStage, PACKED))), FLAGS);
                return 1;
            }
            if (f.is(FluidTags.LAVA)) {
                level.setBlock(pos, f.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
                return 1;
            }
            return 0;
        }
        if (isPlant(s)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            return 1;
        }
        if (isProtected(level, pos, s)) return 0;
        if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, stateFor(Math.max(ICE, Math.min(maxStage, PACKED))), FLAGS);
            return 1;
        }
        int st = stageOf(s);
        if (st >= maxStage) return 0;
        int next = st + 1;
        if (st == NATURAL && (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) next = Math.min(maxStage, ICE);
        level.setBlock(pos, stateFor(next), FLAGS);
        return 1;
    }

    /** Convert straight to a stage (used by the van / AC). Returns 1 when the world changed. */
    public static int freezeTo(ServerLevel level, BlockPos pos, int stage) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return 0;
        if (s.getBlock() instanceof LiquidBlock) {
            FluidState f = s.getFluidState();
            if (f.is(FluidTags.LAVA)) {
                level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), FLAGS);
                return 1;
            }
            level.setBlock(pos, stateFor(Math.max(stage, ICE)), FLAGS);
            return 1;
        }
        if (isPlant(s)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            return 1;
        }
        if (isProtected(level, pos, s)) return 0;
        if (stageOf(s) >= stage) return 0;
        level.setBlock(pos, stateFor(stage), FLAGS);
        return 1;
    }

    /** Removes a block (the van plough). Returns 1 when something was shattered. */
    public static int shatter(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return 0;
        if (s.getBlock() instanceof LiquidBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            return 1;
        }
        if (isProtected(level, pos, s)) return 0;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        return 1;
    }

    /** Top solid ground block of a column, searched downward from y+up to y-down. */
    public static BlockPos surface(Level level, int x, int z, int y, int up, int down) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y + up, z);
        for (int i = 0; i <= up + down; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && !isPlant(s)) {
                return m.immutable();
            }
            m.move(0, -1, 0);
        }
        return null;
    }

    public static boolean isFluidSource(BlockState s) {
        return s.getFluidState().isSource() && s.getFluidState().getType() != Fluids.EMPTY;
    }
}
