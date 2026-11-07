package com.iceage.yantra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: what the Turbo Remote's LCD should show. */
public class RemoteHudPacket {
    public static final int TURBO = 0, STANDBY = 1, PAIRED = 2, NO_SIGNAL = 3;
    public final int mode;

    public RemoteHudPacket(int mode) {
        this.mode = mode;
    }

    public RemoteHudPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.iceage.yantra.client.gui.RemoteHudOverlay.show(mode));
        ctx.get().setPacketHandled(true);
    }
}
