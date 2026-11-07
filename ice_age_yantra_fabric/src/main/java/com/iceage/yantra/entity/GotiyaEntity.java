package com.iceage.yantra.entity;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.ModEntities;
import com.iceage.yantra.ModParticles;
import com.iceage.yantra.ModSounds;
import com.iceage.yantra.Tier;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
 * Gotiya, the hot-headed villager (bold aqua name, panic speed 0.5). Only ONE exists at any time.
 * Tier 1: shouts at the Gola. Tier 2: sits shivering in front of the fan. Tier 3: sits shivering inside the open
 * fridge. Van climax: chases behind the van with a giant hairdryer and melts every frozen block. Hit him and the
 * hairdryer vanishes and he flees.
 */
public class GotiyaEntity extends PathAwareEntity implements VillagerDataContainer {
    public static final int IDLE = 0, DRYER = 1, FLEEING = 2, WALK = 3, SIT = 4, FRIDGE = 5;
    public static final float PANIC_SPEED = 0.5F;
    public static final String GOLA_LINE = "Main tujhe world freeze nahi karne dunga!";
    private static final TrackedData<Integer> STATE = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<VillagerData> VDATA = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.VILLAGER_DATA);
    private static final String[] FAN_LINES = {"Brrr... yeh Farrata Fan toh barf ki hawa de raha hai!", "Mere daant baj rahe hain... band karo isse!",
            "Itni thand! Main jam jaunga!", "Kaun si machine hai yeh... brrr!"};
    private static final String[] FRIDGE_LINES = {"Brrr... fridge ke andar bahut thand hai!", "Mujhe bahar nikalo... par yahan mazaa bhi aa raha hai!",
            "Thand se meri naak jam gayi!", "Brrr... brrr... brrr!"};

    private int stateTicks;
    private boolean climax;
    private boolean checkedUnique;
    private int gagTier;
    @Nullable
    private BlockPos anchor;
    @Nullable
    private Vec3d spot;
    @Nullable
    private Vec3d threat;

    public GotiyaEntity(EntityType<? extends GotiyaEntity> type, World world) {
        super(type, world);
        setPersistent();
        applyName();
    }

    private void applyName() {
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

    public boolean isSitting() {
        int s = state();
        return s == SIT || s == FRIDGE;
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

    // ---------------------------------------------------------------------------------------- immunity
    /** Ice around his head must never choke or glitch him. */
    @Override
    public boolean isInsideWall() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
    }

    // ---------------------------------------------------------------------------------------- spawning
    /** Dialogue as a subtitle (action bar) for everyone nearby. */
    public static void dialogue(ServerWorld level, Vec3d at, String line) {
        Text msg = Text.literal("Gotiya: ").formatted(Formatting.AQUA, Formatting.BOLD).append(Text.literal(line).formatted(Formatting.WHITE));
        for (ServerPlayerEntity p : level.getPlayers()) {
            if (p.getPos().squaredDistanceTo(at) < 48 * 48) p.sendMessage(msg, true);
        }
    }

    /** Only one Gotiya exists in the world at any time: every other one is despawned. */
    public static void removeOthers(net.minecraft.server.MinecraftServer server, GotiyaEntity keep) {
        for (ServerWorld w : server.getWorlds()) {
            for (GotiyaEntity g : w.getEntitiesByType(ModEntities.GOTIYA, e -> e != keep)) {
                w.spawnParticles(ParticleTypes.POOF, g.getX(), g.getY() + 0.8, g.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                g.discard();
            }
        }
    }

    private static Vec3d groundNear(ServerWorld w, double x, double z, double fallbackY) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return new Vec3d(x, fallbackY, z);
        return new Vec3d(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz), z);
    }

    private static GotiyaEntity create(ServerWorld w, Vec3d p) {
        GotiyaEntity g = ModEntities.GOTIYA.create(w);
        if (g == null) return null;
        g.refreshPositionAndAngles(p.x, p.y, p.z, w.random.nextFloat() * 360f, 0f);
        g.initialize(w, w.getLocalDifficulty(g.getBlockPos()), SpawnReason.EVENT, null, null);
        g.applyName();
        return g;
    }

    /** The per-machine gags. Returns null for machines without one (Split AC). */
    @Nullable
    public static GotiyaEntity spawnGag(ServerWorld w, Tier tier, BlockPos machine, Direction facing, ServerPlayerEntity by) {
        if (tier != Tier.GOLA && tier != Tier.FAN && tier != Tier.FRIDGE) return null;
        double d = 6 + w.random.nextDouble() * 3;
        double ang = Math.toRadians(facing.asRotation() + (w.random.nextDouble() - 0.5) * 100);
        Vec3d start = groundNear(w, machine.getX() + 0.5 - Math.sin(ang) * d, machine.getZ() + 0.5 + Math.cos(ang) * d, machine.getY());
        GotiyaEntity g = create(w, start);
        if (g == null) return null;
        g.anchor = machine;
        g.gagTier = tier.number;
        double front = tier == Tier.GOLA ? 3.0 : tier == Tier.FAN ? 2.4 : 1.1;
        Vec3d s = Vec3d.ofBottomCenter(machine).add(facing.getOffsetX() * front, 0, facing.getOffsetZ() * front);
        g.spot = groundNear(w, s.x, s.z, machine.getY());
        if (g.spot.y < machine.getY() - 1) g.spot = new Vec3d(s.x, machine.getY(), s.z);
        g.setState(WALK);
        w.spawnEntity(g);
        removeOthers(w.getServer(), g);
        g.checkedUnique = true;
        w.spawnParticles(ParticleTypes.POOF, start.x, start.y + 0.5, start.z, 12, 0.3, 0.5, 0.3, 0.02);
        if (tier == Tier.GOLA) {
            g.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.3f, 0.9f);
            dialogue(w, start, GOLA_LINE);
        }
        return g;
    }

    /** The van climax (and /iceage gotiya): Gotiya with the giant hairdryer. */
    @Nullable
    public static GotiyaEntity spawnClimax(ServerWorld world, Vec3d at, @Nullable LivingEntity target) {
        int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        GotiyaEntity g = create(world, new Vec3d(x + 0.5, y, z + 0.5));
        if (g == null) return null;
        g.climax = true;
        if (target != null) g.threat = target.getPos();
        g.setState(DRYER);
        world.spawnEntity(g);
        removeOthers(world.getServer(), g);
        g.checkedUnique = true;
        world.spawnParticles(ParticleTypes.POOF, x + 0.5, y + 0.5, z + 0.5, 14, 0.3, 0.5, 0.3, 0.02);
        return g;
    }

    // ---------------------------------------------------------------------------------------- tick
    @Nullable
    private IceVanEntity findVan() {
        IceVanEntity best = null;
        double bd = 150 * 150;
        for (IceVanEntity v : getWorld().getEntitiesByClass(IceVanEntity.class, getBoundingBox().expand(150), e -> true)) {
            double d = v.squaredDistanceTo(this);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
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
        if (!checkedUnique) {
            checkedUnique = true;
            removeOthers(sw.getServer(), this);
        }
        switch (state()) {
            case DRYER -> tickDryer(sw);
            case WALK -> tickWalk(sw);
            case SIT, FRIDGE -> tickSeated(sw);
            case FLEEING -> {
                if (stateTicks % 25 == 5) playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.0f, 1.0f + random.nextFloat() * 0.3f);
                if (stateTicks >= 160) {
                    sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
                    discard();
                }
            }
            default -> {
                if (stateTicks > 6000) discard();
            }
        }
    }

    private boolean machineGone(ServerWorld sw) {
        return anchor == null || !(sw.getBlockState(anchor).getBlock() instanceof com.iceage.yantra.block.YantraBlock);
    }

    private void tickWalk(ServerWorld sw) {
        if (spot == null || machineGone(sw) || stateTicks > 1500) {
            flee(null);
            return;
        }
        double dist = getPos().multiply(1, 0, 1).distanceTo(spot.multiply(1, 0, 1));
        if (dist < 1.3) {
            getNavigation().stop();
            if (gagTier == Tier.FAN.number) {
                setState(SIT);
                playSound(ModSounds.SQUEAK, 0.7f, 1.2f);
            } else if (gagTier == Tier.FRIDGE.number) {
                setState(FRIDGE);
                playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 0.6f, 1.0f);
            } else {
                // Gola: stands in front of the machine shouting
                if (stateTicks % 70 == 0) {
                    playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dialogue(sw, getPos(), GOLA_LINE);
                }
                getLookControl().lookAt(Vec3d.ofCenter(anchor));
            }
            return;
        }
        if (stateTicks % 8 == 0 && !getNavigation().startMovingTo(spot.x, spot.y, spot.z, 0.75)) {
            getMoveControl().moveTo(spot.x, spot.y, spot.z, 0.6);
        }
        if (stateTicks % 60 == 0) playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.8f, 1.3f);
    }

    private void tickSeated(ServerWorld sw) {
        if (machineGone(sw) || anchor == null) {
            flee(null);
            return;
        }
        Direction f = sw.getBlockState(anchor).contains(com.iceage.yantra.block.YantraBlock.FACING)
                ? sw.getBlockState(anchor).get(com.iceage.yantra.block.YantraBlock.FACING) : Direction.NORTH;
        Vec3d seat;
        if (state() == FRIDGE) {
            seat = Vec3d.ofBottomCenter(anchor).add(-f.getOffsetX() * 0.08, 0.06, -f.getOffsetZ() * 0.08);
        } else {
            seat = spot != null ? spot : getPos();
        }
        setPosition(seat.x, seat.y, seat.z);
        setVelocity(Vec3d.ZERO);
        float yaw = f.getOpposite().asRotation();
        if (state() == FRIDGE) yaw = f.asRotation();
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw + MathHelper.sin(stateTicks * 0.07f) * 14f);
        if (stateTicks % 70 == 20) {
            String[] lines = state() == FRIDGE ? FRIDGE_LINES : FAN_LINES;
            dialogue(sw, getPos(), lines[random.nextInt(lines.length)]);
            playSound(ModSounds.SQUEAK, 0.5f, 1.4f);
        }
        if (stateTicks % 5 == 0) sw.spawnParticles(ModParticles.SNOWFLAKE, getX(), getY() + 1.2, getZ(), 2, 0.3, 0.3, 0.3, 0.01);
    }

    /** The climax: chase behind the van, blast hot air, melt 100% of the frozen blocks around. */
    private void tickDryer(ServerWorld sw) {
        IceVanEntity van = findVan();
        Vec3d target;
        if (van != null) {
            target = van.getPos();
            double yawR = Math.toRadians(van.getYaw());
            Vec3d behind = van.getPos().add(Math.sin(yawR) * 11, 0, -Math.cos(yawR) * 11);
            double dist = getPos().multiply(1, 0, 1).distanceTo(behind.multiply(1, 0, 1));
            if (dist > 40) {
                Vec3d g = groundNear(sw, behind.x, behind.z, van.getY());
                refreshPositionAndAngles(g.x, g.y, g.z, getYaw(), 0f);
                getNavigation().stop();
            } else if (dist > 3) {
                if (stateTicks % 4 == 0) getNavigation().startMovingTo(behind.x, behind.y, behind.z, 2.4);
            } else {
                getNavigation().stop();
            }
        } else {
            PlayerEntity p = getWorld().getClosestPlayer(this, 100);
            if (p == null) {
                if (stateTicks > 400) discard();
                return;
            }
            target = p.getPos();
            getNavigation().stop();
        }
        Vec3d to = target.subtract(getPos());
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hd < 1.0e-3) return;
        double dx = to.x / hd, dz = to.z / hd;
        float yaw = (float) (MathHelper.atan2(-dx, dz) * MathHelper.DEGREES_PER_RADIAN);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);

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
        // melt EVERYTHING frozen in a wide area between him and the van, rapidly
        Vec3d c = getPos().add(to.multiply(0.3));
        int cx = MathHelper.floor(c.x), cz = MathHelper.floor(c.z), cy = MathHelper.floor(getY());
        int melted = 0;
        outer:
        for (int ix = -16; ix <= 16; ix++) {
            for (int iz = -16; iz <= 16; iz++) {
                if (ix * ix + iz * iz > 16 * 16) continue;
                melted += FreezeEngine.meltColumn(sw, cx + ix, cz + iz, cy);
                if (melted >= 160) break outer;
            }
        }
        if (melted > 0) {
            sw.spawnParticles(ModParticles.FROST_MIST, c.x, getY() + 0.6, c.z, 10, 3.0, 0.3, 3.0, 0.02);
            if (stateTicks % 6 == 0) sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.6f, 1.1f);
        }
        if (stateTicks % 40 == 10) playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.9f, 0.7f);
    }

    /** Scream and run away at normal panic speed; the hairdryer vanishes instantly. */
    public void flee(@Nullable PlayerEntity by) {
        if (state() == FLEEING) return;
        boolean hadDryer = hasHairdryer();
        threat = by != null ? by.getPos() : getPos().add(random.nextGaussian(), 0, random.nextGaussian());
        setState(FLEEING);
        getNavigation().stop();
        playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.4f, 1.0f);
        Vec3d away = getPos().subtract(threat).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        setVelocity(away.x * 0.2, 0.35, away.z * 0.2);
        velocityModified = true;
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1.8, getZ(), 18, 0.3, 0.3, 0.3, 0.2);
            sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.2, 0.3, 0.0);
            if (hadDryer) {
                // the giant hairdryer drops and vanishes in a puff of smoke
                Vec3d f = getRotationVec(1f).multiply(1, 0, 1).normalize();
                sw.spawnParticles(ParticleTypes.POOF, getX() + f.x * 3, getY() + 3.2, getZ() + f.z * 3, 30, 0.8, 0.8, 0.8, 0.05);
                sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX() + f.x * 3, getY() + 3.2, getZ() + f.z * 3, 14, 0.8, 0.8, 0.8, 0.03);
                sw.playSound(null, getBlockPos(), ModSounds.HANDLE_POP, SoundCategory.HOSTILE, 1.4f, 0.8f);
            }
        }
    }

    private void clientFx() {
        if (state() != FLEEING && !isSitting() && random.nextInt(4) == 0) {
            getWorld().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.9,
                    getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
        if (isSitting() && random.nextInt(5) == 0) {
            getWorld().addParticle(ModParticles.SNOWFLAKE, getX() + (random.nextDouble() - 0.5) * 0.7, getY() + 1.0 + random.nextDouble(),
                    getZ() + (random.nextDouble() - 0.5) * 0.7, 0, -0.01, 0);
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
        return state() != DRYER && !isSitting();
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
        nbt.putInt("gagTier", gagTier);
        if (anchor != null) nbt.putLong("anchor", anchor.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        climax = nbt.getBoolean("climax");
        gagTier = nbt.getInt("gagTier");
        if (nbt.contains("anchor")) anchor = BlockPos.fromLong(nbt.getLong("anchor"));
        int s = nbt.getInt("gotiyaState");
        // gags do not resume after a reload: he just wanders off
        dataTracker.set(STATE, s == DRYER ? DRYER : (s == FLEEING ? FLEEING : IDLE));
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
