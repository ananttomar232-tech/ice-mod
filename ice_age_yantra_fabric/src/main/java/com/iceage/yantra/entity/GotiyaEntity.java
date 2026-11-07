package com.iceage.yantra.entity;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Gotiya: a custom villager NPC with a bold aqua name tag. During the Ice Cream Van climax he stands in the van's
 * path holding an oversized industrial hairdryer and blasts flames and hot air at the ice, melting the frozen road
 * back into water. Hit him and he panics (walk speed 0.5).
 */
public class GotiyaEntity extends PathAwareEntity implements VillagerDataContainer {
    public static final int IDLE = 0, DRYER = 1, FLEEING = 2;
    /** Normal panic walk speed. */
    public static final float PANIC_SPEED = 0.5F;
    private static final TrackedData<Integer> STATE = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<VillagerData> VDATA = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.VILLAGER_DATA);

    private int stateTicks;
    private boolean climax;
    @Nullable
    private Vec3d threat;

    public GotiyaEntity(EntityType<? extends GotiyaEntity> type, World world) {
        super(type, world);
        setPersistent();
        applyName();
    }

    private void applyName() {
        // §b§lGotiya
        setCustomName(Text.literal("Gotiya").formatted(Formatting.AQUA, Formatting.BOLD));
        setCustomNameVisible(true);
    }

    public static DefaultAttributeContainer.Builder createGotiyaAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(STATE, IDLE);
        dataTracker.startTracking(VDATA, new VillagerData(VillagerType.DESERT, VillagerProfession.FARMER, 1));
    }

    @Override
    public VillagerData getVillagerData() {
        return dataTracker.get(VDATA);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        dataTracker.set(VDATA, data);
    }

    public int state() {
        return dataTracker.get(STATE);
    }

    public boolean hasHairdryer() {
        return state() == DRYER;
    }

    private void setState(int s) {
        dataTracker.set(STATE, s);
        stateTicks = 0;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new FleeGoal());
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
    }

    /** Spawns Gotiya with his hairdryer at the given position (snapped to the ground). */
    @Nullable
    public static GotiyaEntity spawnClimax(ServerWorld world, Vec3d at, @Nullable LivingEntity target) {
        int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        GotiyaEntity g = ModEntities.GOTIYA.create(world);
        if (g == null) return null;
        g.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0f, 0f);
        g.climax = true;
        g.initialize(world, world.getLocalDifficulty(g.getBlockPos()), SpawnReason.EVENT, null, null);
        g.applyName();
        if (target != null) g.threat = target.getPos();
        g.setState(DRYER);
        world.spawnEntity(g);
        world.spawnParticles(ParticleTypes.POOF, x + 0.5, y + 0.5, z + 0.5, 14, 0.3, 0.5, 0.3, 0.02);
        return g;
    }

    /** The thing Gotiya wants to melt a path towards: the closest driven van, otherwise the closest player. */
    @Nullable
    private Vec3d findTarget() {
        IceVanEntity best = null;
        double bd = 100 * 100;
        for (IceVanEntity v : getWorld().getEntitiesByClass(IceVanEntity.class, getBoundingBox().expand(100), e -> true)) {
            double d = v.squaredDistanceTo(this);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        if (best != null) return best.getPos();
        PlayerEntity p = getWorld().getClosestPlayer(this, 100);
        return p != null ? p.getPos() : null;
    }

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (getWorld().isClient) {
            clientFx();
            return;
        }
        ServerWorld sw = (ServerWorld) getWorld();
        switch (state()) {
            case DRYER -> tickDryer(sw);
            case FLEEING -> {
                if (stateTicks % 25 == 5) playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.0f, 1.0f + random.nextFloat() * 0.3f);
                if (stateTicks >= 160) {
                    Vec3d t = climax ? findTarget() : null;
                    if (t != null) {
                        setState(DRYER);
                    } else {
                        sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
                        discard();
                    }
                }
            }
            default -> {
                if (stateTicks > 6000) discard();
            }
        }
    }

    /** The climax attack: hot air and flames toward the ice, melting a cone of the frozen road. */
    private void tickDryer(ServerWorld sw) {
        Vec3d target = findTarget();
        if (target == null) {
            if (stateTicks > 400) discard();
            return;
        }
        getNavigation().stop();
        Vec3d to = target.subtract(getPos());
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hd < 1.0e-3) return;
        double dx = to.x / hd, dz = to.z / hd;
        float yaw = (float) (MathHelper.atan2(-dx, dz) * MathHelper.DEGREES_PER_RADIAN);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);

        // the nozzle of the (oversized) hairdryer
        double nx = getX() + dx * 4.0, ny = getY() + 4.4, nz = getZ() + dz * 4.0;   // mouth of the giant hairdryer
        for (int i = 0; i < 6; i++) {
            double sp = 0.7 + random.nextDouble() * 0.5;
            sw.spawnParticles(ParticleTypes.FLAME, nx, ny, nz, 0, dx + (random.nextDouble() - 0.5) * 0.35, -0.18 + (random.nextDouble() - 0.5) * 0.25,
                    dz + (random.nextDouble() - 0.5) * 0.35, sp);
        }
        if (stateTicks % 2 == 0) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, nx, ny, nz, 0, dx * 0.8, -0.05, dz * 0.8, 0.5);
            sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, nx, ny, nz, 0, dx * 0.6, 0.03, dz * 0.6, 0.4);
        }
        if (stateTicks % 20 == 0) {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            sw.playSound(null, getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 0.8f, 0.6f);
        }
        if (stateTicks % 4 == 0) {
            // melt a widening cone: frozen blocks become (briefly flowing) water, the ground stays
            double px = -dz, pz = dx;
            int melted = 0;
            for (int t = 2; t <= 16 && melted < 18; t++) {
                int half = Math.max(1, (int) Math.round(t * 0.22));
                for (int l = -half; l <= half; l++) {
                    int x = MathHelper.floor(getX() + dx * t + px * l);
                    int z = MathHelper.floor(getZ() + dz * t + pz * l);
                    melted += FreezeEngine.meltColumn(sw, x, z, MathHelper.floor(getY()));
                }
            }
            if (melted > 0) {
                sw.spawnParticles(ParticleTypes.CLOUD, getX() + dx * 7, getY() + 0.6, getZ() + dz * 7, 8, 2.0, 0.3, 2.0, 0.02);
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.5f, 1.1f);
            }
        }
        if (stateTicks % 40 == 10) playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.9f, 0.7f);
    }

    /** Scream and run away at normal panic speed. */
    public void flee(@Nullable PlayerEntity by) {
        if (state() == FLEEING) return;
        threat = by != null ? by.getPos() : getPos().add(random.nextGaussian(), 0, random.nextGaussian());
        setState(FLEEING);
        playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.4f, 1.0f);
        Vec3d away = getPos().subtract(threat).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        setVelocity(away.x * 0.2, 0.35, away.z * 0.2);
        velocityModified = true;
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1.8, getZ(), 18, 0.3, 0.3, 0.3, 0.2);
            sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private void clientFx() {
        if (state() != FLEEING && random.nextInt(4) == 0) {
            getWorld().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.9,
                    getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity p) flee(p);
        return false;
    }

    @Override
    public boolean isPushable() {
        return state() != DRYER;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("gotiyaState", state());
        nbt.putBoolean("climax", climax);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        climax = nbt.getBoolean("climax");
        int s = nbt.getInt("gotiyaState");
        dataTracker.set(STATE, s == FLEEING ? FLEEING : (s == DRYER ? DRYER : IDLE));
        applyName();
    }

    private class FleeGoal extends Goal {
        FleeGoal() {
            setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return state() == FLEEING;
        }

        @Override
        public void tick() {
            if (getNavigation().isIdle() && threat != null) {
                Vec3d target = NoPenaltyTargeting.findFrom(GotiyaEntity.this, 24, 7, threat);
                if (target != null) getNavigation().startMovingTo(target.x, target.y, target.z, PANIC_SPEED);
            }
        }
    }
}
