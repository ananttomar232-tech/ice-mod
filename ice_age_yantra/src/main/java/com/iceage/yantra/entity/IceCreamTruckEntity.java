package com.iceage.yantra.entity;

import com.iceage.yantra.Config;
import com.iceage.yantra.progress.FreezeEngine;
import com.iceage.yantra.progress.PacingEngine;
import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModItems;
import com.iceage.yantra.registry.ModParticles;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tier 5 climax: a driveable musical ice cream van. While someone drives it, it lays a 10-wide blue ice
 * highway, ploughs a frozen corridor through anything in its way and flash-freezes houses, trees and terrain
 * around it. Points scale with a growing combo so the counter surges all the way to 10,000,000.
 */
public class IceCreamTruckEntity extends Entity {
    /** The van is drawn 1.4x bigger than its rig model (seat, hitbox and effects scale with it). */
    public static final float VAN_SCALE = 1.4f;
    private static final EntityDataAccessor<Float> DATA_MAX_SPEED = SynchedEntityData.defineId(IceCreamTruckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COMBO = SynchedEntityData.defineId(IceCreamTruckEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HONK = SynchedEntityData.defineId(IceCreamTruckEntity.class, EntityDataSerializers.INT);

    private float speed;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;
    @Nullable
    private UUID owner;
    @Nullable
    private Vec3 lastServerPos;
    private int drivingTicks;
    private int idleTicks;

    // client-side animation state
    public float wheelAngle, prevWheelAngle, steer, prevSteer, lean, prevLean, groundSpeed;
    private float lastYaw;
    private Object jingle;

    public IceCreamTruckEntity(EntityType<? extends IceCreamTruckEntity> type, Level level) {
        super(type, level);
        setMaxUpStep(1.1f);
        blocksBuilding = true;
    }

    public void setOwner(UUID id) {
        owner = id;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MAX_SPEED, 0.62f);
        entityData.define(DATA_COMBO, 1);
        entityData.define(DATA_HONK, 0);
    }

    public int combo() {
        return entityData.get(DATA_COMBO);
    }

    public float speed() {
        return speed;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (source.getEntity() instanceof Player p) {
            if (p.isShiftKeyDown() || p.getAbilities().instabuild) {
                if (!p.getAbilities().instabuild) spawnAtLocation(new ItemStack(ModItems.ICE_CREAM_VAN.get()));
                ejectPassengers();
                discard();
            } else {
                honk();
            }
            return true;
        }
        return false;
    }

    public void honk() {
        level().playSound(null, blockPosition(), ModSounds.HONK.get(), SoundSource.NEUTRAL, 1.6f, 1.0f);
        entityData.set(DATA_HONK, entityData.get(DATA_HONK) + 1);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            if (getPassengers().isEmpty() && player.startRiding(this)) {
                if (owner == null) owner = player.getUUID();
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof Player p ? p : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction fn) {
        if (!hasPassenger(passenger)) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double lx = com.iceage.yantra.RigData.TRUCK_SEAT_X * VAN_SCALE, lz = com.iceage.yantra.RigData.TRUCK_SEAT_Z * VAN_SCALE;
        double ox = lx * Mth.cos(yaw) - lz * Mth.sin(yaw);
        double oz = lx * Mth.sin(yaw) + lz * Mth.cos(yaw);
        fn.accept(passenger, getX() + ox, getY() + com.iceage.yantra.RigData.TRUCK_SEAT_Y * VAN_SCALE + passenger.getMyRidingOffset(), getZ() + oz);
    }

    @Override
    public double getPassengersRidingOffset() {
        return com.iceage.yantra.RigData.TRUCK_SEAT_Y * VAN_SCALE;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        for (double side : new double[]{-2.4, 2.4}) {
            double ox = side * Mth.cos(yaw);
            double oz = side * Mth.sin(yaw);
            Vec3 p = new Vec3(getX() + ox, getY() + 0.1, getZ() + oz);
            if (level().noCollision(passenger, passenger.getBoundingBox().move(p.subtract(passenger.position())))) return p;
        }
        return new Vec3(getX(), getBoundingBox().maxY + 0.1, getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(DATA_MAX_SPEED, Config.VAN_MAX_SPEED.get().floatValue());
        }
        tickLerp();
        float yawBefore = getYRot();
        if (isControlledByLocalInstance()) {
            drive();
            if (!onGround()) {
                setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
            } else if (getDeltaMovement().y < 0) {
                setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
            }
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision && Math.abs(speed) > 0.05f) {
                speed *= 0.5f;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        float dYaw = Mth.wrapDegrees(getYRot() - yawBefore);
        if (dYaw != 0) {
            for (Entity p : getPassengers()) {
                p.setYRot(p.getYRot() + dYaw);
                p.setYHeadRot(p.getYHeadRot() + dYaw);
            }
        }
        if (level().isClientSide) {
            clientAnim();
        } else {
            serverFreeze((ServerLevel) level());
        }
    }

    private void drive() {
        LivingEntity c = getControllingPassenger();
        float fwd = 0, strafe = 0;
        if (c instanceof Player p) {
            fwd = p.zza;
            strafe = p.xxa;
        }
        float max = entityData.get(DATA_MAX_SPEED);
        if (fwd > 0.01f) {
            speed += speed < 0 ? 0.05f : 0.018f + 0.01f * (1f - speed / max);
        } else if (fwd < -0.01f) {
            speed -= speed > 0 ? 0.045f : 0.012f;
        } else {
            speed *= 0.955f;
        }
        speed = Mth.clamp(speed, -max * 0.35f, max);
        if (Math.abs(speed) < 0.002f) speed = 0;
        float grip = Math.min(1f, Math.abs(speed) / 0.06f);
        float turn = strafe * (2.4f + 1.4f * Math.min(1f, Math.abs(speed) / max)) * grip * (speed >= 0 ? 1 : -1);
        setYRot(getYRot() - turn);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        setDeltaMovement(-Mth.sin(yaw) * speed, getDeltaMovement().y, Mth.cos(yaw) * speed);
    }

    // -------------------------------------------------------------- server: the highway + flash freeze
    private void serverFreeze(ServerLevel level) {
        LivingEntity driver = getControllingPassenger();
        Vec3 now = position();
        double moved = lastServerPos == null ? 0 : Math.sqrt(Math.pow(now.x - lastServerPos.x, 2) + Math.pow(now.z - lastServerPos.z, 2));
        lastServerPos = now;
        if (driver == null || moved < 0.015) {
            idleTicks++;
            if (idleTicks > 40) drivingTicks = Math.max(0, drivingTicks - 4);
            entityData.set(DATA_COMBO, comboFor(drivingTicks));
            return;
        }
        idleTicks = 0;
        drivingTicks++;
        UUID credit = driver.getUUID();
        int combo = comboFor(drivingTicks);
        entityData.set(DATA_COMBO, combo);

        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        int roadY = Mth.floor(getY() + 0.01) - 1;
        int half = Config.VAN_HIGHWAY_WIDTH.get() / 2;
        int changed = 0;
        java.util.HashSet<Long> done = new java.util.HashSet<>();
        for (double ahead = 0.5; ahead <= 7.0; ahead += 0.5) {
            for (double l = -half; l <= half; l += 0.5) {
                int x = Mth.floor(getX() + fx * ahead + rx * l);
                int z = Mth.floor(getZ() + fz * ahead + rz * l);
                if (!done.add(BlockPos.asLong(x, 0, z))) continue;
                changed += paveColumn(level, x, z, roadY, Math.abs(l) <= 2.8);
            }
        }
        // flash-freeze the surroundings: houses, trees, terrain
        int radius = Config.VAN_FREEZE_RADIUS.get();
        int samples = Config.VAN_SAMPLES_PER_TICK.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2.5 + Math.sqrt(random.nextDouble()) * (radius - 2.5);
            int x = Mth.floor(getX() + Math.cos(a) * r);
            int z = Mth.floor(getZ() + Math.sin(a) * r);
            int y = roadY - 2 + random.nextInt(17);
            m.set(x, y, z);
            BlockState s = level.getBlockState(m);
            if (s.isAir()) continue;
            changed += FreezeEngine.freezeTo(level, m, FreezeEngine.BLUE);
        }
        if (changed > 0 && PacingEngine.points(level.getServer(), credit) < Tier.FINAL_TARGET) {
            PacingEngine.award(level.getServer(), credit, (long) changed * Config.VAN_POINTS_PER_BLOCK.get() * combo);
        }
        // aggressive ice cannons: packed-ice blocks fly out left and right at high speed and coat the path
        if (tickCount % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                shootIce(level, fx, fz, rx * side, rz * side);
            }
        }
        // effects
        double bx = getX() - fx * 2.7, bz = getZ() - fz * 2.7;
        level.sendParticles(ModParticles.SNOWFLAKE.get(), bx, getY() + 0.4, bz, 6, 1.2, 0.3, 1.2, 0.05);
        level.sendParticles(ModParticles.FROST_MIST.get(), bx, getY() + 0.2, bz, 2, 1.0, 0.1, 1.0, 0.02);
        level.sendParticles(ModParticles.ICE_SHARD.get(), getX() + fx * 3.1, getY() + 1.1, getZ() + fz * 3.1, 4, 1.1, 0.8, 1.1, 0.25);
        if (tickCount % 7 == 0) {
            level.playSound(null, blockPosition(), ModSounds.FREEZE.get(), SoundSource.NEUTRAL, 0.8f, 0.6f + random.nextFloat() * 0.3f);
        }
    }

    private void shootIce(ServerLevel level, double fx, double fz, double sx, double sz) {
        double along = (random.nextDouble() - 0.4) * 2.4;
        double px = getX() + sx * 1.7 + fx * along, pz = getZ() + sz * 1.7 + fz * along;
        BlockPos spawn = BlockPos.containing(px, getY() + 2.2, pz);
        if (!level.getBlockState(spawn).isAir()) return;
        net.minecraft.world.entity.item.FallingBlockEntity fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, spawn,
                Blocks.PACKED_ICE.defaultBlockState());
        fb.dropItem = false;
        double out = 0.9 + random.nextDouble() * 0.7;
        double fwd = speed * 0.8 + (random.nextDouble() - 0.5) * 0.3;
        fb.setDeltaMovement(sx * out + fx * fwd, 0.35 + random.nextDouble() * 0.35, sz * out + fz * fwd);
        fb.hurtMarked = true;
        level.sendParticles(ModParticles.ICE_SHARD.get(), px, getY() + 2.2, pz, 6, 0.2, 0.2, 0.2, 0.3);
        if (random.nextInt(4) == 0) level.playSound(null, spawn, ModSounds.FAN_WHOOSH.get(), SoundSource.NEUTRAL, 0.5f, 1.7f);
    }

    private int paveColumn(ServerLevel level, int x, int z, int roadY, boolean corridor) {
        int changed = 0;
        BlockPos road = new BlockPos(x, roadY, z);
        BlockState rs = level.getBlockState(road);
        if (rs.isAir() || !rs.getFluidState().isEmpty() || FreezeEngine.isPlant(rs)) {
            if (!FreezeEngine.isProtected(level, road, rs) || rs.isAir()) {
                level.setBlock(road, Blocks.PACKED_ICE.defaultBlockState(), 18);
                changed++;
            }
            BlockPos under = road.below();
            BlockState us = level.getBlockState(under);
            if (us.isAir() || !us.getFluidState().isEmpty()) {
                level.setBlock(under, Blocks.PACKED_ICE.defaultBlockState(), 18);
                changed++;
            }
        } else {
            changed += FreezeEngine.freezeTo(level, road, FreezeEngine.PACKED);
        }
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos p = road.above(dy);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (corridor) {
                changed += FreezeEngine.shatter(level, p);
            } else if (dy <= 3) {
                changed += FreezeEngine.freezeTo(level, p, FreezeEngine.BLUE);
            }
        }
        return changed;
    }

    private int comboFor(int ticks) {
        int max = Config.VAN_MAX_COMBO.get();
        int ramp = Math.max(1, Config.VAN_COMBO_RAMP_SECONDS.get() * 20 / max);
        return Math.min(max, 1 + ticks / ramp);
    }

    // -------------------------------------------------------------- client: interpolation + animation
    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            double dyaw = Mth.wrapDegrees(lerpYRot - getYRot());
            setYRot(getYRot() + (float) dyaw / lerpSteps);
            --lerpSteps;
            setPos(nx, ny, nz);
            setRot(getYRot(), getXRot());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 6;
    }

    private void clientAnim() {
        prevWheelAngle = wheelAngle;
        prevSteer = steer;
        prevLean = lean;
        double dx = getX() - xo, dz = getZ() - zo;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        float fwd = (float) (dx * -Mth.sin(yaw) + dz * Mth.cos(yaw));
        groundSpeed = fwd;
        wheelAngle += fwd / (0.425f * VAN_SCALE) * Mth.RAD_TO_DEG;
        float dYaw = Mth.wrapDegrees(getYRot() - lastYaw);
        lastYaw = getYRot();
        steer += (Mth.clamp(-dYaw * 6f, -28f, 28f) - steer) * 0.3f;
        lean += (Mth.clamp(dYaw * Math.abs(fwd) * 9f, -6f, 6f) - lean) * 0.2f;
        if (!getPassengers().isEmpty() || Math.abs(fwd) > 0.02f) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.iceage.yantra.client.ClientHooks.ensureJingle(this));
        }
        if (Math.abs(fwd) > 0.08f && random.nextInt(2) == 0) {
            double bx = getX() + Mth.sin(yaw) * 2.7, bz = getZ() - Mth.cos(yaw) * 2.7;
            level().addParticle(ModParticles.FROST_MIST.get(), bx + (random.nextDouble() - 0.5) * 2, getY() + 0.15, bz + (random.nextDouble() - 0.5) * 2, 0, 0.01, 0);
        }
    }

    public Object jingleHandle() {
        return jingle;
    }

    public void setJingleHandle(Object o) {
        jingle = o;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.6, 2.6, 2.6);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ICE_CREAM_VAN.get());
    }
}
