package com.iceage.yantra.block;

import com.iceage.yantra.progress.PacingEngine;
import com.iceage.yantra.progress.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared Yantra state machine. Subclasses implement the gag; the pacing engine decides when to stop:
 * a Yantra runs only while its owner's frozen-block counter is below the Yantra's target.
 */
public abstract class YantraBlockEntity extends BlockEntity {
    @Nullable
    protected UUID owner;
    protected int phase;
    protected int phaseTicks;
    public int age;

    protected YantraBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract Tier tier();

    public abstract void onUse(ServerPlayer player);

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {
    }

    public boolean wantsUse(Player player) {
        return true;
    }

    public int phase() {
        return phase;
    }

    public final void tick() {
        age++;
        if (level instanceof ServerLevel sl) {
            phaseTicks++;
            serverTick(sl);
        } else {
            clientTick();
        }
    }

    protected void setPhase(int p) {
        phase = p;
        phaseTicks = 0;
        sync();
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(YantraBlock.FACING) ? s.getValue(YantraBlock.FACING) : Direction.NORTH;
    }

    /** World-space centre of the block's base. */
    public Vec3 base() {
        return Vec3.atBottomCenterOf(worldPosition);
    }

    public Vec3 front(double distance) {
        Direction f = facing();
        return base().add(f.getStepX() * distance, 0, f.getStepZ() * distance);
    }

    protected long ownerPoints() {
        return level == null ? 0 : PacingEngine.points(level.getServer(), owner);
    }

    protected boolean targetReached() {
        return owner != null && level != null && PacingEngine.reached(level.getServer(), owner, tier().target);
    }

    protected boolean targetReachedFor(Player p) {
        return level != null && PacingEngine.reached(level.getServer(), p.getUUID(), tier().target);
    }

    /** Pays frozen blocks to the owner; returns true when this Yantra's target has now been reached. */
    protected boolean pay(long amount) {
        if (level == null || owner == null) return false;
        PacingEngine.award(level.getServer(), owner, amount);
        return targetReached();
    }

    protected void say(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    protected void saveSync(CompoundTag tag) {
        tag.putInt("phase", phase);
        tag.putInt("phaseTicks", phaseTicks);
        if (owner != null) tag.putUUID("owner", owner);
    }

    protected void loadSync(CompoundTag tag) {
        phase = tag.getInt("phase");
        phaseTicks = tag.getInt("phaseTicks");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveSync(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadSync(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveSync(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 2.5, 1.5);
    }
}
