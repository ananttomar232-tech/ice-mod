package com.iceage.yantra.block;

import com.iceage.yantra.Config;
import com.iceage.yantra.RigData;
import com.iceage.yantra.progress.FreezeEngine;
import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crank it twice -> the handle snaps off with a cartoon pop, the rotary wheel keeps spinning on its own and
 * machine-guns crushed ice at the 25 surrounding blocks. At 25 frozen blocks it sputters, squeaks and dies.
 */
public class GolaMachineBlockEntity extends YantraBlockEntity {
    public static final int IDLE = 0, CRANK = 1, RUNAWAY = 2, SPUTTER = 3, DONE = 4;
    public static final int CRANK_TICKS = 14;

    private int cranks;
    private int frozen;
    public boolean snapped;
    public long snapTime;
    private final List<BlockPos> targets = new ArrayList<>();
    private int targetIndex;

    // client animation
    public float wheelAngle, prevWheelAngle, wheelSpeed;

    public GolaMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GOLA.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return Tier.GOLA;
    }

    @Override
    public void onUse(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (phase == RUNAWAY) {
            say(player, "msg.ice_age_yantra.gola.possessed", ChatFormatting.LIGHT_PURPLE);
            return;
        }
        if (phase == SPUTTER || phase == DONE || targetReachedFor(player)) {
            say(player, "msg.ice_age_yantra.done", ChatFormatting.GOLD, Tier.GOLA.displayName(), Tier.FAN.displayName());
            level.playSound(null, worldPosition, ModSounds.SQUEAK.get(), SoundSource.BLOCKS, 0.8f, 1.3f);
            return;
        }
        if (phase == CRANK) return;
        owner = player.getUUID();
        cranks++;
        setPhase(CRANK);
        level.playSound(null, worldPosition, ModSounds.CRANK.get(), SoundSource.BLOCKS, 1.0f, 0.9f + 0.15f * cranks);
        say(player, "msg.ice_age_yantra.gola.crank", ChatFormatting.AQUA, cranks, Config.GOLA_CRANKS_TO_SNAP.get());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        switch (phase) {
            case CRANK -> {
                if (phaseTicks >= CRANK_TICKS) {
                    if (cranks >= Config.GOLA_CRANKS_TO_SNAP.get()) snap(level);
                    else setPhase(IDLE);
                }
            }
            case RUNAWAY -> runaway(level);
            case SPUTTER -> {
                Vec3 w = wheelWorld();
                if (phaseTicks % 2 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, w.x, w.y + 0.1, w.z, 2, 0.12, 0.05, 0.12, 0.01);
                    level.sendParticles(ParticleTypes.SMOKE, w.x, w.y, w.z, 3, 0.2, 0.1, 0.2, 0.02);
                }
                if (phaseTicks == 18 || phaseTicks == 34) {
                    level.playSound(null, worldPosition, ModSounds.SQUEAK.get(), SoundSource.BLOCKS, 1.0f, phaseTicks == 18 ? 1.2f : 0.8f);
                }
                if (phaseTicks >= 60) setPhase(DONE);
            }
            default -> {
            }
        }
    }

    private void snap(ServerLevel level) {
        snapped = true;
        snapTime = level.getGameTime();
        targets.clear();
        targetIndex = 0;
        collectTargets(level, 4);
        setPhase(RUNAWAY);
        Vec3 h = handleWorld();
        level.playSound(null, worldPosition, ModSounds.HANDLE_POP.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 18, 0.2, 0.2, 0.2, 0.4);
        level.sendParticles(ParticleTypes.POOF, h.x, h.y, h.z, 6, 0.1, 0.1, 0.1, 0.02);
        if (owner != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p != null) say(p, "msg.ice_age_yantra.gola.snap", ChatFormatting.RED);
        }
    }

    private void collectTargets(ServerLevel level, int radius) {
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos s = FreezeEngine.surface(level, worldPosition.getX() + dx, worldPosition.getZ() + dz, worldPosition.getY(), 2, 4);
                if (s == null) continue;
                BlockState st = level.getBlockState(s);
                if (FreezeEngine.stageOf(st) >= FreezeEngine.ICE || FreezeEngine.isProtected(level, s, st)) continue;
                list.add(s);
            }
        }
        BlockPos c = worldPosition;
        list.sort(Comparator.comparingDouble(p -> p.distSqr(c) + (Math.atan2(p.getZ() - c.getZ(), p.getX() - c.getX()) + Math.PI) * 0.01));
        targets.addAll(list);
    }

    private void runaway(ServerLevel level) {
        Vec3 w = wheelWorld();
        if (phaseTicks % 12 == 1) {
            level.playSound(null, worldPosition, ModSounds.WHEEL_SPIN.get(), SoundSource.BLOCKS, 0.9f, 1.0f + Math.min(0.8f, phaseTicks / 120f));
        }
        level.sendParticles(ModParticles.ICE_SHARD.get(), w.x, w.y + 0.05, w.z, 2, 0.08, 0.02, 0.08, 0.15);
        if (owner != null && targetReached()) {
            beginSputter(level);
            return;
        }
        if (phaseTicks % Math.max(1, Config.GOLA_TICKS_PER_BLOCK.get()) != 0) return;
        int changed = 0;
        int guard = 0;
        while (changed == 0 && guard++ < 40) {
            if (targetIndex >= targets.size()) {
                targets.clear();
                targetIndex = 0;
                collectTargets(level, 4 + guard / 8 + frozen / 25);
                if (targets.isEmpty()) break;
            }
            BlockPos t = targets.get(targetIndex++);
            changed = FreezeEngine.freezeTo(level, t, FreezeEngine.ICE);
            if (changed > 0) {
                shootIce(level, w, Vec3.atCenterOf(t).add(0, 0.5, 0));
                level.playSound(null, t, ModSounds.FREEZE.get(), SoundSource.BLOCKS, 0.7f, 0.9f + level.random.nextFloat() * 0.4f);
            }
        }
        frozen++;
        if (pay(1)) beginSputter(level);
    }

    private void beginSputter(ServerLevel level) {
        setPhase(SPUTTER);
        level.playSound(null, worldPosition, ModSounds.SPUTTER.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        level.playSound(null, worldPosition, ModSounds.SQUEAK.get(), SoundSource.BLOCKS, 1.0f, 1.5f);
    }

    private void shootIce(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            double arc = Math.sin(t * Math.PI) * 0.9;
            Vec3 p = from.lerp(to, t).add(0, arc, 0);
            level.sendParticles(ModParticles.ICE_SHARD.get(), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.02);
        }
        level.sendParticles(ModParticles.SNOWFLAKE.get(), to.x, to.y, to.z, 8, 0.35, 0.1, 0.35, 0.03);
    }

    /** Wheel hub in world space (rotated rig pivot). */
    public Vec3 wheelWorld() {
        return rigToWorld(RigData.GOLA_WHEEL_X, RigData.GOLA_WHEEL_Y + 0.05, RigData.GOLA_WHEEL_Z);
    }

    public Vec3 handleWorld() {
        return rigToWorld(RigData.GOLA_HANDLE_TOP_X, RigData.GOLA_HANDLE_TOP_Y - 0.15, RigData.GOLA_HANDLE_TOP_Z);
    }

    public Vec3 rigToWorld(double x, double y, double z) {
        double dx = x - 0.5, dz = z - 0.5;
        float rot = (180f - facing().toYRot()) * Mth.DEG_TO_RAD;
        double c = Math.cos(rot), s = Math.sin(rot);
        double rx = dx * c + dz * s;
        double rz = -dx * s + dz * c;
        return new Vec3(worldPosition.getX() + 0.5 + rx, worldPosition.getY() + y, worldPosition.getZ() + 0.5 + rz);
    }

    @Override
    protected void clientTick() {
        prevWheelAngle = wheelAngle;
        float target;
        switch (phase) {
            case CRANK -> target = 360f / CRANK_TICKS;
            case RUNAWAY -> target = 38f + Math.min(30f, phaseTicks * 0.25f);
            case SPUTTER -> target = Math.max(0f, 26f - phaseTicks * 0.5f) + (age % 6 < 3 ? 6f : -4f);
            default -> target = 0f;
        }
        if (phase == CRANK) wheelSpeed = target;
        else wheelSpeed += (target - wheelSpeed) * 0.08f;
        wheelAngle += wheelSpeed;
        if (phase == RUNAWAY || phase == CRANK) phaseTicks++;
        if (phase == SPUTTER) phaseTicks++;
        if (level != null && phase == SPUTTER && level.random.nextInt(3) == 0) {
            Vec3 w = wheelWorld();
            level.addParticle(ParticleTypes.SMOKE, w.x, w.y + 0.1, w.z, 0, 0.04, 0);
        }
    }

    @Override
    protected void saveSync(CompoundTag tag) {
        super.saveSync(tag);
        tag.putInt("cranks", cranks);
        tag.putInt("frozen", frozen);
        tag.putBoolean("snapped", snapped);
        tag.putLong("snapTime", snapTime);
    }

    @Override
    protected void loadSync(CompoundTag tag) {
        super.loadSync(tag);
        cranks = tag.getInt("cranks");
        frozen = tag.getInt("frozen");
        snapped = tag.getBoolean("snapped");
        snapTime = tag.getLong("snapTime");
    }
}
