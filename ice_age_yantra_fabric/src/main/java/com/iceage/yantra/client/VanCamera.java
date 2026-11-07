package com.iceage.yantra.client;

import com.iceage.yantra.entity.IceVanEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

import java.lang.reflect.Method;

/**
 * Third-person camera for the (1.4x) van, without any mixin: pushes the camera further back and up so the van and its
 * driver stay in frame instead of the camera sitting right behind the cargo box.
 */
public final class VanCamera {
    private static Method moveBy;
    private static boolean failed;

    private VanCamera() {
    }

    public static void register() {
        WorldRenderEvents.START.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (failed || mc.player == null || mc.options.getPerspective().isFirstPerson()) return;
            if (!(mc.player.getVehicle() instanceof IceVanEntity)) return;
            Camera cam = ctx.camera();
            if (!cam.isThirdPerson()) return;
            try {
                if (moveBy == null) {
                    // Camera.moveBy(double, double, double) = intermediary method_19324 (setPos has the same signature!)
                    String name = net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver()
                            .mapMethodName("intermediary", "net.minecraft.class_4184", "method_19324", "(DDD)V");
                    moveBy = Camera.class.getDeclaredMethod(name, double.class, double.class, double.class);
                    moveBy.setAccessible(true);
                }
                moveBy.invoke(cam, -4.5, 1.6, 0.0);
            } catch (Throwable t) {
                failed = true;
            }
        });
    }
}
