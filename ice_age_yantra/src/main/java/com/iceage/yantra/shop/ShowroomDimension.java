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
    public static final Vec3 ARRIVAL = new Vec3(0.5, 64.0, -5.5);
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
        ShowroomBuilder.ensureBuilt(shop);
        player.teleportTo(shop, ARRIVAL.x, ARRIVAL.y, ARRIVAL.z, 0f, 5f);
        shop.playSound(null, BlockPos.containing(ARRIVAL), ModSounds.PORTAL_WHOOSH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("msg.ice_age_yantra.showroom.welcome").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
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
        player.teleportTo(target, pos.x, pos.y, pos.z, yaw, 0f);
        target.playSound(null, BlockPos.containing(pos), ModSounds.PORTAL_WHOOSH.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
