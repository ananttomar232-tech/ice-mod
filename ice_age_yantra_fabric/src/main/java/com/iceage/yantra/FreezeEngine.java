package com.iceage.yantra;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Freezing and melting of terrain. Only packed ice and blue ice are ever produced (neither melts by itself near
 * light, fire or sun). Only Gotiya's hairdryer melts them back.
 */
public final class FreezeEngine {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private FreezeEngine() {
    }

    public static boolean isFrozen(BlockState s) {
        return s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE) || s.isOf(Blocks.ICE);
    }

    /** Blocks that must never be frozen or melted: unbreakable, block entities, this mod's blocks and the shop room. */
    public static boolean isProtected(ServerWorld w, BlockPos pos, BlockState s) {
        if (ShopBuilder.isInShopZone(pos)) return true;
        if (s.getHardness(w, pos) < 0) return true;
        if (s.hasBlockEntity()) return true;
        return Registries.BLOCK.getId(s.getBlock()).getNamespace().equals(IceAgeYantra.MODID);
    }

    private static BlockState iceFor(ServerWorld w, BlockPos pos) {
        return w.random.nextInt(5) == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
    }

    /**
     * Freezes the topmost solid (or water) block of the column x/z found between yRef+up and yRef-down.
     * Plants and snow on top are cleared. Returns the number of blocks that changed.
     */
    public static int freezeColumn(ServerWorld w, int x, int z, int yRef, int up, int down) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int changed = 0;
        for (int y = yRef + up; y >= yRef - down; y--) {
            m.set(x, y, z);
            BlockState s = w.getBlockState(m);
            if (s.isAir()) continue;
            if (isFrozen(s)) return changed;
            boolean fluid = !s.getFluidState().isEmpty();
            if (!fluid && s.isReplaceable()) {
                // grass, flowers, snow layers: clear away and keep looking for the ground below
                if (!isProtected(w, m, s)) {
                    w.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
                    changed++;
                }
                continue;
            }
            if (isProtected(w, m, s)) return changed;
            w.setBlockState(m, iceFor(w, m), FLAGS);
            return changed + 1;
        }
        return changed;
    }

    /** Paves a road: like freezeColumn but also fills empty space right under the vehicle. */
    public static int paveColumn(ServerWorld w, int x, int z, int roadY) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos road = new BlockPos(x, roadY, z);
        BlockState s = w.getBlockState(road);
        if (s.isAir() || (!s.getFluidState().isEmpty()) || (s.isReplaceable())) {
            if (!isProtected(w, road, s) || s.isAir()) {
                w.setBlockState(road, Blocks.PACKED_ICE.getDefaultState(), FLAGS);
                return 1;
            }
            return 0;
        }
        return freezeColumn(w, x, z, roadY, 0, 1);
    }

    /** Hairdryer: turns packed/blue ice of a column back into (briefly flowing) water, leaving the ground below. */
    public static int meltColumn(ServerWorld w, int x, int z, int yRef) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int changed = 0;
        for (int y = yRef + 2; y >= yRef - 3; y--) {
            m.set(x, y, z);
            BlockState s = w.getBlockState(m);
            if (!isFrozen(s) || ShopBuilder.isInShopZone(m)) continue;
            BlockState water = Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 3);
            w.setBlockState(m, water, Block.NOTIFY_ALL);
            changed++;
        }
        return changed;
    }
}
