package com.iceage.yantra.network;

import com.iceage.yantra.IceAgeYantra;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(IceAgeYantra.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncProgressPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncProgressPacket::encode).decoder(SyncProgressPacket::new)
                .consumerMainThread(SyncProgressPacket::handle).add();
        CHANNEL.messageBuilder(CelebratePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CelebratePacket::encode).decoder(CelebratePacket::new)
                .consumerMainThread(CelebratePacket::handle).add();
        CHANNEL.messageBuilder(RemoteHudPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteHudPacket::encode).decoder(RemoteHudPacket::new)
                .consumerMainThread(RemoteHudPacket::handle).add();
        CHANNEL.messageBuilder(PurchasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PurchasePacket::encode).decoder(PurchasePacket::new)
                .consumerMainThread(PurchasePacket::handle).add();
        CHANNEL.messageBuilder(ShowroomPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShowroomPacket::encode).decoder(ShowroomPacket::new)
                .consumerMainThread(ShowroomPacket::handle).add();
    }
}
