package com.iceage.yantra.shop;

import com.iceage.yantra.network.CelebratePacket;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.progress.IceProgress;
import com.iceage.yantra.progress.PacingEngine;
import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.registry.ModItems;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Unlock + purchase rules shared by the shop GUI and the physical showroom pedestals. */
public final class ShopLogic {
    private static final Map<UUID, Long> LAST_BUY = new HashMap<>();

    private ShopLogic() {
    }

    public static void purchase(ServerPlayer player, Tier tier) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_BUY.get(player.getUUID());
        if (last != null && now - last < 10) return;
        LAST_BUY.put(player.getUUID(), now);

        IceProgress data = IceProgress.get(player.server);
        IceProgress.Entry e = data.entry(player.getUUID());
        if (e.points < tier.price) {
            long missing = tier.price - e.points;
            player.displayClientMessage(Component.translatable("msg.ice_age_yantra.shop.locked", tier.displayName(),
                    String.format("%,d", missing)).withStyle(ChatFormatting.RED), true);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1.2f);
            return;
        }
        ItemStack stack = new ItemStack(tier.item());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        if (tier == Tier.AC && !player.getInventory().contains(new ItemStack(ModItems.AC_REMOTE.get()))) {
            ItemStack remote = new ItemStack(ModItems.AC_REMOTE.get());
            if (!player.getInventory().add(remote)) player.drop(remote, false);
        }
        boolean first = (e.purchasedMask & tier.bit()) == 0;
        e.purchasedMask |= tier.bit();
        data.setDirty();
        PacingEngine.sync(player);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.PURCHASE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CelebratePacket(CelebratePacket.PURCHASE, tier.number));
        player.sendSystemMessage(Component.translatable(first ? "msg.ice_age_yantra.shop.bought" : "msg.ice_age_yantra.shop.bought_again",
                tier.displayName()).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable("msg.ice_age_yantra.shop.hint." + tier.id).withStyle(ChatFormatting.GRAY));
    }
}
