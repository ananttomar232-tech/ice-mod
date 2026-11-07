package com.iceage.yantra.progress;

import com.iceage.yantra.network.CelebratePacket;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.network.SyncProgressPacket;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The pacing engine: every Yantra pays its frozen blocks in here. Points are capped at 10,000,000 and every
 * tier target crossing is celebrated, which is also the signal Yantras use to switch themselves off.
 */
public final class PacingEngine {
    private static final Map<UUID, Long> PENDING_GAIN = new HashMap<>();

    private PacingEngine() {
    }

    public static long points(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return 0;
        return IceProgress.get(server).entry(owner).points;
    }

    public static boolean reached(MinecraftServer server, UUID owner, long target) {
        return points(server, owner) >= target;
    }

    /** Adds frozen blocks to the owner's counter. Returns how many were really added (cap aware). */
    public static long award(MinecraftServer server, UUID owner, long amount) {
        if (server == null || owner == null || amount <= 0) return 0;
        IceProgress data = IceProgress.get(server);
        IceProgress.Entry e = data.entry(owner);
        long before = e.points;
        if (before >= Tier.FINAL_TARGET) return 0;
        amount = Math.max(1L, Math.round(amount * (double) e.speed));
        long after = Math.min(Tier.FINAL_TARGET, before + amount);
        e.points = after;
        data.setDirty();
        PENDING_GAIN.merge(owner, after - before, Long::sum);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        for (Tier t : Tier.values()) {
            if (before < t.target && after >= t.target) {
                onTargetReached(server, player, t, e);
            }
        }
        return after - before;
    }

    public static void set(MinecraftServer server, UUID owner, long value) {
        IceProgress data = IceProgress.get(server);
        IceProgress.Entry e = data.entry(owner);
        e.points = Math.max(0, Math.min(Tier.FINAL_TARGET, value));
        if (e.points < Tier.FINAL_TARGET) e.climaxDone = false;
        data.setDirty();
        PENDING_GAIN.merge(owner, 0L, Long::sum);
    }

    /** Counter speed multiplier (x0.1 .. x1000); every award is multiplied by it. */
    public static void setSpeed(MinecraftServer server, UUID owner, float speed) {
        IceProgress data = IceProgress.get(server);
        IceProgress.Entry e = data.entry(owner);
        e.speed = Math.max(0.1f, Math.min(1000f, speed));
        data.setDirty();
        PENDING_GAIN.merge(owner, 0L, Long::sum);
    }

    private static void onTargetReached(MinecraftServer server, ServerPlayer player, Tier tier, IceProgress.Entry e) {
        if (player == null) return;
        if (tier == Tier.VAN) {
            if (e.climaxDone) return;
            e.climaxDone = true;
            // finale: Gotiya staggers in with his giant hairdryer to melt the world... and fails (ice never melts)
            com.iceage.yantra.entity.ChillVillagerEntity.spawnHairdryerFinale(player);
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), ModSounds.FANFARE.get(), SoundSource.PLAYERS, 1.6f, 1.0f);
            for (int i = 0; i < 12; i++) {
                spawnFirework(level, player.getX() + (level.random.nextDouble() - 0.5) * 16, player.getY() + 1,
                        player.getZ() + (level.random.nextDouble() - 0.5) * 16, i);
            }
            return;
        }
        // no banner, no chat: only a soft chime
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.TARGET_REACHED.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void spawnFirework(ServerLevel level, double x, double y, double z, int seed) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fw = new CompoundTag();
        ListTag explosions = new ListTag();
        CompoundTag ex = new CompoundTag();
        ex.putByte("Type", (byte) (seed % 5));
        ex.putIntArray("Colors", new int[]{0x7FE6FF, 0xFFFFFF, 0xFF8FC0, 0x3D8BFF});
        ex.putIntArray("FadeColors", new int[]{0xBFF6FF});
        ex.putBoolean("Twinkle", true);
        ex.putBoolean("Trail", true);
        explosions.add(ex);
        fw.put("Explosions", explosions);
        fw.putByte("Flight", (byte) (1 + seed % 2));
        rocket.getOrCreateTag().put("Fireworks", fw);
        FireworkRocketEntity e = new FireworkRocketEntity(level, x, y, z, rocket);
        level.addFreshEntity(e);
    }

    /** Called at the end of every server tick: pushes changed counters to their owners. */
    public static void flush(MinecraftServer server) {
        if (PENDING_GAIN.isEmpty()) return;
        IceProgress data = IceProgress.get(server);
        var it = PENDING_GAIN.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p != null) {
                IceProgress.Entry e = data.entry(en.getKey());
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SyncProgressPacket(e.points, e.purchasedMask, en.getValue(), e.speed));
            }
            it.remove();
        }
    }

    public static void sync(ServerPlayer p) {
        IceProgress.Entry e = IceProgress.get(p.server).entry(p.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SyncProgressPacket(e.points, e.purchasedMask, 0, e.speed));
    }
}
