package com.iceage.yantra.block;

import com.iceage.yantra.Config;
import com.iceage.yantra.entity.ChillVillagerEntity;
import com.iceage.yantra.progress.FreezeEngine;
import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModEntities;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Door opens -> freezing fog spills out, ice blocks get launched, and a sweaty villager waddles over to sit
 * inside shivering with relief. Left-click the fridge or the villager to make him scream and sprint off.
 * At 1,000 frozen blocks the door slams and locks.
 */
public class RetroFridgeBlockEntity extends YantraBlockEntity {
    public static final int CLOSED = 0, OPEN = 1, LOCKED = 2;

    @Nullable
    private UUID villager;
    public boolean villagerInside;
    public long slamTime = -1000;
    public long burstTime = -1000;
    private final List<BlockPos> field = new ArrayList<>();
    private int fieldIndex;
    private int maxStage = FreezeEngine.PACKED;

    public float doorAngle, prevDoorAngle, topDoorAngle, prevTopDoorAngle;

    public RetroFridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FRIDGE.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return Tier.FRIDGE;
    }

    @Override
    public void onUse(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (phase == OPEN) return;
        // reusable forever: the door never stays locked, every click starts a fresh freezing run
        owner = player.getUUID();
        beginRun();
        buildField(level);
        setPhase(OPEN);
        level.playSound(null, worldPosition, ModSounds.FRIDGE_OPEN.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        say(player, "msg.ice_age_yantra.fridge.opened", ChatFormatting.AQUA);
        Vec3 f = front(0.8);
        level.sendParticles(ModParticles.FROST_MIST.get(), f.x, f.y + 0.9, f.z, 24, 0.35, 0.5, 0.35, 0.05);
        spawnVillager(level);
    }

    private void buildField(ServerLevel level) {
        field.clear();
        fieldIndex = 0;
        int r = Config.FRIDGE_RADIUS.get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || (dx == 0 && dz == 0)) continue;
                field.add(new BlockPos(worldPosition.getX() + dx, worldPosition.getY(), worldPosition.getZ() + dz));
            }
        }
        Collections.shuffle(field, new java.util.Random(worldPosition.asLong()));
        BlockPos c = worldPosition;
        field.sort((a, b) -> Double.compare(a.distSqr(c) + (a.hashCode() & 7), b.distSqr(c) + (b.hashCode() & 7)));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (phase == OPEN) {
            open(level);
        } else if (phase == CLOSED && villagerInside) {
            if (phaseTicks % 90 == 30) {
                level.playSound(null, worldPosition, ModSounds.VILLAGER_RELIEF.get(), SoundSource.NEUTRAL, 0.45f, 0.9f);
            }
            if (phaseTicks % 6 == 0) {
                Vec3 f = front(0.5);
                level.sendParticles(ModParticles.SNOWFLAKE.get(), f.x, f.y + 0.4 + level.random.nextDouble() * 1.2, f.z, 1, 0.3, 0.1, 0.3, 0.01);
            }
        }
    }

    private void open(ServerLevel level) {
        if (field.isEmpty()) buildField(level);
        Direction d = facing();
        Vec3 f = front(0.65);
        if (phaseTicks % 2 == 0) {
            level.sendParticles(ModParticles.FROST_MIST.get(), f.x, f.y + 0.3 + level.random.nextDouble() * 1.2, f.z, 0,
                    d.getStepX() * 0.6 + (level.random.nextDouble() - 0.5) * 0.4, -0.08, d.getStepZ() * 0.6 + (level.random.nextDouble() - 0.5) * 0.4, 0.25);
        }
        if (phaseTicks % Config.FRIDGE_LAUNCH_INTERVAL.get() == 0) launchIce(level, d);
        if (targetReached()) {
            lock(level);
            return;
        }
        if (phaseTicks % 3 != 0) return;
        int changed = 0;
        int per = Config.FRIDGE_BLOCKS_PER_PULSE.get();
        int guard = 0;
        while (changed < per && guard++ < per * 6 && !field.isEmpty()) {
            if (fieldIndex >= field.size()) {
                fieldIndex = 0;
                if (maxStage < FreezeEngine.BLUE) maxStage++;
            }
            BlockPos col = field.get(fieldIndex++);
            BlockPos s = FreezeEngine.surface(level, col.getX(), col.getZ(), worldPosition.getY(), 3, 6);
            if (s == null) continue;
            int c = FreezeEngine.advance(level, s, maxStage);
            if (c > 0) {
                changed += c;
                if (level.random.nextInt(4) == 0) {
                    level.sendParticles(ModParticles.FROST_MIST.get(), s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 1, 0.2, 0.05, 0.2, 0.01);
                }
            }
        }
        if (changed == 0) changed = 1;
        if (pay(changed)) lock(level);
    }

    private void launchIce(ServerLevel level, Direction d) {
        BlockPos spawn = worldPosition.relative(d).above();
        if (!level.getBlockState(spawn).isAir()) return;
        BlockState block = level.random.nextInt(3) == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
        FallingBlockEntity fb = FallingBlockEntity.fall(level, spawn, block);
        fb.dropItem = false;
        double side = (level.random.nextDouble() - 0.5) * 0.7;
        Direction s = d.getClockWise();
        fb.setDeltaMovement(d.getStepX() * (0.35 + level.random.nextDouble() * 0.35) + s.getStepX() * side, 0.55 + level.random.nextDouble() * 0.25,
                d.getStepZ() * (0.35 + level.random.nextDouble() * 0.35) + s.getStepZ() * side);
        fb.hurtMarked = true;
        level.playSound(null, spawn, ModSounds.FAN_WHOOSH.get(), SoundSource.BLOCKS, 0.4f, 1.6f);
        pay(1);
    }

    private void lock(ServerLevel level) {
        setPhase(CLOSED);
        slamTime = level.getGameTime();
        sync();
        level.playSound(null, worldPosition, ModSounds.FRIDGE_SLAM.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
        Vec3 f = front(0.6);
        level.sendParticles(ModParticles.FROST_MIST.get(), f.x, f.y + 1.0, f.z, 30, 0.4, 0.6, 0.4, 0.08);
        level.sendParticles(ParticleTypes.CLOUD, f.x, f.y + 1.0, f.z, 10, 0.3, 0.6, 0.3, 0.05);
        if (owner != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p != null) say(p, "msg.ice_age_yantra.fridge.slam", ChatFormatting.AQUA);
        }
    }

    /** /iceage gotiya 3: Gotiya walks over and sits in the fridge (opens the door if it is closed). */
    @Nullable
    public ChillVillagerEntity summonVillager(ServerLevel level, ServerPlayer by) {
        if (phase != OPEN) {
            owner = by.getUUID();
            beginRun();
            buildField(level);
            setPhase(OPEN);
            level.playSound(null, worldPosition, ModSounds.FRIDGE_OPEN.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        }
        if (villager != null && level.getEntity(villager) instanceof ChillVillagerEntity old && old.isAlive()) {
            if (old.isSitting()) return old;
            old.discard();
        }
        villager = null;
        villagerInside = false;
        spawnVillager(level);
        return villager != null && level.getEntity(villager) instanceof ChillVillagerEntity v ? v : null;
    }

    private void spawnVillager(ServerLevel level) {
        if (villager != null && level.getEntity(villager) instanceof ChillVillagerEntity existing && existing.isAlive()) return;
        Direction d = facing();
        float baseYaw = d.toYRot();
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = Math.toRadians(baseYaw + (level.random.nextDouble() - 0.5) * 140);
            double dist = 9 + level.random.nextDouble() * 4;
            int x = Mth.floor(worldPosition.getX() + 0.5 - Math.sin(ang) * dist);
            int z = Mth.floor(worldPosition.getZ() + 0.5 + Math.cos(ang) * dist);
            BlockPos ground = FreezeEngine.surface(level, x, z, worldPosition.getY(), 6, 8);
            if (ground == null) continue;
            BlockPos at = ground.above();
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) continue;
            if (level.getBlockState(ground).getFluidState().isEmpty()) {
                spawnAt(level, Vec3.atBottomCenterOf(at));
                return;
            }
        }
        spawnAt(level, front(3.0).add(0, 0.1, 0));
    }

    private void spawnAt(ServerLevel level, Vec3 p) {
        ChillVillagerEntity v = ModEntities.CHILL_VILLAGER.get().create(level);
        if (v == null) return;
        v.moveTo(p.x, p.y, p.z, level.random.nextFloat() * 360f, 0);
        v.setFridge(worldPosition);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(v);
        villager = v.getUUID();
        level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 12, 0.3, 0.5, 0.3, 0.02);
        sync();
    }

    /** Where a seated villager sits (world space). */
    public Vec3 seat() {
        Direction d = facing();
        return Vec3.atBottomCenterOf(worldPosition).add(-d.getStepX() * 0.08, 0.06, -d.getStepZ() * 0.08);
    }

    public boolean canSeat(ChillVillagerEntity v) {
        return !villagerInside && (villager == null || villager.equals(v.getUUID()));
    }

    public void onVillagerSeated(ChillVillagerEntity v) {
        villager = v.getUUID();
        villagerInside = true;
        sync();
    }

    public void onVillagerLeft() {
        villagerInside = false;
        if (level != null) burstTime = level.getGameTime();
        sync();
    }

    /** Left-click on the fridge: if a villager is chilling inside, he screams and bolts. */
    public boolean scareVillager(Player by) {
        if (!(level instanceof ServerLevel sl) || villager == null) return false;
        Entity e = sl.getEntity(villager);
        if (e instanceof ChillVillagerEntity v && v.isSitting()) {
            v.scare(by);
            return true;
        }
        return false;
    }

    @Override
    protected void clientTick() {
        prevDoorAngle = doorAngle;
        prevTopDoorAngle = topDoorAngle;
        long t = level == null ? 0 : level.getGameTime();
        boolean burst = t - burstTime < 26;
        float target = (phase == OPEN || burst) ? 108f : 0f;
        float topTarget = phase == OPEN ? 72f + 6f * Mth.sin(age * 0.15f) : (burst ? 30f : 0f);
        float rate = target > doorAngle ? 0.16f : 0.34f;
        doorAngle += (target - doorAngle) * rate;
        topDoorAngle += (topTarget - topDoorAngle) * (topTarget > topDoorAngle ? 0.09f : 0.3f);
        if (level != null && phase == OPEN && doorAngle > 40f) {
            Direction d = facing();
            Vec3 f = Vec3.atBottomCenterOf(worldPosition).add(d.getStepX() * 0.62, 0, d.getStepZ() * 0.62);
            for (int i = 0; i < 2; i++) {
                level.addParticle(ModParticles.FROST_MIST.get(), f.x + (level.random.nextDouble() - 0.5) * 0.8, f.y + 0.2 + level.random.nextDouble() * 1.5,
                        f.z + (level.random.nextDouble() - 0.5) * 0.8, d.getStepX() * 0.035, -0.012, d.getStepZ() * 0.035);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0, 0, 2.0).expandTowards(0, 2.5, 0);
    }

    @Override
    protected void saveSync(CompoundTag tag) {
        super.saveSync(tag);
        if (villager != null) tag.putUUID("villager", villager);
        tag.putBoolean("inside", villagerInside);
        tag.putLong("slam", slamTime);
        tag.putLong("burst", burstTime);
        tag.putInt("maxStage", maxStage);
    }

    @Override
    protected void loadSync(CompoundTag tag) {
        super.loadSync(tag);
        villager = tag.hasUUID("villager") ? tag.getUUID("villager") : null;
        villagerInside = tag.getBoolean("inside");
        slamTime = tag.getLong("slam");
        burstTime = tag.getLong("burst");
        maxStage = Math.max(FreezeEngine.PACKED, tag.getInt("maxStage"));
    }
}
