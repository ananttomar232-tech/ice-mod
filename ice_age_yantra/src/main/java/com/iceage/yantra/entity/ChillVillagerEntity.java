package com.iceage.yantra.entity;

import com.iceage.yantra.block.FarrataFanBlockEntity;
import com.iceage.yantra.block.GolaMachineBlockEntity;
import com.iceage.yantra.block.RetroFridgeBlockEntity;
import com.iceage.yantra.block.YantraBlockEntity;
import com.iceage.yantra.progress.FreezeEngine;
import com.iceage.yantra.registry.ModEntities;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Gotiya: the goofy overheated villager who keeps trying to stop the Ice Age.
 * <ul>
 * <li>Tier 1 (Gola): storms over holding an empty bowl ("Main ye nahi hone dunga!"), gets blasted in the face
 * with crushed ice and runs off crying.</li>
 * <li>Tier 2 (Fan) / Tier 4 (AC): walks over and sits right in front of the machine, shivering happily.</li>
 * <li>Tier 3 (Fridge): waddles over and sits inside the open fridge.</li>
 * <li>Finale: hauls a giant industrial hairdryer to melt the world... and fails, because the ice never melts.</li>
 * </ul>
 * Any hit or left-click makes him scream and flee at normal villager panic speed.
 */
public class ChillVillagerEntity extends PathfinderMob implements VillagerDataHolder {
    public static final int WALKING = 0, SITTING = 1, FLEEING = 2, BOWL = 3, FRONT_WALK = 4, FRONT_SIT = 5, HAIRDRYER = 6, BLASTED = 7;
    public static final String NAME = "Gotiya";
    public static final float FLEE_SPEED = 0.5F;
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ChillVillagerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CRYING = SynchedEntityData.defineId(ChillVillagerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER = SynchedEntityData.defineId(ChillVillagerEntity.class, EntityDataSerializers.VILLAGER_DATA);

    /** The Yantra Gotiya is reacting to (fridge, gola, fan, AC). */
    @Nullable
    private BlockPos fridgePos;
    private int stateTicks;
    @Nullable
    private Vec3 threat;
    @Nullable
    private Vec3 sitSpot;

    public ChillVillagerEntity(EntityType<? extends ChillVillagerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal(NAME));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, WALKING);
        entityData.define(DATA_CRYING, false);
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

    /** Sitting pose (inside the fridge or on the ground in front of a fan / AC). */
    public boolean isSitting() {
        int s = state();
        return s == SITTING || s == FRONT_SIT;
    }

    public boolean isCrying() {
        return entityData.get(DATA_CRYING);
    }

    public boolean hasHairdryer() {
        return state() == HAIRDRYER;
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
        goalSelector.addGoal(2, new WalkToYantraGoal());
        goalSelector.addGoal(2, new HairdryerGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Nullable
    private RetroFridgeBlockEntity fridge() {
        if (fridgePos == null || !level().isLoaded(fridgePos)) return null;
        return level().getBlockEntity(fridgePos) instanceof RetroFridgeBlockEntity f ? f : null;
    }

    @Nullable
    private YantraBlockEntity anchor() {
        if (fridgePos == null || !level().isLoaded(fridgePos)) return null;
        return level().getBlockEntity(fridgePos) instanceof YantraBlockEntity y ? y : null;
    }

    // ------------------------------------------------------------------------------ spawning

    /** Shows a line of Gotiya dialogue as an on-screen subtitle to everyone nearby. */
    public static void dialogue(ServerLevel level, Vec3 at, String line) {
        Component msg = Component.literal(NAME + ": ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) < 48 * 48) p.displayClientMessage(msg, true);
        }
    }

    /** Spawns Gotiya on solid ground roughly {@code minDist..maxDist} blocks in front of a Yantra. */
    @Nullable
    public static ChillVillagerEntity spawnNear(ServerLevel level, BlockPos anchor, Direction facing, double minDist, double maxDist) {
        float baseYaw = facing.toYRot();
        for (int attempt = 0; attempt < 32; attempt++) {
            double ang = Math.toRadians(baseYaw + (level.random.nextDouble() - 0.5) * 120);
            double dist = minDist + level.random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(anchor.getX() + 0.5 - Math.sin(ang) * dist);
            int z = Mth.floor(anchor.getZ() + 0.5 + Math.cos(ang) * dist);
            BlockPos ground = FreezeEngine.surface(level, x, z, anchor.getY(), 6, 8);
            if (ground == null) continue;
            BlockPos at = ground.above();
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) continue;
            if (!level.getBlockState(ground).getFluidState().isEmpty()) continue;
            return spawnAt(level, Vec3.atBottomCenterOf(at), anchor);
        }
        Vec3 f = Vec3.atBottomCenterOf(anchor).add(facing.getStepX() * minDist, 0.1, facing.getStepZ() * minDist);
        return spawnAt(level, f, anchor);
    }

    @Nullable
    public static ChillVillagerEntity spawnAt(ServerLevel level, Vec3 p, @Nullable BlockPos anchor) {
        ChillVillagerEntity v = ModEntities.CHILL_VILLAGER.get().create(level);
        if (v == null) return null;
        v.moveTo(p.x, p.y, p.z, level.random.nextFloat() * 360f, 0);
        v.fridgePos = anchor;
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.EVENT, null, null);
        v.setCustomName(Component.literal(NAME));
        v.setCustomNameVisible(true);
        level.addFreshEntity(v);
        level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 12, 0.3, 0.5, 0.3, 0.02);
        return v;
    }

    /** Tier 1: Gotiya storms in with an empty bowl to stop the Gola machine. */
    public static ChillVillagerEntity forGola(ServerLevel level, GolaMachineBlockEntity gola) {
        ChillVillagerEntity v = spawnNear(level, gola.getBlockPos(), gola.facing(), 6, 8);
        if (v != null) {
            v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOWL));
            v.setDropChance(EquipmentSlot.MAINHAND, 0f);
            v.setState(BOWL);
            v.playSound(SoundEvents.VILLAGER_NO, 1.2f, 0.9f);
            dialogue(level, v.position(), "Tum is world ko barf me nahi jama sakte! Main ye nahi hone dunga!");
        }
        return v;
    }

    /** Tier 2 / 4: Gotiya walks over and sits right in front of the machine, shivering. */
    public static ChillVillagerEntity forFront(ServerLevel level, YantraBlockEntity y) {
        ChillVillagerEntity v = spawnNear(level, y.getBlockPos(), y.facing().getClockWise(), 7, 10);
        if (v != null) v.setState(FRONT_WALK);
        return v;
    }

    /** Finale: Gotiya hauls in a giant hairdryer to melt the frozen world. It does not work. */
    public static void spawnHairdryerFinale(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 c = player.position().add(look.scale(12));
        BlockPos ground = FreezeEngine.surface(level, Mth.floor(c.x), Mth.floor(c.z), player.blockPosition().getY(), 10, 16);
        Vec3 at = ground != null ? Vec3.atBottomCenterOf(ground.above()) : c;
        ChillVillagerEntity v = spawnAt(level, at, null);
        if (v == null) return;
        v.threat = player.position();
        v.setYRot((float) (Mth.atan2(-(player.getX() - at.x), player.getZ() - at.z) * Mth.RAD_TO_DEG));
        v.setState(HAIRDRYER);
        dialogue(level, at, "Ab dekho! Main poori duniya ki barf pighla dunga!!");
    }

    // ------------------------------------------------------------------------------ tick

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
                Vec3 front = Vec3.atBottomCenterOf(fridgePos).add(f.facing().getStepX() * 1.1, 0, f.facing().getStepZ() * 1.1);
                if (position().distanceToSqr(front) < 2.6 && f.canSeat(this) && f.phase() == RetroFridgeBlockEntity.OPEN) {
                    sitDown(sl, f);
                }
                if (stateTicks % 60 == 0) playSound(SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.3f);
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
            case BOWL -> tickBowl(sl);
            case BLASTED -> {
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                if (stateTicks % 4 == 0) {
                    sl.sendParticles(ModParticles.SNOWFLAKE.get(), getX(), getEyeY(), getZ(), 4, 0.25, 0.2, 0.25, 0.02);
                }
                if (stateTicks == 6) playSound(SoundEvents.VILLAGER_HURT, 1.2f, 1.4f);
                if (stateTicks >= 22) {
                    entityData.set(DATA_CRYING, true);
                    scare(null);
                }
            }
            case FRONT_WALK -> {
                YantraBlockEntity y = anchor();
                if (y == null || stateTicks > 1200) {
                    scare(null);
                    return;
                }
                Vec3 spot = frontSpot(y);
                if (position().multiply(1, 0, 1).distanceToSqr(spot.multiply(1, 0, 1)) < 0.9) {
                    sitSpot = new Vec3(spot.x, getY(), spot.z);
                    getNavigation().stop();
                    setState(FRONT_SIT);
                    playSound(ModSounds.VILLAGER_RELIEF.get(), 1.0f, 1.0f);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.4, getZ(), 6, 0.3, 0.2, 0.3, 0.0);
                }
                if (stateTicks % 50 == 0) playSound(SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.3f);
            }
            case FRONT_SIT -> tickFrontSit(sl);
            case HAIRDRYER -> tickHairdryer(sl);
            case FLEEING -> {
                if (stateTicks % 30 == 5) {
                    playSound(isCrying() ? SoundEvents.VILLAGER_HURT : ModSounds.VILLAGER_SCREAM.get(), 1.2f, 1.0f + random.nextFloat() * 0.3f);
                }
                if (stateTicks % 3 == 0) {
                    sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.7, getZ(), 2, 0.2, 0.1, 0.2, 0.05);
                }
                if (stateTicks > 220) {
                    sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
                    sl.sendParticles(ModParticles.SNOWFLAKE.get(), getX(), getY() + 1.0, getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                    discard();
                }
            }
            default -> {
            }
        }
    }

    /** Where Gotiya stands / sits: right in the cold stream, ~2 blocks in front of the Yantra. */
    private Vec3 frontSpot(YantraBlockEntity y) {
        double d = y instanceof GolaMachineBlockEntity ? 1.6 : 2.2;
        return Vec3.atBottomCenterOf(y.getBlockPos()).add(y.facing().getStepX() * d, 0, y.facing().getStepZ() * d);
    }

    private void tickBowl(ServerLevel sl) {
        YantraBlockEntity y = anchor();
        if (y == null || stateTicks > 900) {
            scare(null);
            return;
        }
        Vec3 spot = frontSpot(y);
        getLookControl().setLookAt(Vec3.atCenterOf(y.getBlockPos()));
        if (stateTicks % 40 == 20) playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f + random.nextFloat() * 0.2f);
        if (position().multiply(1, 0, 1).distanceToSqr(spot.multiply(1, 0, 1)) < 1.4) {
            getNavigation().stop();
            // he holds out his bowl... and the machine blasts crushed ice right into his face
            Vec3 from = y instanceof GolaMachineBlockEntity g ? g.wheelWorld() : Vec3.atCenterOf(y.getBlockPos()).add(0, 0.6, 0);
            Vec3 face = new Vec3(getX(), getEyeY(), getZ());
            for (int i = 0; i <= 12; i++) {
                double t = i / 12.0;
                Vec3 p = from.lerp(face, t).add(0, Math.sin(t * Math.PI) * 0.35, 0);
                sl.sendParticles(ModParticles.ICE_SHARD.get(), p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0.03);
            }
            Vec3 dir = face.subtract(from).normalize();
            for (int i = 0; i < 24; i++) {
                sl.sendParticles(ModParticles.ICE_SHARD.get(), from.x, from.y, from.z, 0, dir.x + (random.nextDouble() - 0.5) * 0.3,
                        dir.y + (random.nextDouble() - 0.3) * 0.3, dir.z + (random.nextDouble() - 0.5) * 0.3, 0.9);
            }
            sl.sendParticles(ModParticles.SNOWFLAKE.get(), face.x, face.y, face.z, 40, 0.25, 0.25, 0.25, 0.08);
            sl.sendParticles(ParticleTypes.CLOUD, face.x, face.y, face.z, 10, 0.2, 0.2, 0.2, 0.03);
            sl.playSound(null, blockPosition(), ModSounds.FREEZE.get(), SoundSource.NEUTRAL, 1.4f, 1.2f);
            sl.playSound(null, blockPosition(), ModSounds.HANDLE_POP.get(), SoundSource.NEUTRAL, 1.0f, 1.4f);
            Vec3 knock = position().subtract(Vec3.atBottomCenterOf(y.getBlockPos())).multiply(1, 0, 1);
            if (knock.lengthSqr() > 1e-4) knock = knock.normalize();
            setDeltaMovement(knock.x * 0.3, 0.25, knock.z * 0.3);
            hurtMarked = true;
            threat = Vec3.atCenterOf(y.getBlockPos());
            setState(BLASTED);
        }
    }

    private void tickFrontSit(ServerLevel sl) {
        YantraBlockEntity y = anchor();
        if (y == null) {
            scare(null);
            return;
        }
        if (sitSpot != null) setPos(sitSpot.x, sitSpot.y, sitSpot.z);
        setDeltaMovement(Vec3.ZERO);
        float yaw = y.facing().getOpposite().toYRot();
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw + Mth.sin(stateTicks * 0.07f) * 12f);
        boolean running = y.phase() != 0;
        if (running && stateTicks % 5 == 0) {
            sl.sendParticles(ModParticles.SNOWFLAKE.get(), getX(), getY() + 1.2, getZ(), 2, 0.3, 0.3, 0.3, 0.01);
        }
        if (stateTicks % 90 == 30) {
            playSound(ModSounds.VILLAGER_RELIEF.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.4, getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        }
        if (!running && stateTicks > 1200) scare(null);
    }

    // giant hairdryer finale -------------------------------------------------------------------
    /** Nozzle tip in world space (the dryer is held in front, barrel pointing forward). */
    public Vec3 nozzle() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        return position().add(fx * 4.0, 4.4, fz * 4.0);
    }

    private void tickHairdryer(ServerLevel sl) {
        Vec3 n = nozzle();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // massive blast of hot air and flame out of the nozzle (purely visual: the ice never melts)
        for (int i = 0; i < 6; i++) {
            double sp = 0.6 + random.nextDouble() * 0.5;
            sl.sendParticles(ParticleTypes.FLAME, n.x, n.y, n.z, 0, fx + (random.nextDouble() - 0.5) * 0.35, -0.25 + (random.nextDouble() - 0.5) * 0.3,
                    fz + (random.nextDouble() - 0.5) * 0.35, sp);
        }
        if (stateTicks % 2 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, n.x, n.y, n.z, 0, fx * 0.8, -0.1, fz * 0.8, 0.5);
            sl.sendParticles(ParticleTypes.SMOKE, n.x, n.y, n.z, 4, 0.3, 0.3, 0.3, 0.05);
        }
        if (stateTicks % 5 == 0) {
            // heat haze lands on the ice: it hisses and steams... and stays frozen
            Vec3 hit = n.add(fx * 6, -4.0, fz * 6);
            sl.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y, hit.z, 6, 1.2, 0.3, 1.2, 0.02);
            sl.sendParticles(ModParticles.SNOWFLAKE.get(), hit.x, hit.y + 0.5, hit.z, 6, 1.2, 0.3, 1.2, 0.03);
        }
        if (stateTicks % 20 == 0) {
            sl.playSound(null, BlockPos.containing(n), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.0f, 0.5f + random.nextFloat() * 0.2f);
            sl.playSound(null, blockPosition(), ModSounds.FAN_WHOOSH.get(), SoundSource.NEUTRAL, 1.4f, 0.6f);
        }
        if (stateTicks % 37 == 0) {
            sl.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.9f, 0.8f);
        }
        if (stateTicks % 45 == 10) playSound(SoundEvents.VILLAGER_HURT, 0.9f, 0.7f);  // grunting under the weight
        if (stateTicks == 320) dialogue(sl, position(), "Ye barf pighal kyun nahi rahi?!");
        if (stateTicks >= 600) {
            // the dryer overheats and dies, the world stays frozen, Gotiya runs off crying
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, n.x, n.y, n.z, 40, 1.0, 0.8, 1.0, 0.05);
            sl.sendParticles(ParticleTypes.EXPLOSION, n.x, n.y, n.z, 2, 0.3, 0.3, 0.3, 0.0);
            sl.playSound(null, blockPosition(), ModSounds.SPUTTER.get(), SoundSource.NEUTRAL, 1.6f, 0.7f);
            dialogue(sl, position(), "Nahiii! Ye barf kabhi nahi pighlegi!");
            entityData.set(DATA_CRYING, true);
            scare(null);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return hasHairdryer() ? getBoundingBox().inflate(5.0, 6.0, 5.0) : super.getBoundingBoxForCulling();
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

    /** Scream, hop up and run away (normal villager panic speed). */
    public void scare(@Nullable Player by) {
        if (state() == FLEEING) return;
        boolean wasInFridge = state() == SITTING;
        RetroFridgeBlockEntity f = fridge();
        if (by != null) threat = by.position();
        else if (threat == null) threat = fridgePos != null ? Vec3.atCenterOf(fridgePos) : position().add(random.nextGaussian(), 0, random.nextGaussian());
        setState(FLEEING);
        noPhysics = false;
        setNoGravity(false);
        sitSpot = null;
        playSound(ModSounds.VILLAGER_SCREAM.get(), 1.6f, 1.0f);
        Direction out = f != null ? f.facing() : getDirection();
        if (wasInFridge && f != null) {
            f.onVillagerLeft();
            Vec3 p = Vec3.atBottomCenterOf(fridgePos).add(out.getStepX() * 1.0, 0.05, out.getStepZ() * 1.0);
            setPos(p.x, p.y, p.z);
        }
        Vec3 away = position().subtract(threat).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(out.getStepX(), 0, out.getStepZ());
        away = away.normalize();
        setDeltaMovement(away.x * 0.25, 0.42, away.z * 0.25);
        hurtMarked = true;
        getNavigation().stop();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.8, getZ(), 20, 0.3, 0.3, 0.3, 0.2);
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private void clientFx() {
        int s = state();
        if ((s == WALKING || s == BOWL || s == FRONT_WALK || s == HAIRDRYER) && random.nextInt(4) == 0) {
            // sweating buckets
            level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.9, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
        if (isCrying() && s == FLEEING) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), sx = Mth.cos(yaw), sz = Mth.sin(yaw);
            for (int k = -1; k <= 1; k += 2) {
                level().addParticle(ParticleTypes.FALLING_WATER, getX() + fx * 0.28 + sx * 0.12 * k, getY() + 1.55, getZ() + fz * 0.28 + sz * 0.12 * k,
                        0, 0, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SPLASH, getX() + fx * 0.3, getY() + 1.5, getZ() + fz * 0.3, 0, 0.05, 0);
            }
        }
        if (isSitting()) {
            if (random.nextInt(5) == 0) {
                level().addParticle(ModParticles.SNOWFLAKE.get(), getX() + (random.nextDouble() - 0.5) * 0.7, getY() + 1.0 + random.nextDouble(),
                        getZ() + (random.nextDouble() - 0.5) * 0.7, 0, -0.01, 0);
            }
            if (tickCount % 18 == 0) {
                float yaw = getYRot() * Mth.DEG_TO_RAD;
                level().addParticle(ModParticles.FROST_MIST.get(), getX() - Mth.sin(yaw) * 0.35, getY() + 1.2, getZ() + Mth.cos(yaw) * 0.35,
                        -Mth.sin(yaw) * 0.02, 0.005, Mth.cos(yaw) * 0.02);
            }
        }
        if (s == HAIRDRYER && random.nextInt(2) == 0) {
            Vec3 n = nozzle();
            level().addParticle(ParticleTypes.FLAME, n.x + (random.nextDouble() - 0.5) * 0.6, n.y + (random.nextDouble() - 0.5) * 0.6,
                    n.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
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
        return !isSitting() && state() != HAIRDRYER;
    }

    @Override
    protected void pushEntities() {
        if (!isSitting()) super.pushEntities();
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSitting() || state() == HAIRDRYER ? null : SoundEvents.VILLAGER_AMBIENT;
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
        tag.putBoolean("crying", isCrying());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("fridge")) fridgePos = NbtUtils.readBlockPos(tag.getCompound("fridge"));
        int s = tag.getInt("chillState");
        // seated-in-front states resume walking so they find their spot again
        if (s == FRONT_SIT) s = FRONT_WALK;
        if (s == BLASTED) s = FLEEING;
        entityData.set(DATA_STATE, s);
        entityData.set(DATA_CRYING, tag.getBoolean("crying"));
        if (s == SITTING) {
            noPhysics = true;
            setNoGravity(true);
        }
        setCustomName(Component.literal(NAME));
        setCustomNameVisible(true);
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

    /** Walks Gotiya to the spot right in front of a Gola / Fan / AC. */
    private class WalkToYantraGoal extends Goal {
        private int repath;

        WalkToYantraGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            int s = state();
            return (s == BOWL || s == FRONT_WALK) && fridgePos != null;
        }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 8;
            YantraBlockEntity y = anchor();
            if (y == null) return;
            Vec3 spot = frontSpot(y);
            if (!getNavigation().moveTo(spot.x, spot.y, spot.z, state() == BOWL ? 0.75 : 0.6)) {
                // no path (e.g. slippery ice): just shuffle straight there
                getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.6);
            }
        }
    }

    /** Lurches slowly forward under the weight of the giant hairdryer, swinging it around. */
    private class HairdryerGoal extends Goal {
        private int repath;

        HairdryerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return state() == HAIRDRYER;
        }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 40;
            Vec3 c = threat != null ? threat : position();
            double a = random.nextDouble() * Math.PI * 2;
            double r = 5 + random.nextDouble() * 4;
            getNavigation().moveTo(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0.35);
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
                if (target != null) getNavigation().moveTo(target.x, target.y, target.z, FLEE_SPEED);
            }
        }
    }
}
