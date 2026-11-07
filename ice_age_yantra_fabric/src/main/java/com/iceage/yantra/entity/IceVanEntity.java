package com.iceage.yantra.entity;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.IcePoints;
import com.iceage.yantra.ModEntities;
import com.iceage.yantra.ModItems;
import com.iceage.yantra.ModParticles;
import com.iceage.yantra.Tier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Musical Ice Cream Van: a real custom vehicle entity (not a pig, not a vanilla mob). WASD driving with smooth
 * turning, the driver sits inside the cab, it lays a packed-ice road and fires snowballs and spinning ice
 * particles to its left and right. Driving it triggers Gotiya's hairdryer climax.
 */
public class IceVanEntity extends Entity {
    /** Visual and seat scale of the rig model. */
    public static final float SCALE = 1.4f;
    /** Set by the client mod: keeps the jingle playing while the van is driven (kept out of common code). */
    public static java.util.function.Consumer<IceVanEntity> jingleHook = v -> {
    };

    private static final TrackedData<Integer> COMBO = DataTracker.registerData(IceVanEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final double MAX_SPEED = 0.62;
    private static final int MAX_COMBO = 20;
    private static final int COMBO_RAMP_SECONDS = 40;
    private static final int POINTS_PER_BLOCK = 30;
    private static final int ROAD_HALF_WIDTH = 3;

    private float speed;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYaw;
    @Nullable
    private UUID owner;
    @Nullable
    private Vec3d lastServerPos;
    private int drivingTicks, idleTicks;
    private int climaxCooldown;
    @Nullable
    private UUID gotiya;

    // client-side animation state
    public float wheelAngle, prevWheelAngle, steer, prevSteer, lean, prevLean, groundSpeed;
    private float lastYaw;

    public IceVanEntity(EntityType<? extends IceVanEntity> type, World world) {
        super(type, world);
        setStepHeight(1.1f);
    }

    public void setOwner(UUID id) {
        owner = id;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(COMBO, 1);
    }

    public int combo() {
        return dataTracker.get(COMBO);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("owner", owner);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.ICE_CREAM_VAN);
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(2.8, 3.2, 2.8);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (getWorld().isClient || isRemoved()) return true;
        if (source.getAttacker() instanceof PlayerEntity p) {
            if (p.isSneaking() || p.getAbilities().creativeMode) {
                if (!p.getAbilities().creativeMode) dropStack(new ItemStack(ModItems.ICE_CREAM_VAN));
                removeAllPassengers();
                discard();
            } else {
                getWorld().playSound(null, getBlockPos(), com.iceage.yantra.ModSounds.VAN_HONK, SoundCategory.NEUTRAL, 1.6f, 1.0f);
            }
            return true;
        }
        return false;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        if (!getWorld().isClient) {
            if (getPassengerList().isEmpty() && player.startRiding(this)) {
                if (owner == null) owner = player.getUuid();
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty() && passenger instanceof PlayerEntity;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        if (!hasPassenger(passenger)) return;
        double lx = com.iceage.yantra.RigData.TRUCK_SEAT_X * SCALE, lz = com.iceage.yantra.RigData.TRUCK_SEAT_Z * SCALE;
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double ox = lx * MathHelper.cos(yaw) - lz * MathHelper.sin(yaw);
        double oz = lx * MathHelper.sin(yaw) + lz * MathHelper.cos(yaw);
        updater.accept(passenger, getX() + ox, getY() + com.iceage.yantra.RigData.TRUCK_SEAT_Y * SCALE + passenger.getHeightOffset(), getZ() + oz);
    }

    @Override
    public double getMountedHeightOffset() {
        return com.iceage.yantra.RigData.TRUCK_SEAT_Y * SCALE;
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (double side : new double[]{2.6, -2.6}) {
            double ox = side * MathHelper.cos(yaw);
            double oz = side * MathHelper.sin(yaw);
            Vec3d p = new Vec3d(getX() + ox, getY() + 0.1, getZ() + oz);
            if (getWorld().isSpaceEmpty(passenger, passenger.getBoundingBox().offset(p.subtract(passenger.getPos())))) return p;
        }
        return new Vec3d(getX(), getBoundingBox().maxY + 0.1, getZ());
    }

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        float yawBefore = getYaw();
        if (isLogicalSideForUpdatingMovement()) {
            drive();
            if (!isOnGround()) {
                setVelocity(getVelocity().add(0, -0.08, 0));
            } else if (getVelocity().y < 0) {
                setVelocity(getVelocity().multiply(1, 0, 1));
            }
            move(MovementType.SELF, getVelocity());
            if (horizontalCollision && Math.abs(speed) > 0.05f) speed *= 0.5f;
        } else {
            setVelocity(Vec3d.ZERO);
        }
        float dYaw = MathHelper.wrapDegrees(getYaw() - yawBefore);
        if (dYaw != 0) {
            for (Entity p : getPassengerList()) {
                p.setYaw(p.getYaw() + dYaw);
                p.setHeadYaw(p.getHeadYaw() + dYaw);
            }
        }
        if (getWorld().isClient) {
            clientAnim();
        } else {
            serverTick((ServerWorld) getWorld());
        }
    }

    private void drive() {
        LivingEntity c = getControllingPassenger();
        float fwd = 0, strafe = 0;
        if (c instanceof PlayerEntity p) {
            fwd = p.forwardSpeed;
            strafe = p.sidewaysSpeed;
        }
        float max = (float) MAX_SPEED;
        if (fwd > 0.01f) {
            speed += speed < 0 ? 0.05f : 0.018f + 0.01f * (1f - speed / max);
        } else if (fwd < -0.01f) {
            speed -= speed > 0 ? 0.045f : 0.012f;
        } else {
            speed *= 0.955f;
        }
        speed = MathHelper.clamp(speed, -max * 0.35f, max);
        if (Math.abs(speed) < 0.002f) speed = 0;
        float grip = Math.min(1f, Math.abs(speed) / 0.06f);
        float turn = strafe * (2.4f + 1.4f * Math.min(1f, Math.abs(speed) / max)) * grip * (speed >= 0 ? 1 : -1);
        setYaw(getYaw() - turn);
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        setVelocity(-MathHelper.sin(yaw) * speed, getVelocity().y, MathHelper.cos(yaw) * speed);
    }

    // ------------------------------------------------------------------ server: road, points, side cannons, climax
    private void serverTick(ServerWorld world) {
        LivingEntity driver = getControllingPassenger();
        Vec3d now = getPos();
        double moved = lastServerPos == null ? 0 : Math.sqrt(Math.pow(now.x - lastServerPos.x, 2) + Math.pow(now.z - lastServerPos.z, 2));
        lastServerPos = now;

        if (driver != null) climax(world, driver);

        if (driver == null || moved < 0.015) {
            idleTicks++;
            if (idleTicks > 40) drivingTicks = Math.max(0, drivingTicks - 4);
            dataTracker.set(COMBO, comboFor(drivingTicks));
            return;
        }
        idleTicks = 0;
        drivingTicks++;
        int combo = comboFor(drivingTicks);
        dataTracker.set(COMBO, combo);

        double yaw = Math.toRadians(getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        int roadY = MathHelper.floor(getY() + 0.01) - 1;
        int changed = 0;
        java.util.HashSet<Long> done = new java.util.HashSet<>();
        for (double ahead = 0.5; ahead <= 7.0; ahead += 0.5) {
            for (double l = -ROAD_HALF_WIDTH; l <= ROAD_HALF_WIDTH; l += 0.5) {
                int x = MathHelper.floor(getX() + fx * ahead + lx * l);
                int z = MathHelper.floor(getZ() + fz * ahead + lz * l);
                if (!done.add(BlockPos.asLong(x, 0, z))) continue;
                changed += FreezeEngine.paveColumn(world, x, z, roadY);
            }
        }
        if (changed > 0) {
            IcePoints.award(world.getServer(), driver.getUuid(), (long) changed * POINTS_PER_BLOCK * combo);
        }

        // side cannons: snowballs and spinning ice particles to the left and right
        if (age % 3 == 0) {
            for (int side = -1; side <= 1; side += 2) fireSnowball(world, side, fx, fz, lx, lz);
        }
        for (int side = -1; side <= 1; side += 2) spinParticles(world, side, fx, fz, lx, lz);
        if (age % 7 == 0) {
            world.playSound(null, getBlockPos(), com.iceage.yantra.ModSounds.FREEZE, SoundCategory.NEUTRAL, 0.8f, 0.6f + random.nextFloat() * 0.3f);
        }
        world.spawnParticles(ModParticles.FROST_MIST, getX() - fx * 2.7, getY() + 0.4, getZ() - fz * 2.7, 6, 1.2, 0.3, 1.2, 0.05);
    }

    private void fireSnowball(ServerWorld world, int side, double fx, double fz, double lx, double lz) {
        double ox = getX() + lx * side * 1.7 + fx * (random.nextDouble() * 2.0 - 0.6);
        double oz = getZ() + lz * side * 1.7 + fz * (random.nextDouble() * 2.0 - 0.6);
        SnowballEntity sb = new SnowballEntity(world, ox, getY() + 2.0, oz);
        double out = 1.1 + random.nextDouble() * 0.6;
        sb.setVelocity(lx * side * out + fx * speed * 0.9 + (random.nextDouble() - 0.5) * 0.2, 0.25 + random.nextDouble() * 0.3,
                lz * side * out + fz * speed * 0.9 + (random.nextDouble() - 0.5) * 0.2);
        world.spawnEntity(sb);
    }

    private void spinParticles(ServerWorld world, int side, double fx, double fz, double lx, double lz) {
        double cx = getX() + lx * side * 1.9;
        double cy = getY() + 1.6;
        double cz = getZ() + lz * side * 1.9;
        ItemStackParticleEffect ice = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.PACKED_ICE));
        for (int k = 0; k < 4; k++) {
            double a = age * 0.9 + k * Math.PI / 2;
            double ca = Math.cos(a), sa = Math.sin(a);
            // ring of radius 0.5 around the cannon axis (the side vector), spinning with the ticks
            double px = cx + fx * ca * 0.5;
            double pz = cz + fz * ca * 0.5;
            double py = cy + sa * 0.5;
            double vx = lx * side * 0.8 + fx * (-sa) * 0.18;
            double vz = lz * side * 0.8 + fz * (-sa) * 0.18;
            double vy = ca * 0.18;
            world.spawnParticles(k % 2 == 0 ? ModParticles.SNOWFLAKE : (k == 1 ? ModParticles.ICE_SHARD : ice), px, py, pz, 0, vx, vy, vz, 1.0);
        }
    }

    /** Gotiya spawns ahead in the van's path (and again, a while later, if he ran off). */
    private void climax(ServerWorld world, LivingEntity driver) {
        if (gotiya != null) {
            Entity g = world.getEntity(gotiya);
            if (g instanceof GotiyaEntity ge && ge.isAlive()) return;
            gotiya = null;
            climaxCooldown = 300;
        }
        if (climaxCooldown > 0) {
            climaxCooldown--;
            return;
        }
        double yaw = Math.toRadians(getYaw());
        Vec3d at = getPos().add(-Math.sin(yaw) * 30, 0, Math.cos(yaw) * 30);
        if (!world.isChunkLoaded(MathHelper.floor(at.x) >> 4, MathHelper.floor(at.z) >> 4)) return;
        GotiyaEntity g = GotiyaEntity.spawnClimax(world, at, driver);
        if (g != null) gotiya = g.getUuid();
    }

    private int comboFor(int ticks) {
        int ramp = Math.max(1, COMBO_RAMP_SECONDS * 20 / MAX_COMBO);
        return Math.min(MAX_COMBO, 1 + ticks / ramp);
    }

    // ------------------------------------------------------------------ client: interpolation + animation
    private void tickLerp() {
        if (isLogicalSideForUpdatingMovement()) {
            lerpSteps = 0;
            updateTrackedPosition(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            double dyaw = MathHelper.wrapDegrees(lerpYaw - getYaw());
            setYaw(getYaw() + (float) dyaw / lerpSteps);
            lerpSteps--;
            setPosition(nx, ny, nz);
            setRotation(getYaw(), getPitch());
        }
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpSteps = 6;
    }

    private void clientAnim() {
        prevWheelAngle = wheelAngle;
        prevSteer = steer;
        prevLean = lean;
        double dx = getX() - prevX, dz = getZ() - prevZ;
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float fwd = (float) (dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw));
        groundSpeed = fwd;
        wheelAngle += fwd / (0.425f * SCALE) * MathHelper.DEGREES_PER_RADIAN;
        float dYaw = MathHelper.wrapDegrees(getYaw() - lastYaw);
        lastYaw = getYaw();
        steer += (MathHelper.clamp(-dYaw * 6f, -28f, 28f) - steer) * 0.3f;
        lean += (MathHelper.clamp(dYaw * Math.abs(fwd) * 9f, -6f, 6f) - lean) * 0.2f;
        if (!getPassengerList().isEmpty() || Math.abs(fwd) > 0.02f) jingleHook.accept(this);
        if (Math.abs(fwd) > 0.08f && random.nextInt(2) == 0) {
            double bx = getX() + MathHelper.sin(yaw) * 2.7, bz = getZ() - MathHelper.cos(yaw) * 2.7;
            getWorld().addParticle(ParticleTypes.CLOUD, bx + (random.nextDouble() - 0.5) * 2, getY() + 0.15, bz + (random.nextDouble() - 0.5) * 2, 0, 0.01, 0);
        }
    }
}
