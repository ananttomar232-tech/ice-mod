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
    public final float speed;

    public SyncProgressPacket(long points, int purchasedMask, long gained, float speed) {
        this.points = points;
        this.purchasedMask = purchasedMask;
        this.gained = gained;
        this.speed = speed;
    }

    public SyncProgressPacket(FriendlyByteBuf buf) {
        this(buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(points);
        buf.writeVarInt(purchasedMask);
        buf.writeVarLong(gained);
        buf.writeFloat(speed);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.iceage.yantra.client.ClientProgress.update(points, purchasedMask, gained, speed));
        ctx.get().setPacketHandled(true);
    }
}
