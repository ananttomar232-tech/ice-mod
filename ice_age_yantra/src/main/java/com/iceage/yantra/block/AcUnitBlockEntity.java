package com.iceage.yantra.block;

import com.iceage.yantra.Config;
import com.iceage.yantra.RigData;
import com.iceage.yantra.item.AcRemoteItem;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.network.RemoteHudPacket;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * 16 C TURBO: a blizzard sweeps a 40 x 40 field in front of the unit row by row, packing it into ice at x16
 * points per block. At 50,000 frozen blocks the remote beeps and shows STANDBY.
 */
public class AcUnitBlockEntity extends YantraBlockEntity {
    public static final int IDLE = 0, TURBO = 1, STANDBY = 2;
    private int row, col, layer;
    private int stage = FreezeEngine.PACKED;

    public float fanAngle, prevFanAngle, fanSpeed;

    public AcUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AC.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return Tier.AC;
    }

    @Override
    public boolean wantsUse(Player player) {
        return !(player.getMainHandItem().getItem() instanceof AcRemoteItem);
    }

    @Override
    public void onUse(ServerPlayer player) {
        say(player, phase == TURBO ? "msg.ice_age_yantra.ac.running" : "msg.ice_age_yantra.ac.need_remote", ChatFormatting.AQUA);
    }

    /** Called by the Turbo Remote. Returns the LCD mode to show. */
    public int pressTurbo(ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level;
        if (phase == STANDBY || targetReachedFor(player)) {
            if (owner == null) owner = player.getUUID();
            if (phase != STANDBY) setPhase(STANDBY);
            level.playSound(null, worldPosition, ModSounds.REMOTE_STANDBY.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            return RemoteHudPacket.STANDBY;
        }
        owner = player.getUUID();
        if (phase != TURBO) {
            row = 0;
            col = 0;
            layer = 0;
            setPhase(TURBO);
        }
        level.playSound(null, worldPosition, ModSounds.REMOTE_BEEP.get(), SoundSource.BLOCKS, 1.0f, 1.2f);
        return RemoteHudPacket.TURBO;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (phase != TURBO) return;
        Direction f = facing();
        Direction side = f.getClockWise();
        Vec3 grille = rigToWorld(RigData.AC_ROTOR_X, RigData.AC_ROTOR_Y, RigData.AC_ROTOR_Z - 0.1);
        for (int i = 0; i < 7; i++) {
            double sp = (level.random.nextDouble() - 0.5) * 0.9;
            level.sendParticles(ModParticles.SNOWFLAKE.get(), grille.x, grille.y, grille.z, 0,
                    f.getStepX() + side.getStepX() * sp, 0.05 + level.random.nextDouble() * 0.2, f.getStepZ() + side.getStepZ() * sp,
                    1.1 + level.random.nextDouble() * 0.6);
        }
        if (phaseTicks % 2 == 0) {
            level.sendParticles(ModParticles.FROST_MIST.get(), grille.x, grille.y, grille.z, 0, f.getStepX(), 0.0, f.getStepZ(), 0.6);
        }
        if (targetReached()) {
            standby(level);
            return;
        }
        int area = Config.AC_AREA.get();
        int per = Config.AC_COLUMNS_PER_TICK.get();
        int changed = 0;
        int processed = 0;
        BlockPos waveCenter = null;
        while (processed < per) {
            int d = 2 + row;
            int l = col - area / 2;
            int x = worldPosition.getX() + f.getStepX() * d + side.getStepX() * l;
            int z = worldPosition.getZ() + f.getStepZ() * d + side.getStepZ() * l;
            BlockPos s = FreezeEngine.surface(level, x, z, worldPosition.getY(), 8, 12);
            if (s != null) {
                if (layer == 0) changed += FreezeEngine.freezeTo(level, s.above(), stage);
                changed += FreezeEngine.freezeTo(level, s.below(layer), stage);
                if (col == area / 2) waveCenter = s;
            }
            processed++;
            col++;
            if (col >= area) {
                col = 0;
                row++;
                if (row >= area) {
                    row = 0;
                    layer++;
                    if (layer > 4) {
                        layer = 0;
                        stage = Math.min(FreezeEngine.BLUE, stage + 1);
                    }
                }
            }
        }
        if (waveCenter != null) {
            level.sendParticles(ModParticles.SNOWFLAKE.get(), waveCenter.getX() + 0.5, waveCenter.getY() + 1.5, waveCenter.getZ() + 0.5,
                    20, side.getStepX() * 8 + 0.5, 0.8, side.getStepZ() * 8 + 0.5, 0.05);
            if (level.random.nextInt(3) == 0) {
                level.playSound(null, waveCenter, ModSounds.FREEZE.get(), SoundSource.BLOCKS, 0.8f, 0.7f + level.random.nextFloat() * 0.3f);
            }
        }
        if (changed == 0) changed = 1;
        if (pay((long) changed * Config.AC_POINTS_PER_BLOCK.get())) standby(level);
    }

    private void standby(ServerLevel level) {
        setPhase(STANDBY);
        level.playSound(null, worldPosition, ModSounds.REMOTE_STANDBY.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        if (owner != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p != null) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RemoteHudPacket(RemoteHudPacket.STANDBY));
                say(p, "msg.ice_age_yantra.ac.standby", ChatFormatting.AQUA);
            }
        }
    }

    public Vec3 rigToWorld(double x, double y, double z) {
        double dx = x - 0.5, dz = z - 0.5;
        double rot = Math.toRadians(180f - facing().toYRot());
        double c = Math.cos(rot), s = Math.sin(rot);
        return new Vec3(worldPosition.getX() + 0.5 + dx * c + dz * s, worldPosition.getY() + y, worldPosition.getZ() + 0.5 - dx * s + dz * c);
    }

    @Override
    protected void clientTick() {
        prevFanAngle = fanAngle;
        float target = phase == TURBO ? 55f : 0f;
        fanSpeed += (target - fanSpeed) * 0.05f;
        if (fanSpeed < 0.05f) fanSpeed = 0;
        fanAngle += fanSpeed;
        if (fanSpeed > 1f) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.iceage.yantra.client.ClientHooks.ensureAcLoop(this));
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }

    @Override
    protected void saveSync(CompoundTag tag) {
        super.saveSync(tag);
        tag.putInt("row", row);
        tag.putInt("col", col);
        tag.putInt("layer", layer);
        tag.putInt("stage", stage);
    }

    @Override
    protected void loadSync(CompoundTag tag) {
        super.loadSync(tag);
        row = tag.getInt("row");
        col = tag.getInt("col");
        layer = tag.getInt("layer");
        stage = Math.max(FreezeEngine.PACKED, tag.getInt("stage"));
    }
}
