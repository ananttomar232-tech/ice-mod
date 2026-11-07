package com.iceage.yantra.shop;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.progress.IceProgress;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The dedicated Frozen Showroom dimension and the teleport logic of the ice portals. */
public final class ShowroomDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, IceAgeYantra.id("frozen_showroom"));
    public static final Vec3 ARRIVAL = new Vec3(0.5, 65.0, 0.5);
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private ShowroomDimension() {
    }

    public static boolean isShowroom(Level level) {
        return level.dimension() == KEY;
    }

    /** Stepping into an ice portal. */
    public static void enterPortal(ServerPlayer player) {
        long now = player.server.getTickCount();
        Long until = COOLDOWN.get(player.getUUID());
        if (until != null && now < until) {
            COOLDOWN.put(player.getUUID(), now + 20);
            return;
        }
        COOLDOWN.put(player.getUUID(), now + 60);
        toggle(player);
    }

    public static void toggle(ServerPlayer player) {
        COOLDOWN.put(player.getUUID(), (long) player.server.getTickCount() + 60);
        if (isShowroom(player.level())) goBack(player);
        else goToShowroom(player);
    }

    public static void goToShowroom(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel shop = server.getLevel(KEY);
        if (shop == null) {
            player.displayClientMessage(Component.literal("Frozen Showroom dimension is missing (datapack disabled?)").withStyle(ChatFormatting.RED), false);
            return;
        }
        IceProgress data = IceProgress.get(server);
        IceProgress.Entry e = data.entry(player.getUUID());
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        e.returnDim = player.level().dimension();
        e.returnPos = player.position().subtract(look.scale(1.6));
        e.returnYaw = player.getYRot() + 180f;
        data.setDirty();
        shop.getChunk(0, 0);
        ShowroomBuilder.ensureBuilt(shop);
        safeTeleport(player, shop, ARRIVAL, 0f, 5f);
        shop.playSound(null, BlockPos.containing(ARRIVAL), ModSounds.PORTAL_WHOOSH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Teleports with zero velocity and no accumulated fall damage. */
    public static void safeTeleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.stopRiding();
        if (player.level() != level) {
            // tell the client to skip the "Loading terrain" screen for this hop
            com.iceage.yantra.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.iceage.yantra.network.CelebratePacket(com.iceage.yantra.network.CelebratePacket.SEAMLESS, 0));
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
        player.hurtMarked = true;
        player.connection.resetPosition();
    }

    /** Finds solid ground with two free blocks above it, near the remembered spot (never mid-air, never inside blocks). */
    public static Vec3 groundAt(ServerLevel level, Vec3 near) {
        int x = net.minecraft.util.Mth.floor(near.x), z = net.minecraft.util.Mth.floor(near.z);
        level.getChunk(x >> 4, z >> 4);
        int startY = Math.min(level.getMaxBuildHeight() - 3, net.minecraft.util.Mth.floor(near.y) + 3);
        for (int y = startY; y > Math.max(level.getMinBuildHeight(), startY - 48); y--) {
            BlockPos g = new BlockPos(x, y, z);
            if (level.getBlockState(g).getCollisionShape(level, g).isEmpty()) continue;
            if (!level.getBlockState(g.above()).getCollisionShape(level, g.above()).isEmpty()) continue;
            if (!level.getBlockState(g.above(2)).getCollisionShape(level, g.above(2)).isEmpty()) continue;
            if (level.getBlockState(g).getBlock() instanceof com.iceage.yantra.block.IcePortalBlock) continue;
            double top = g.getY() + level.getBlockState(g).getCollisionShape(level, g).max(net.minecraft.core.Direction.Axis.Y);
            return new Vec3(x + 0.5, top, z + 0.5);
        }
        BlockPos h = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        return Vec3.atBottomCenterOf(h);
    }

    /** Void safety net: anyone below the showroom floor is put straight back on the arrival pad. */
    public static void guard(ServerPlayer player) {
        if (isShowroom(player.level()) && player.getY() < ShowroomBuilder.FLOOR - 4) {
            safeTeleport(player, (ServerLevel) player.level(), ARRIVAL, 0f, 5f);
        }
    }

    public static void goBack(ServerPlayer player) {
        MinecraftServer server = player.server;
        IceProgress.Entry e = IceProgress.get(server).entry(player.getUUID());
        ServerLevel target = e.returnDim != null ? server.getLevel(e.returnDim) : null;
        Vec3 pos = e.returnPos;
        float yaw = e.returnYaw;
        if (target == null || pos == null) {
            target = server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            pos = Vec3.atBottomCenterOf(target.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
            yaw = 0f;
        }
        pos = groundAt(target, pos);
        safeTeleport(player, target, pos, yaw, 0f);
        target.playSound(null, BlockPos.containing(pos), ModSounds.PORTAL_WHOOSH.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
