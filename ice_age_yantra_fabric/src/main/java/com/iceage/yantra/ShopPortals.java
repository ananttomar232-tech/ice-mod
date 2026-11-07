package com.iceage.yantra;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Teleport logic of the Village Portal and the Exit Portal. Same dimension, so no loading screens. */
public final class ShopPortals {
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

    private ShopPortals() {
    }

    private static boolean cooling(ServerPlayerEntity p) {
        int now = p.server.getTicks();
        Integer until = COOLDOWN.get(p.getUuid());
        if (until != null && now < until) return true;
        COOLDOWN.put(p.getUuid(), now + 40);
        return false;
    }

    /** Walking into a Village Portal: remember where we came from and go to the shop floor. */
    public static void enterShop(ServerPlayerEntity p, BlockPos portalBase, Direction facing) {
        MinecraftServer server = p.server;
        ServerWorld overworld = server.getOverworld();
        if (p.getServerWorld() != overworld) return;
        if (cooling(p)) return;

        // return position: two blocks in front of the portal, on the side we came from
        Vec3d c = Vec3d.ofBottomCenter(portalBase);
        double side = (p.getX() - c.x) * facing.getOffsetX() + (p.getZ() - c.z) * facing.getOffsetZ();
        int s = side >= 0 ? 1 : -1;
        double dx = facing.getOffsetX() * s, dz = facing.getOffsetZ() * s;
        IceData data = IceData.get(server);
        IceData.Entry e = data.entry(p.getUuid());
        e.hasReturn = true;
        e.rx = c.x + dx * 2.0;
        e.ry = portalBase.getY();
        e.rz = c.z + dz * 2.0;
        e.ryaw = (float) (MathHelper.atan2(-dx, dz) * 57.29577951308232);
        data.markDirty();

        ShopBuilder.ensureBuilt(server);
        teleport(p, overworld, ShopBuilder.ARRIVAL, 0f, 5f);
        overworld.playSound(null, BlockPos.ofFloored(ShopBuilder.ARRIVAL), ModSounds.PORTAL_WHOOSH, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /** Stepping on the Exit Portal: back to IcePortalReturnPos, on solid ground. */
    public static void exitShop(ServerPlayerEntity p) {
        MinecraftServer server = p.server;
        ServerWorld overworld = server.getOverworld();
        if (cooling(p)) return;
        IceData.Entry e = IceData.get(server).entry(p.getUuid());
        Vec3d target;
        float yaw;
        if (e.hasReturn) {
            target = groundAt(overworld, new Vec3d(e.rx, e.ry, e.rz));
            yaw = e.ryaw;
        } else {
            BlockPos spawn = overworld.getSpawnPos();
            target = groundAt(overworld, new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5));
            yaw = 0f;
        }
        teleport(p, overworld, target, yaw, 0f);
        overworld.playSound(null, BlockPos.ofFloored(target), ModSounds.PORTAL_WHOOSH, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    /** Teleports with zero velocity and zero fall distance. */
    public static void teleport(ServerPlayerEntity p, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        p.stopRiding();
        p.fallDistance = 0f;
        p.teleport(world, pos.x, pos.y, pos.z, yaw, pitch);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.velocityModified = true;
    }

    private static boolean passable(ServerWorld w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return s.getCollisionShape(w, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Finds solid ground with two free blocks above it, near the given position (never mid-air, never inside blocks). */
    public static Vec3d groundAt(ServerWorld w, Vec3d near) {
        int x = MathHelper.floor(near.x), z = MathHelper.floor(near.z);
        w.getChunk(x >> 4, z >> 4);
        int y0 = MathHelper.floor(near.y);
        // scan down from just above the remembered feet position
        for (int y = Math.min(w.getTopY() - 3, y0 + 1); y > Math.max(w.getBottomY() + 1, y0 - 40); y--) {
            Vec3d r = check(w, x, y, z);
            if (r != null) return r;
        }
        // we were inside something: scan upwards instead
        for (int y = y0 + 2; y < Math.min(w.getTopY() - 3, y0 + 24); y++) {
            Vec3d r = check(w, x, y, z);
            if (r != null) return r;
        }
        BlockPos top = new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return Vec3d.ofBottomCenter(top);
    }

    private static Vec3d check(ServerWorld w, int x, int y, int z) {
        BlockPos g = new BlockPos(x, y, z);
        BlockState gs = w.getBlockState(g);
        if (gs.getCollisionShape(w, g).isEmpty()) return null;
        if (!passable(w, g.up()) || !passable(w, g.up(2))) return null;
        double top = g.getY() + gs.getCollisionShape(w, g).getMax(Direction.Axis.Y);
        return new Vec3d(x + 0.5, top, z + 0.5);
    }
}
