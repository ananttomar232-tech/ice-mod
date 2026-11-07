package com.iceage.yantra.network;

import com.iceage.yantra.shop.ShowroomDimension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "take me to the frozen showroom" button in the shop GUI. */
public class ShowroomPacket {
    public ShowroomPacket() {
    }

    public ShowroomPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ShowroomDimension.toggle(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
