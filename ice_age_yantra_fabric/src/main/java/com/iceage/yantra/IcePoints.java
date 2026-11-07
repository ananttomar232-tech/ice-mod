package com.iceage.yantra;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The frozen-blocks counter: awarding, multiplier ("speed") and syncing to the client HUD. */
public final class IcePoints {
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private IcePoints() {
    }

    public static long get(MinecraftServer server, UUID id) {
        return IceData.get(server).entry(id).points;
    }

    public static float speed(MinecraftServer server, UUID id) {
        return IceData.get(server).entry(id).speed;
    }

    /** Adds frozen blocks (multiplied by the counter speed). Returns how many were really added. */
    public static long award(MinecraftServer server, UUID id, long raw) {
        if (server == null || id == null || raw <= 0) return 0;
        IceData data = IceData.get(server);
        IceData.Entry e = data.entry(id);
        long before = e.points;
        if (before >= Tier.FINAL_TARGET) return 0;
        long amount = Math.max(1L, Math.round(raw * (double) e.speed));
        long after = Math.min(Tier.FINAL_TARGET, before + amount);
        e.points = after;
        data.markDirty();
        PENDING.merge(id, after - before, Long::sum);
        return after - before;
    }

    public static void set(MinecraftServer server, UUID id, long value) {
        IceData data = IceData.get(server);
        data.entry(id).points = Math.max(0L, Math.min(Tier.FINAL_TARGET, value));
        data.markDirty();
        PENDING.merge(id, 0L, Long::sum);
    }

    public static void setSpeed(MinecraftServer server, UUID id, float speed) {
        IceData data = IceData.get(server);
        data.entry(id).speed = Math.max(0.1f, Math.min(1000f, speed));
        data.markDirty();
        PENDING.merge(id, 0L, Long::sum);
    }

    public static void send(ServerPlayerEntity p, long gained) {
        IceData.Entry e = IceData.get(p.server).entry(p.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(e.points);
        buf.writeFloat(e.speed);
        buf.writeLong(gained);
        ServerPlayNetworking.send(p, Net.POINTS, buf);
    }

    /** Called at the end of every server tick: pushes changed counters to their owners (one packet per tick). */
    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null) send(p, en.getValue());
            it.remove();
        }
    }
}
