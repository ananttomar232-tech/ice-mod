package com.iceage.yantra;

import com.iceage.yantra.block.ShowcaseBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The secret Ice Shop: a 15 x 8 x 15 room built in the OVERWORLD at X 10000 / Y 120 / Z 10000 (no custom dimension,
 * no chunk-loading lag). Packed ice, blue ice, glowstone lighting and glass display pedestals.
 */
public final class ShopBuilder {
    public static final int VERSION = 4;
    public static final int CX = 10000, CZ = 10000;
    public static final int FLOOR = 120, TOP = 133;
    public static final int X0 = CX - 15, X1 = CX + 15, Z0 = CZ - 15, Z1 = CZ + 15;
    /** Where players arrive: standing on the floor (top of the floor blocks = Y 121.0). */
    public static final Vec3d ARRIVAL = new Vec3d(10000.5, 121.0, 10000.5);
    /** Showcase blocks (tier = index + 1). Their glass pedestal stands one block below. */
    public static final BlockPos[] SHOWCASES = {
            new BlockPos(CX - 10, FLOOR + 1, CZ - 5), new BlockPos(CX + 10, FLOOR + 1, CZ - 5),
            new BlockPos(CX - 10, FLOOR + 1, CZ + 5), new BlockPos(CX + 10, FLOOR + 1, CZ + 5),
            new BlockPos(CX, FLOOR + 1, CZ + 10)
    };
    public static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private ShopBuilder() {
    }

    public static boolean isInShopZone(BlockPos p) {
        return p.getX() >= X0 - 4 && p.getX() <= X1 + 4 && p.getZ() >= Z0 - 4 && p.getZ() <= Z1 + 4
                && p.getY() >= FLOOR - 6 && p.getY() <= TOP + 4;
    }

    /** Builds the shop if it does not exist yet (or is an older version). Safe to call often. */
    public static void ensureBuilt(MinecraftServer server) {
        IceData data = IceData.get(server);
        ServerWorld w = server.getOverworld();
        boolean ok = data.shopVersion >= VERSION;
        if (ok) {
            // the world may have been edited: check one showcase
            ok = w.getBlockState(SHOWCASES[0]).isOf(ModBlocks.SHOWCASE);
        }
        if (ok) return;
        build(w);
        data.shopVersion = VERSION;
        data.markDirty();
    }

    public static void rebuild(MinecraftServer server) {
        build(server.getOverworld());
        IceData data = IceData.get(server);
        data.shopVersion = VERSION;
        data.markDirty();
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, FLAGS);
    }

    private static void build(ServerWorld w) {
        // make sure every chunk of the room is generated and loaded
        for (int cx = (X0 - 2) >> 4; cx <= (X1 + 2) >> 4; cx++) {
            for (int cz = (Z0 - 2) >> 4; cz <= (Z1 + 2) >> 4; cz++) {
                w.getChunk(cx, cz);
            }
        }
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState packed = Blocks.PACKED_ICE.getDefaultState();
        BlockState blue = Blocks.BLUE_ICE.getDefaultState();
        BlockState glow = Blocks.GLOWSTONE.getDefaultState();
        BlockState lantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState glass = Blocks.GLASS.getDefaultState();

        // clear the volume (and a little around it)
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                for (int y = FLOOR - 1; y <= TOP + 4; y++) set(w, x, y, z, air);
            }
        }
        // foundation columns so the room never floats over a valley
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int y = FLOOR - 1; y >= FLOOR - 40 && y > w.getBottomY(); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (w.getBlockState(p).isOpaqueFullCube(w, p)) break;
                    set(w, x, y, z, packed);
                }
            }
        }
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean edgeX = x == X0 || x == X1, edgeZ = z == Z0 || z == Z1;
                boolean edge = edgeX || edgeZ;
                // floor: checkerboard of packed and blue ice
                set(w, x, FLOOR, z, edge || ((x + z) & 1) == 0 ? packed : blue);
                // ceiling
                set(w, x, TOP, z, edge ? blue : packed);
                // walls
                if (edge) {
                    for (int y = FLOOR + 1; y < TOP; y++) {
                        BlockState s = (edgeX && edgeZ) ? blue : (((edgeX ? z : x) - X0) % 4 == 0 ? blue : packed);
                        set(w, x, y, z, s);
                    }
                }
            }
        }
        // glowstone lighting: ceiling grid and two rows of wall lamps
        for (int i = 3; i < X1 - X0 - 2; i += 4) {
            for (int j = 3; j < Z1 - Z0 - 2; j += 4) set(w, X0 + i, TOP, Z0 + j, glow);
            for (int y : new int[]{FLOOR + 4, FLOOR + 9}) {
                set(w, X0 + i, y, Z0, glow);
                set(w, X0 + i, y, Z1, glow);
                set(w, X0, y, Z0 + i, glow);
                set(w, X1, y, Z0 + i, glow);
            }
        }
        // glass display pedestals: a 3 x 3 glass plate in the floor, lit from below by glowstone, carrying the showcase
        for (int i = 0; i < SHOWCASES.length; i++) {
            BlockPos s = SHOWCASES[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(w, s.getX() + dx, FLOOR, s.getZ() + dz, glass);
                    set(w, s.getX() + dx, FLOOR - 1, s.getZ() + dz, (dx == 0 && dz == 0) ? glow : lantern);
                }
            }
            w.setBlockState(s, ModBlocks.SHOWCASE.getDefaultState().with(ShowcaseBlock.TIER, i + 1), FLAGS);
        }
        // exit portal: a glowing arch against the north wall, facing into the room (with a sea lantern carpet)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(w, CX + dx, FLOOR, Z0 + 2 + dz, lantern);
        }
        BlockState exit = ModBlocks.EXIT_PORTAL.getDefaultState().with(com.iceage.yantra.block.PortalBlock.FACING, net.minecraft.util.math.Direction.SOUTH)
                .with(com.iceage.yantra.block.PortalBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER);
        w.setBlockState(new BlockPos(CX, FLOOR + 1, Z0 + 2), exit, FLAGS);
        w.setBlockState(new BlockPos(CX, FLOOR + 2, Z0 + 2), exit.with(com.iceage.yantra.block.PortalBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), FLAGS);
    }
}
