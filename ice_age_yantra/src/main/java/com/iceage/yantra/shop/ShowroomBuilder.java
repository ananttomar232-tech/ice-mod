package com.iceage.yantra.shop;

import com.iceage.yantra.block.IcePortalBlock;
import com.iceage.yantra.block.ShowcasePedestalBlock;
import com.iceage.yantra.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Builds the Frozen Showroom: a crystalline ice hall (frost bricks, frosted glass windows, ice pillars,
 * aurora strips, glacier tile floor), hanging icicle chandeliers, five showcase pedestals with floating
 * Yantras, a glowing title and the return portal.
 */
public final class ShowroomBuilder {
    public static final int VERSION = 5;
    public static final int FLOOR = 63;
    public static final int X0 = -12, X1 = 12, Z0 = -10, Z1 = 22, TOP = 76;
    public static final BlockPos[] PEDESTALS = {
            new BlockPos(-7, 64, 1), new BlockPos(7, 64, 1), new BlockPos(-7, 64, 9), new BlockPos(7, 64, 9), new BlockPos(0, 64, 16)
    };

    private ShowroomBuilder() {
    }

    static class State extends SavedData {
        int version;

        static State load(CompoundTag tag) {
            State s = new State();
            s.version = tag.getInt("version");
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("version", version);
            return tag;
        }
    }

    public static void ensureBuilt(ServerLevel level) {
        State st = level.getDataStorage().computeIfAbsent(State::load, State::new, "ice_age_yantra_showroom");
        if (st.version < VERSION) {
            build(level);
            st.version = VERSION;
            st.setDirty();
        }
    }

    public static void rebuild(ServerLevel level) {
        build(level);
        State st = level.getDataStorage().computeIfAbsent(State::load, State::new, "ice_age_yantra_showroom");
        st.version = VERSION;
        st.setDirty();
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void build(ServerLevel level) {
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockState bricks = ModBlocks.FROST_BRICKS.get().defaultBlockState();
        BlockState glass = ModBlocks.FROSTED_GLASS.get().defaultBlockState();
        BlockState tiles = ModBlocks.GLACIER_TILES.get().defaultBlockState();
        BlockState aurora = ModBlocks.AURORA_ICE.get().defaultBlockState();
        BlockState packed = ModBlocks.PACKED_FROST.get().defaultBlockState();
        BlockState pillar = ModBlocks.ICE_PILLAR.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

        // clear volume + shell
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                for (int y = FLOOR - 2; y <= TOP + 2; y++) {
                    set(level, x, y, z, air);
                }
            }
        }
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                set(level, x, FLOOR - 1, z, packed);
                boolean wall = x == X0 || x == X1 || z == Z0 || z == Z1;
                // floor
                BlockState floor;
                if (wall) floor = packed;
                else if (x == X0 + 1 || x == X1 - 1 || z == Z0 + 1 || z == Z1 - 1) floor = bricks;
                else if (Math.abs(x) <= 1 && z < 14) floor = (Math.abs(x) == 1 ? tiles : aurora);
                else floor = tiles;
                set(level, x, FLOOR, z, floor);
                // ceiling + roof
                boolean strip = !wall && (z % 4 == 0) && Math.abs(x) <= 9;
                set(level, x, TOP, z, strip ? aurora : packed);
                set(level, x, TOP + 1, z, bricks);
                if (!wall) continue;
                for (int y = FLOOR + 1; y < TOP; y++) {
                    BlockState s = bricks;
                    boolean sideWall = x == X0 || x == X1;
                    int along = sideWall ? z : x;
                    boolean isPillar = sideWall ? Math.floorMod(along + 4, 6) == 0 : Math.floorMod(along, 6) == 0;
                    boolean corner = (x == X0 || x == X1) && (z == Z0 || z == Z1);
                    if (corner || isPillar) s = pillar;
                    else if (y >= FLOOR + 3 && y <= FLOOR + 9) s = glass;
                    if (y == TOP - 1 && !(corner || isPillar)) s = aurora;
                    if (y == FLOOR + 1 && !(corner || isPillar)) s = packed;
                    set(level, x, y, z, s);
                }
            }
        }
        // pedestal daises (glowing rings) + pedestals
        for (int i = 0; i < PEDESTALS.length; i++) {
            BlockPos p = PEDESTALS[i];
            int r = i == 4 ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    set(level, p.getX() + dx, FLOOR, p.getZ() + dz, (Math.abs(dx) == r || Math.abs(dz) == r) ? aurora : bricks);
                }
            }
            level.setBlock(p, ModBlocks.SHOWCASE_PEDESTAL.get().defaultBlockState().setValue(ShowcasePedestalBlock.TIER, i + 1), Block.UPDATE_ALL);
        }
        // chandeliers
        int[][] ch = {{-6, -4}, {6, -4}, {-6, 5}, {6, 5}, {-6, 13}, {6, 13}, {0, 1}, {0, 9}, {0, 19}};
        for (int[] c : ch) {
            level.setBlock(new BlockPos(c[0], TOP - 1, c[1]), ModBlocks.ICICLE_CHANDELIER.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        // title sign above the van stage
        level.setBlock(new BlockPos(0, FLOOR + 8, 20), ModBlocks.SHOP_SIGN.get().defaultBlockState(), Block.UPDATE_ALL);
        // return portal at the entrance, facing into the hall
        BlockState portal = ModBlocks.ICE_PORTAL.get().defaultBlockState().setValue(IcePortalBlock.FACING, Direction.SOUTH);
        level.setBlock(new BlockPos(0, FLOOR + 1, Z0 + 1), portal.setValue(IcePortalBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(new BlockPos(0, FLOOR + 2, Z0 + 1), portal.setValue(IcePortalBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        // arrival pad: a solid glowing dais whose top is exactly Y = 65.0, centred on X = 0.5, Z = 0.5
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, dx, FLOOR, dz, packed);
                set(level, dx, FLOOR + 1, dz, (dx == 0 && dz == 0) ? aurora : bricks);
            }
        }
        // frost carpet in front of the portal
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                set(level, dx, FLOOR, Z0 + dz, dx == 0 ? aurora : tiles);
            }
        }
    }
}
