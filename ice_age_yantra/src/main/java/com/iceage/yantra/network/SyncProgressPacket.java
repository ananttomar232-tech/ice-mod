package com.iceage.yantra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the player's frozen-block counter (and how much was just gained, for the ding). */
public class SyncProgressPacket {
    public final long points;
    public final int purchasedMask;
    public final long gained;

    public SyncProgressPacket(long points, int purchasedMask, long gained) {
        this.points = points;
        this.purchasedMask = purchasedMask;
        this.gained = gained;
    }

    public SyncProgressPacket(FriendlyByteBuf buf) {
        this(buf.readVarLong(), buf.readVarInt(), buf.readVarLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(points);
        buf.writeVarInt(purchasedMask);
        buf.writeVarLong(gained);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.iceage.yantra.client.ClientProgress.update(points, purchasedMask, gained));
        ctx.get().setPacketHandled(true);
    }
}
