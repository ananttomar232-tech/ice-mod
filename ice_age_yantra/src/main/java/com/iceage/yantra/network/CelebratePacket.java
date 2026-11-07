package com.iceage.yantra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: big on-screen banners (target reached, purchase, 10M climax). */
public class CelebratePacket {
    public static final int TARGET = 0, CLIMAX = 1, PURCHASE = 2, LOCKED = 3, OPEN_SHOP = 9, SEAMLESS = 10;
    public final int kind;
    public final long value;

    public CelebratePacket(int kind, long value) {
        this.kind = kind;
        this.value = value;
    }

    public CelebratePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeVarLong(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.iceage.yantra.client.gui.CelebrationOverlay.show(kind, value)));
        ctx.get().setPacketHandled(true);
    }
}
