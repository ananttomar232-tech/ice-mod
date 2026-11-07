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
    /** While set, the vanilla "Loading terrain" / dimension-change screens are skipped for portal trips. */
    public static volatile long seamlessUntil;

    private ClientEvents() {
    }

    private static java.lang.reflect.Method camMove, camMaxZoom;
    private static boolean camReflectFailed;

    /** Third person in the (1.4x bigger) van: pull the camera further back and up so it never ends up inside the van. */
    @SubscribeEvent
    public static void vanCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson() || camReflectFailed) return;
        if (!(mc.player.getVehicle() instanceof com.iceage.yantra.entity.IceCreamTruckEntity)) return;
        net.minecraft.client.Camera cam = e.getCamera();
        if (!cam.isDetached()) return;
        try {
            if (camMove == null) {
                camMove = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.Camera.class, "m_90568_",
                        double.class, double.class, double.class);
                camMaxZoom = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.Camera.class, "m_90566_",
                        double.class);
            }
            camMove.invoke(cam, 0.0, 1.0, 0.0);
            double extra = (double) camMaxZoom.invoke(cam, 4.0);
            camMove.invoke(cam, -extra, 0.0, 0.0);
        } catch (Throwable t) {
            camReflectFailed = true;
        }
    }

    @SubscribeEvent
    public static void noLoadingScreen(net.minecraftforge.client.event.ScreenEvent.Opening e) {
        if (System.currentTimeMillis() > seamlessUntil) return;
        net.minecraft.client.gui.screens.Screen s = e.getNewScreen();
        if (s instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
            e.setCanceled(true);
            seamlessUntil = 0;
        } else if (s instanceof net.minecraft.client.gui.screens.ProgressScreen
                || s instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen) {
            e.setCanceled(true);
        }
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
