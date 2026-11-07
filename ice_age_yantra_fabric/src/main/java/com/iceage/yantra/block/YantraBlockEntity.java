package com.iceage.yantra.block;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.IcePoints;
import com.iceage.yantra.ModBlocks;
import com.iceage.yantra.ModParticles;
import com.iceage.yantra.ModSounds;
import com.iceage.yantra.Tier;
import com.iceage.yantra.YantraActions;
import com.iceage.yantra.entity.GotiyaEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Yantra runs exactly ONE cycle: right-click once, it freezes continuously for the whole cycle, pays out exactly the
 * price of the next tier, and is then used up (break it and place a fresh one). Also holds the client animation state.
 */
public class YantraBlockEntity extends BlockEntity {
    public static final int IDLE = 0, RUNNING = 1, USED = 2;
    public static final int EVENT_START = 1, EVENT_END = 2;
    public static final int HANDLE_FLIGHT_TICKS = 12;

    // ---- persistent state
    public int phase;
    public int cycle;
    public long awarded;
    public boolean handleGone;
    @Nullable
    private UUID owner;
    @Nullable
    private UUID gotiya;

    // ---- client animation
    public int age;
    public int playerId = -1;
    public int flightTicks;
    public float angle, prevAngle, speed, door, prevDoor;

    public YantraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.YANTRA_BE, pos, state);
    }

    public Tier tier() {
        return ((YantraBlock) getCachedState().getBlock()).tier;
    }

    public Direction facing() {
        return getCachedState().get(YantraBlock.FACING);
    }

    public static int cycleTicks(Tier t) {
        return switch (t) {
            case GOLA -> 120;
            case FAN -> 160;
            case FRIDGE -> 200;
            case AC -> 260;
            case VAN -> 1;
        };
    }

    private static int radius(Tier t) {
        return switch (t) {
            case GOLA -> 6;
            case FAN -> 14;
            case FRIDGE -> 9;
            case AC -> 13;
            case VAN -> 1;
        };
    }

    // ---------------------------------------------------------------------------------------------- server
    /** Called by the block on right-click. Returns false when the machine is already running or used up. */
    public boolean tryStart(ServerPlayerEntity player) {
        if (phase != IDLE || !(world instanceof ServerWorld sw)) return false;
        phase = RUNNING;
        cycle = 0;
        awarded = 0;
        owner = player.getUuid();
        Tier t = tier();
        if (t == Tier.GOLA) handleGone = true;
        markDirty();
        sw.addSyncedBlockEvent(pos, getCachedState().getBlock(), EVENT_START, player.getId());
        sw.playSound(null, pos, t == Tier.GOLA ? ModSounds.CRANK : t == Tier.FAN ? ModSounds.FAN_WHOOSH : t == Tier.FRIDGE ? ModSounds.FRIDGE_OPEN : ModSounds.AC_BLIZZARD,
                SoundCategory.BLOCKS, 1.3f, 1.0f);
        GotiyaEntity g = GotiyaEntity.spawnGag(sw, t, pos, facing(), player);
        gotiya = g == null ? null : g.getUuid();
        return true;
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, YantraBlockEntity be) {
        if (be.phase != RUNNING || !(world instanceof ServerWorld sw)) return;
        Tier t = be.tier();
        int total = cycleTicks(t);
        int i = be.cycle++;
        ServerPlayerEntity owner = be.owner == null ? null : sw.getServer().getPlayerManager().getPlayer(be.owner);

        // exact payout: after the last tick exactly t.cycleYield has been paid, no more, no less
        long should = t.cycleYield * (i + 1) / total;
        long pay = should - be.awarded;
        if (pay > 0 && be.owner != null) IcePoints.awardExact(sw.getServer(), be.owner, pay);
        be.awarded = should;

        // continuous freezing: the frozen ring/cone grows with the cycle
        int R = radius(t);
        double r0 = (double) R * i / total, r1 = (double) R * (i + 1) / total;
        freezeRing(sw, pos, be.facing(), t, R, r0, r1);

        // flying ice blocks: 20-30 over the first half second
        if (i == 0) be.projectilesLeft = 20 + sw.random.nextInt(11);
        if (i < 10 && be.projectilesLeft > 0) {
            int n = Math.max(1, be.projectilesLeft / (10 - i));
            YantraActions.launch(sw, pos, be.facing(), t, n);
            be.projectilesLeft -= n;
        }
        if (i % 12 == 0) YantraActions.effects(sw, pos, be.facing(), t);

        // Gola gag: the snapped handle hits the player's face
        if (t == Tier.GOLA && i == HANDLE_FLIGHT_TICKS && owner != null) {
            sw.playSound(null, owner.getBlockPos(), ModSounds.HANDLE_POP, SoundCategory.PLAYERS, 1.6f, 1.0f);
            sw.playSound(null, owner.getBlockPos(), ModSounds.SQUEAK, SoundCategory.PLAYERS, 1.0f, 1.5f);
            Vec3d eye = owner.getEyePos();
            sw.spawnParticles(ParticleTypes.CRIT, eye.x, eye.y, eye.z, 16, 0.2, 0.2, 0.2, 0.4);
            sw.spawnParticles(ParticleTypes.POOF, eye.x, eye.y, eye.z, 8, 0.2, 0.2, 0.2, 0.03);
            Vec3d away = owner.getPos().subtract(Vec3d.ofCenter(pos)).multiply(1, 0, 1);
            if (away.lengthSquared() > 1e-4) away = away.normalize();
            owner.addVelocity(away.x * 0.35, 0.25, away.z * 0.35);
            owner.velocityModified = true;
        }

        if (be.cycle >= total) be.finish(sw);
    }

    private int projectilesLeft;

    private static void freezeRing(ServerWorld w, BlockPos pos, Direction f, Tier t, int R, double r0, double r1) {
        Direction side = f.rotateYClockwise();
        int y = pos.getY();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d;
                if (t == Tier.FAN) {
                    double fw = dx * f.getOffsetX() + dz * f.getOffsetZ();
                    double lat = dx * side.getOffsetX() + dz * side.getOffsetZ();
                    if (fw < 0 || Math.abs(lat) > 0.55 * fw + 1.0) continue;
                    d = fw;
                } else {
                    d = Math.sqrt(dx * dx + dz * dz);
                }
                if (d <= r0 || d > r1) continue;
                FreezeEngine.freezeColumn(w, pos.getX() + dx, pos.getZ() + dz, y, 4, 6);
            }
        }
    }

    private void finish(ServerWorld sw) {
        phase = USED;
        markDirty();
        sw.addSyncedBlockEvent(pos, getCachedState().getBlock(), EVENT_END, 0);
        sw.playSound(null, pos, ModSounds.SPUTTER, SoundCategory.BLOCKS, 1.2f, 1.0f);
        sw.playSound(null, pos, ModSounds.SQUEAK, SoundCategory.BLOCKS, 1.0f, 1.3f);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.25, 0.2, 0.25, 0.02);
        if (gotiya != null && sw.getEntity(gotiya) instanceof GotiyaEntity g && g.isAlive() && g.state() != GotiyaEntity.DRYER) g.flee(null);
    }

    // ---------------------------------------------------------------------------------------------- client
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type == EVENT_START) {
            phase = RUNNING;
            flightTicks = 0;
            playerId = data;
            if (tier() == Tier.GOLA) handleGone = true;
            return true;
        }
        if (type == EVENT_END) {
            phase = USED;
            return true;
        }
        return super.onSyncedBlockEvent(type, data);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, YantraBlockEntity be) {
        be.age++;
        be.prevAngle = be.angle;
        be.prevDoor = be.door;
        if (be.phase == RUNNING && be.flightTicks < 1000) be.flightTicks++;
        boolean run = be.phase == RUNNING;
        be.speed += ((run ? 52f : 0f) - be.speed) * (run ? 0.12f : 0.05f);
        if (be.speed < 0.05f) be.speed = 0;
        be.angle += be.speed;
        // fridge: the door opens on the single click and stays open for the whole cycle, then closes
        float doorTarget = run ? 108f : 0f;
        be.door += (doorTarget - be.door) * (doorTarget > be.door ? 0.16f : 0.3f);
    }

    // ---------------------------------------------------------------------------------------------- nbt
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("phase", phase);
        nbt.putInt("cycle", cycle);
        nbt.putLong("awarded", awarded);
        nbt.putBoolean("handleGone", handleGone);
        if (owner != null) nbt.putUuid("owner", owner);
        if (gotiya != null) nbt.putUuid("gotiya", gotiya);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        phase = nbt.getInt("phase");
        cycle = nbt.getInt("cycle");
        awarded = nbt.getLong("awarded");
        handleGone = nbt.getBoolean("handleGone");
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
        gotiya = nbt.containsUuid("gotiya") ? nbt.getUuid("gotiya") : null;
        if (phase == RUNNING && world == null) flightTicks = 1000;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
