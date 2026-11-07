package com.iceage.yantra.network;

import com.iceage.yantra.progress.Tier;
import com.iceage.yantra.shop.ShopLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: buy a Yantra from the shop GUI. */
public class PurchasePacket {
    public final int tier;

    public PurchasePacket(int tier) {
        this.tier = tier;
    }

    public PurchasePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(tier);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        Tier t = Tier.byNumber(tier);
        if (player != null && t != null) {
            ShopLogic.purchase(player, t);
        }
        ctx.get().setPacketHandled(true);
    }
}
