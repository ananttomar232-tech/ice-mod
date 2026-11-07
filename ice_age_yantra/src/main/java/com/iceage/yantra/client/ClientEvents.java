package com.iceage.yantra.client;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.client.gui.CelebrationOverlay;
import com.iceage.yantra.client.gui.RemoteHudOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IceAgeYantra.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ClientProgress.tick();
        RemoteHudOverlay.tick();
        CelebrationOverlay.tick();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientProgress.reset();
    }
}
