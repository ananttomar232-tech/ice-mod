package com.iceage.yantra.entity;

import com.iceage.yantra.block.RetroFridgeBlockEntity;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The goofy overheated villager from the fridge gag: waddles over sweating, sits inside the open fridge
 * shivering with relief, and screams + sprints away when anyone pokes him or the fridge.
 */
public class ChillVillagerEntity extends PathfinderMob implements VillagerDataHolder {
    public static final int WALKING = 0, SITTING = 1, FLEEING = 2;
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ChillVillagerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER = SynchedEntityData.defineId(ChillVillagerEntity.class, EntityDataSerializers.VILLAGER_DATA);

    @Nullable
    private BlockPos fridgePos;
    private int stateTicks;
    @Nullable
    private Vec3 threat;

    public ChillVillagerEntity(EntityType<? extends ChillVillagerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, WALKING);
        entityData.define(DATA_VILLAGER, new VillagerData(VillagerType.DESERT, VillagerProfession.FARMER, 1));
    }

    @Override
    public VillagerData getVillagerData() {
        return entityData.get(DATA_VILLAGER);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        entityData.set(DATA_VILLAGER, data);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public boolean isSitting() {
        return state() == SITTING;
    }

    private void setState(int s) {
        entityData.set(DATA_STATE, s);
        stateTicks = 0;
    }

    public void setFridge(BlockPos pos) {
        fridgePos = pos;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new WalkToFridgeGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Nullable
    private RetroFridgeBlockEntity fridge() {
        if (fridgePos == null || !level().isLoaded(fridgePos)) return null;
        return level().getBlockEntity(fridgePos) instanceof RetroFridgeBlockEntity f ? f : null;
    }

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        switch (state()) {
            case WALKING -> {
                RetroFridgeBlockEntity f = fridge();
                if (f == null || stateTicks > 1800) {
                    scare(null);
                    return;
                }
                if (f.phase() == RetroFridgeBlockEntity.LOCKED) {
                    playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                    scare(null);
                    return;
                }
                Vec3 front = Vec3.atBottomCenterOf(fridgePos).add(f.facing().getStepX() * 1.1, 0, f.facing().getStepZ() * 1.1);
                if (position().distanceToSqr(front) < 2.6 && f.canSeat(this) && f.phase() == RetroFridgeBlockEntity.OPEN) {
                    sitDown(sl, f);
                }
                if (stateTicks % 60 == 0) {
                    playSound(SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.3f);
                }
            }
            case SITTING -> {
                RetroFridgeBlockEntity f = fridge();
                if (f == null) {
                    scare(null);
                    return;
                }
                Vec3 seat = f.seat();
                setPos(seat.x, seat.y, seat.z);
                setDeltaMovement(Vec3.ZERO);
                float yaw = f.facing().toYRot();
                setYRot(yaw);
                setYBodyRot(yaw);
                setYHeadRot(yaw + Mth.sin(stateTicks * 0.05f) * 25f);
                if (stateTicks % 100 == 40) {
                    playSound(ModSounds.VILLAGER_RELIEF.get(), 0.7f, 1.0f);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 5, 0.3, 0.2, 0.3, 0.0);
                }
            }
            case FLEEING -> {
                if (stateTicks % 30 == 5) {
                    playSound(ModSounds.VILLAGER_SCREAM.get(), 1.2f, 1.0f + random.nextFloat() * 0.3f);
                }
                if (stateTicks % 3 == 0) {
                    sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.7, getZ(), 2, 0.2, 0.1, 0.2, 0.05);
                }
                if (stateTicks > 200) {
                    sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
                    sl.sendParticles(ModParticles.SNOWFLAKE.get(), getX(), getY() + 1.0, getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                    discard();
                }
            }
            default -> {
            }
        }
    }

    private void sitDown(ServerLevel sl, RetroFridgeBlockEntity f) {
        setState(SITTING);
        noPhysics = true;
        setNoGravity(true);
        getNavigation().stop();
        Vec3 seat = f.seat();
        moveTo(seat.x, seat.y, seat.z, f.facing().toYRot(), 0);
        f.onVillagerSeated(this);
        playSound(ModSounds.VILLAGER_RELIEF.get(), 1.0f, 1.0f);
        sl.sendParticles(ModParticles.FROST_MIST.get(), seat.x, seat.y + 1.0, seat.z, 12, 0.3, 0.4, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, seat.x, seat.y + 1.8, seat.z, 8, 0.3, 0.2, 0.3, 0.0);
    }

    /** Scream, jump out, sprint away frantically. */
    public void scare(@Nullable Player by) {
        if (state() == FLEEING) return;
        boolean wasSitting = state() == SITTING;
        RetroFridgeBlockEntity f = fridge();
        threat = by != null ? by.position() : (fridgePos != null ? Vec3.atCenterOf(fridgePos) : position());
        setState(FLEEING);
        noPhysics = false;
        setNoGravity(false);
        playSound(ModSounds.VILLAGER_SCREAM.get(), 1.6f, 1.0f);
        Direction out = f != null ? f.facing() : getDirection();
        if (wasSitting && f != null) {
            f.onVillagerLeft();
            Vec3 p = Vec3.atBottomCenterOf(fridgePos).add(out.getStepX() * 1.0, 0.05, out.getStepZ() * 1.0);
            setPos(p.x, p.y, p.z);
        }
        Vec3 away = position().subtract(threat).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(out.getStepX(), 0, out.getStepZ());
        away = away.normalize();
        setDeltaMovement(away.x * 0.55, 0.62, away.z * 0.55);
        hurtMarked = true;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.8, getZ(), 20, 0.3, 0.3, 0.3, 0.2);
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private void clientFx() {
        if (state() == WALKING && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.9, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
        if (state() == SITTING) {
            if (random.nextInt(5) == 0) {
                level().addParticle(ModParticles.SNOWFLAKE.get(), getX() + (random.nextDouble() - 0.5) * 0.7, getY() + 1.0 + random.nextDouble(),
                        getZ() + (random.nextDouble() - 0.5) * 0.7, 0, -0.01, 0);
            }
            if (tickCount % 18 == 0) {
                float yaw = getYRot() * Mth.DEG_TO_RAD;
                level().addParticle(ModParticles.FROST_MIST.get(), getX() - Mth.sin(yaw) * 0.35, getY() + 1.45, getZ() + Mth.cos(yaw) * 0.35,
                        -Mth.sin(yaw) * 0.02, 0.005, Mth.cos(yaw) * 0.02);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && source.getEntity() instanceof Player p) {
            scare(p);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return state() != SITTING;
    }

    @Override
    protected void pushEntities() {
        if (state() != SITTING) super.pushEntities();
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == SITTING ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.VILLAGER_SCREAM.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (fridgePos != null) tag.put("fridge", NbtUtils.writeBlockPos(fridgePos));
        tag.putInt("chillState", state());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("fridge")) fridgePos = NbtUtils.readBlockPos(tag.getCompound("fridge"));
        int s = tag.getInt("chillState");
        entityData.set(DATA_STATE, s == SITTING ? SITTING : (s == FLEEING ? FLEEING : WALKING));
        if (s == SITTING) {
            noPhysics = true;
            setNoGravity(true);
        }
    }

    // ------------------------------------------------------------------------------------ goals
    private class WalkToFridgeGoal extends Goal {
        private int repath;

        WalkToFridgeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return state() == WALKING && fridgePos != null;
        }

        @Override
        public void tick() {
            if (--repath > 0 || fridgePos == null) return;
            repath = 10;
            RetroFridgeBlockEntity f = fridge();
            if (f == null) return;
            Vec3 front = Vec3.atBottomCenterOf(fridgePos).add(f.facing().getStepX() * 1.1, 0, f.facing().getStepZ() * 1.1);
            getNavigation().moveTo(front.x, front.y, front.z, 0.62);
            getLookControl().setLookAt(front.x, front.y + 1, front.z);
        }
    }

    private class FleeGoal extends Goal {
        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return state() == FLEEING;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone() && threat != null) {
                Vec3 target = DefaultRandomPos.getPosAway(ChillVillagerEntity.this, 24, 7, threat);
                if (target != null) getNavigation().moveTo(target.x, target.y, target.z, 1.75);
            }
        }
    }
}
