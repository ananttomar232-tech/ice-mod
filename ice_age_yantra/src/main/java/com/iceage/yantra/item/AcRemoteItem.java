package com.iceage.yantra.item;

import com.iceage.yantra.block.AcUnitBlockEntity;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.network.RemoteHudPacket;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Turbo Remote: right-click an AC unit to pair, right-click anywhere for "16 C TURBO". */
public class AcRemoteItem extends Item {
    public AcRemoteItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof AcUnitBlockEntity)) return InteractionResult.PASS;
        if (!level.isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            CompoundTag tag = ctx.getItemInHand().getOrCreateTag();
            tag.put("pairedPos", NbtUtils.writeBlockPos(ctx.getClickedPos()));
            tag.putString("pairedDim", level.dimension().location().toString());
            level.playSound(null, ctx.getClickedPos(), ModSounds.REMOTE_BEEP.get(), SoundSource.PLAYERS, 1.0f, 1.5f);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RemoteHudPacket(RemoteHudPacket.PAIRED));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ServerPlayer sp = (ServerPlayer) player;
        AcUnitBlockEntity ac = findAc((ServerLevel) level, stack, player);
        int mode;
        if (ac == null) {
            mode = RemoteHudPacket.NO_SIGNAL;
            level.playSound(null, player.blockPosition(), ModSounds.REMOTE_BEEP.get(), SoundSource.PLAYERS, 0.6f, 0.6f);
        } else {
            mode = ac.pressTurbo(sp);
        }
        level.playSound(null, player.blockPosition(), ModSounds.REMOTE_BEEP.get(), SoundSource.PLAYERS, 0.7f, mode == RemoteHudPacket.TURBO ? 1.8f : 1.0f);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new RemoteHudPacket(mode));
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }

    @Nullable
    private static AcUnitBlockEntity findAc(ServerLevel level, ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("pairedPos") && level.dimension().location().toString().equals(tag.getString("pairedDim"))) {
            BlockPos p = NbtUtils.readBlockPos(tag.getCompound("pairedPos"));
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof AcUnitBlockEntity ac) return ac;
        }
        AcUnitBlockEntity best = null;
        double bestD = 48 * 48;
        BlockPos c = player.blockPosition();
        int r = 3;
        for (int cx = (c.getX() >> 4) - r; cx <= (c.getX() >> 4) + r; cx++) {
            for (int cz = (c.getZ() >> 4) - r; cz <= (c.getZ() >> 4) + r; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof AcUnitBlockEntity ac) {
                        double d = be.getBlockPos().distSqr(c);
                        if (d < bestD) {
                            bestD = d;
                            best = ac;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.ice_age_yantra.remote.use").withStyle(ChatFormatting.AQUA));
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("pairedPos")) {
            BlockPos p = NbtUtils.readBlockPos(tag.getCompound("pairedPos"));
            tip.add(Component.translatable("tooltip.ice_age_yantra.remote.paired", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.translatable("tooltip.ice_age_yantra.remote.unpaired").withStyle(ChatFormatting.GRAY));
        }
    }
}
