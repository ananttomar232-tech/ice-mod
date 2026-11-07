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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Click -> SPEED 3. Roaring wind + frost stream freeze a 15 block cone and the clicker gets a gentle
 * comedic 2-3 block backwards nudge. Stops as soon as the owner reaches 150 frozen blocks.
 */
public class FarrataFanBlockEntity extends YantraBlockEntity {
    public static final int IDLE = 0, RUNNING = 1, WINDDOWN = 2, DONE = 3;
    private final List<BlockPos> cone = new ArrayList<>();
    private int coneIndex;
    private int maxStage = FreezeEngine.ICE;

    public float bladeAngle, prevBladeAngle, bladeSpeed, shake;

    public FarrataFanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FAN.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return Tier.FAN;
    }

    @Override
    public void onUse(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (phase == RUNNING) return;
        owner = player.getUUID();
        beginRun();
        // Tier 2 gag: Gotiya walks over and sits right in front of the fan, shivering happily
        if (!gotiyaAround(level)) {
            trackGotiya(com.iceage.yantra.entity.ChillVillagerEntity.forFront(level, this));
        }
        buildCone(level);
        setPhase(RUNNING);
        level.playSound(null, worldPosition, ModSounds.FAN_WHOOSH.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        say(player, "msg.ice_age_yantra.fan.speed3", ChatFormatting.AQUA);
        // gentle comedic backwards nudge: ~2.5 blocks, then you are right back in front of the action
        Direction f = facing();
        double s = Config.FAN_NUDGE_STRENGTH.get();
        player.setDeltaMovement(player.getDeltaMovement().add(f.getStepX() * s, 0.32, f.getStepZ() * s));
        player.hurtMarked = true;
    }

    private void buildCone(ServerLevel level) {
        cone.clear();
        coneIndex = 0;
        Direction f = facing();
        Direction side = f.getClockWise();
        int len = Config.FAN_CONE_LENGTH.get();
        for (int d = 1; d <= len; d++) {
            int half = Math.max(1, (int) Math.round(d * 0.58));
            for (int l = -half; l <= half; l++) {
                int x = worldPosition.getX() + f.getStepX() * d + side.getStepX() * l;
                int z = worldPosition.getZ() + f.getStepZ() * d + side.getStepZ() * l;
                BlockPos s = FreezeEngine.surface(level, x, z, worldPosition.getY(), 3, 5);
                if (s != null) cone.add(s);
                for (int y = 0; y <= 3; y++) {
                    BlockPos w = new BlockPos(x, worldPosition.getY() + y, z);
                    if (s != null && w.getY() <= s.getY()) continue;
                    BlockState st = level.getBlockState(w);
                    if (!st.isAir() && !FreezeEngine.isPlant(st)) cone.add(w);
                }
            }
        }
        BlockPos c = worldPosition;
        cone.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (phase == RUNNING) {
            running(level);
        } else if (phase == WINDDOWN) {
            if (phaseTicks == 1) level.playSound(null, worldPosition, ModSounds.SQUEAK.get(), SoundSource.BLOCKS, 0.8f, 0.7f);
            if (phaseTicks >= 70) setPhase(IDLE);
        }
    }

    private void running(ServerLevel level) {
        if (cone.isEmpty()) buildCone(level);
        Direction f = facing();
        Vec3 hub = rigToWorld(RigData.FAN_ROTOR_X, RigData.FAN_ROTOR_Y, RigData.FAN_ROTOR_Z - 0.15);
        for (int i = 0; i < 4; i++) {
            double spread = 0.35;
            double vx = f.getStepX() + (level.random.nextDouble() - 0.5) * spread * 2;
            double vz = f.getStepZ() + (level.random.nextDouble() - 0.5) * spread * 2;
            double vy = (level.random.nextDouble() - 0.6) * 0.25;
            level.sendParticles(ModParticles.SNOWFLAKE.get(), hub.x, hub.y, hub.z, 0, vx, vy, vz, 0.75);
        }
        if (phaseTicks % 3 == 0) {
            level.sendParticles(ModParticles.FROST_MIST.get(), hub.x, hub.y, hub.z, 0, f.getStepX(), -0.05, f.getStepZ(), 0.35);
        }
        // blow loose entities (not players: they already got their nudge)
        AABB box = new AABB(worldPosition).expandTowards(f.getStepX() * 6, 1, f.getStepZ() * 6).inflate(1.2, 0.5, 1.2);
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof ItemEntity || (e instanceof LivingEntity && !(e instanceof net.minecraft.world.entity.player.Player)
                && !(e instanceof com.iceage.yantra.entity.ChillVillagerEntity)))) {
            e.push(f.getStepX() * 0.06, 0.01, f.getStepZ() * 0.06);
            e.hurtMarked = true;
        }
        if (targetReached()) {
            setPhase(WINDDOWN);
            return;
        }
        if (phaseTicks % Math.max(1, Config.FAN_TICKS_PER_FREEZE.get()) != 0) return;
        int changed = 0;
        int tries = 0;
        while (changed == 0 && !cone.isEmpty() && tries++ < cone.size()) {
            if (coneIndex >= cone.size()) {
                coneIndex = 0;
            }
            BlockPos p = cone.get(coneIndex++);
            changed = FreezeEngine.advance(level, p, maxStage);
            if (changed > 0) {
                level.sendParticles(ModParticles.SNOWFLAKE.get(), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.01);
                if (level.random.nextInt(3) == 0) {
                    level.playSound(null, p, ModSounds.FREEZE.get(), SoundSource.BLOCKS, 0.5f, 1.2f + level.random.nextFloat() * 0.3f);
                }
            }
        }
        if (changed == 0 && maxStage < FreezeEngine.BLUE) maxStage++;
        if (pay(1)) setPhase(WINDDOWN);
    }

    public Vec3 rigToWorld(double x, double y, double z) {
        double dx = x - 0.5, dz = z - 0.5;
        double rot = Math.toRadians(180f - facing().toYRot());
        double c = Math.cos(rot), s = Math.sin(rot);
        return new Vec3(worldPosition.getX() + 0.5 + dx * c + dz * s, worldPosition.getY() + y, worldPosition.getZ() + 0.5 - dx * s + dz * c);
    }

    @Override
    protected void clientTick() {
        prevBladeAngle = bladeAngle;
        float target = phase == RUNNING ? 62f : 0f;
        bladeSpeed += (target - bladeSpeed) * (phase == RUNNING ? 0.06f : 0.025f);
        if (bladeSpeed < 0.05f) bladeSpeed = 0;
        bladeAngle += bladeSpeed;
        shake = phase == RUNNING ? bladeSpeed / 62f : 0f;
        if (bladeSpeed > 1f) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.iceage.yantra.client.ClientHooks.ensureFanLoop(this));
        }
    }

    @Override
    protected void saveSync(CompoundTag tag) {
        super.saveSync(tag);
        tag.putInt("maxStage", maxStage);
    }

    @Override
    protected void loadSync(CompoundTag tag) {
        super.loadSync(tag);
        maxStage = Math.max(FreezeEngine.ICE, tag.getInt("maxStage"));
    }
}
