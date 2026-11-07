package com.iceage.yantra.client;

import com.iceage.yantra.ShopBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated cinematic camera: type {@code #cinematic} in chat inside the Ice Shop.
 * <ol>
 * <li>360-degree orbit around each of the five pedestals (Gola, Fan, Fridge, AC, Van), eased.</li>
 * <li>A glide up to a high overhead establishing shot of the whole shop, held for 4 seconds.</li>
 * <li>A smooth return to the player's own view; HUD and controls are restored.</li>
 * </ol>
 * ESC (or any screen opening, or leaving the world) cancels it immediately. Client only, no mixins: the camera is
 * a client-side marker entity that is set as the camera entity and moved every frame.
 */
public final class CinematicCamera {
    private static final String TRIGGER = "#cinematic";

    private interface PoseFn {
        /** @param start pose when the segment began, u in 0..1; returns {x, y, z, yaw, pitch} */
        double[] at(double[] start, double u);
    }

    private record Seg(double dur, PoseFn fn) {
    }

    private static boolean active;
    private static List<Seg> segs;
    private static int segIndex;
    private static long segStartNs;
    private static double[] segStartPose, lastPose;
    private static Entity camEntity;
    /** Vanilla never draws the local player when the camera is another entity, so a client-only double stands in. */
    private static OtherClientPlayerEntity double_;
    private static final int DOUBLE_ID = -8675309;
    private static boolean oldHudHidden;
    private static Perspective oldPerspective;
    private static float savedYaw, savedPitch;

    private CinematicCamera() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
            if (!msg.trim().equalsIgnoreCase(TRIGGER)) return true;
            start();
            return false;   // the command is consumed, nothing is sent to the chat
        });
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (!active) return;
            guard(mc);
            if (active) KeyBinding.unpressAll();   // movement / actions locked
        });
        WorldRenderEvents.START.register(ctx -> frame());
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            if (active) stop();
        });
    }

    // ------------------------------------------------------------------------------------------------ lifecycle
    private static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (active || mc.player == null || mc.world == null) return;
        BlockPos pp = mc.player.getBlockPos();
        if (!ShopBuilder.isInShopZone(pp)) {
            mc.player.sendMessage(Text.literal("Go to the Ice Shop first, then type #cinematic"), true);
            return;
        }
        double[] eye = playerPose(mc);
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        oldHudHidden = mc.options.hudHidden;
        oldPerspective = mc.options.getPerspective();
        mc.options.hudHidden = true;
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        camEntity = new ArmorStandEntity(mc.world, eye[0], eye[1], eye[2]);
        final net.minecraft.client.network.ClientPlayerEntity real = mc.player;
        // own UUID (the world refuses a second entity with the player's UUID), but the real player's skin and model
        double_ = new OtherClientPlayerEntity(mc.world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), real.getGameProfile().getName())) {
            @Override
            public net.minecraft.util.Identifier getSkinTexture() {
                return real.getSkinTexture();
            }

            @Override
            public String getModel() {
                return real.getModel();
            }
        };
        double_.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), savedYaw, 0f);
        double_.setYaw(savedYaw);
        double_.setPitch(savedPitch);
        double_.bodyYaw = double_.prevBodyYaw = mc.player.bodyYaw;
        double_.headYaw = double_.prevHeadYaw = mc.player.headYaw;
        for (EquipmentSlot slot : EquipmentSlot.values()) double_.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        mc.world.addEntity(DOUBLE_ID, double_);
        segs = buildSequence(mc);
        segIndex = 0;
        lastPose = eye;
        segStartPose = eye;
        segStartNs = System.nanoTime();
        active = true;
        place(eye);
        mc.setCameraEntity(camEntity);
        KeyBinding.unpressAll();
    }

    private static void stop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!active) return;
        active = false;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            mc.player.prevYaw = savedYaw;
            mc.player.prevPitch = savedPitch;
        }
        mc.options.hudHidden = oldHudHidden;
        if (oldPerspective != null) mc.options.setPerspective(oldPerspective);
        if (double_ != null && mc.world != null) mc.world.removeEntity(DOUBLE_ID, Entity.RemovalReason.DISCARDED);
        double_ = null;
        camEntity = null;
        segs = null;
        KeyBinding.unpressAll();
    }

    /** ESC (the pause menu), any other screen, or a world change cancels the cutscene at once. */
    private static void guard(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || camEntity == null || camEntity.getWorld() != mc.world) {
            stop();
            return;
        }
        if (mc.currentScreen != null) {
            boolean chat = mc.currentScreen instanceof ChatScreen;
            if (!chat) {
                mc.setScreen(null);   // close the pause menu that ESC opened
                stop();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ per frame
    private static void frame() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        guard(mc);
        if (!active) return;
        // mouse look must not turn the player during the cutscene
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
        mc.player.prevYaw = savedYaw;
        mc.player.prevPitch = savedPitch;
        mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        double t = (System.nanoTime() - segStartNs) / 1.0e9;
        Seg s = segs.get(segIndex);
        while (t >= s.dur()) {
            // finish this segment exactly, then roll into the next one without losing time
            lastPose = s.fn().at(segStartPose, 1.0);
            segIndex++;
            if (segIndex >= segs.size()) {
                stop();
                return;
            }
            segStartNs += (long) (s.dur() * 1.0e9);
            segStartPose = lastPose;
            t -= s.dur();
            s = segs.get(segIndex);
        }
        double[] pose = s.fn().at(segStartPose, t / s.dur());
        lastPose = pose;
        place(pose);
        if (double_ != null) {
            // the double must not be seen from inside: hide it when the camera is close (start and end of the shot)
            double dx = pose[0] - double_.getX(), dy = pose[1] - double_.getEyeY(), dz = pose[2] - double_.getZ();
            double_.setInvisible(dx * dx + dy * dy + dz * dz < 2.6 * 2.6);
        }
    }

    private static void place(double[] p) {
        float eye = camEntity.getEyeHeight(EntityPose.STANDING);
        double y = p[1] - eye;
        camEntity.setPosition(p[0], y, p[2]);
        camEntity.prevX = camEntity.lastRenderX = p[0];
        camEntity.prevY = camEntity.lastRenderY = y;
        camEntity.prevZ = camEntity.lastRenderZ = p[2];
        camEntity.setYaw((float) p[3]);
        camEntity.setPitch((float) p[4]);
        camEntity.prevYaw = (float) p[3];
        camEntity.prevPitch = (float) p[4];
    }

    // ------------------------------------------------------------------------------------------------ sequence
    private static double[] playerPose(MinecraftClient mc) {
        return new double[]{mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch()};
    }

    private static List<Seg> buildSequence(MinecraftClient mc) {
        List<Seg> list = new ArrayList<>();
        BlockPos[] show = ShopBuilder.SHOWCASES;
        for (int i = 0; i < show.length; i++) {
            final int idx = i;
            // glide to the start of the orbit
            list.add(new Seg(i == 0 ? 2.0 : 1.6, (s, u) -> blend(s, orbitPose(idx, 0.0), cubic(u))));
            // the 360-degree orbit: 3.5 s per Yantra, 4 s for the van
            list.add(new Seg(i == 4 ? 4.0 : 3.5, (s, u) -> orbitPose(idx, u)));
        }
        // glide up to the overhead establishing shot
        list.add(new Seg(3.2, (s, u) -> blend(s, overheadPose(mc, 0.0), cubic(u))));
        // hold for 4 seconds with a slow pan
        list.add(new Seg(4.0, (s, u) -> overheadPose(mc, u)));
        // return to the player's own view
        list.add(new Seg(2.2, (s, u) -> blend(s, new double[]{mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), savedYaw, savedPitch}, cubic(u))));
        return list;
    }

    /** Orbit centre = the floating Yantra above its pedestal. */
    private static double[] orbitCenter(int i) {
        BlockPos p = ShopBuilder.SHOWCASES[i];
        return new double[]{p.getX() + 0.5, p.getY() + 1.55, p.getZ() + 0.5};
    }

    private static double[] orbitPose(int i, double u) {
        double[] c = orbitCenter(i);
        double radius = i == 4 ? 4.4 : 3.1;
        // start on the side facing the middle of the shop so the first shot is readable
        double a0 = Math.atan2(ShopBuilder.CZ + 0.5 - c[2], ShopBuilder.CX + 0.5 - c[0]);
        double a = a0 + Math.PI * 2 * orbitEase(u);
        double x = c[0] + Math.cos(a) * radius, z = c[2] + Math.sin(a) * radius;
        double y = c[1] + 0.15;   // item level
        return lookAt(x, y, z, c[0], c[1], c[2]);
    }

    private static double[] overheadPose(MinecraftClient mc, double u) {
        double px = mc.player.getX(), pz = mc.player.getZ();
        // frame the whole room: the middle of the player and of the five pedestals
        double tx = 0.5 * (px + ShopBuilder.CX + 0.5), tz = 0.5 * (pz + ShopBuilder.CZ + 3.0);
        double ty = ShopBuilder.FLOOR + 1.3;
        double pan = MathHelper.lerp(smooth(u), -2.0, 2.0);
        double cx = MathHelper.clamp(tx + pan, ShopBuilder.X0 + 1.5, ShopBuilder.X1 - 1.5);
        double cz = Math.max(ShopBuilder.Z0 + 1.5, tz - 8.5);
        double cy = Math.min(ShopBuilder.TOP - 1.6, ty + 9.2);
        return lookAt(cx, cy, cz, tx, ty, tz);
    }

    // ------------------------------------------------------------------------------------------------ math
    private static double[] lookAt(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{x, y, z, yaw, pitch};
    }

    private static double[] blend(double[] a, double[] b, double e) {
        double dy = MathHelper.wrapDegrees(b[3] - a[3]);
        return new double[]{a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e, a[2] + (b[2] - a[2]) * e, a[3] + dy * e, a[4] + (b[4] - a[4]) * e};
    }

    /** Cubic ease-in-out (cubic bezier 0.65, 0, 0.35, 1 shape). */
    private static double cubic(double u) {
        return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }

    private static double smooth(double u) {
        return u * u * (3 - 2 * u);
    }

    /** Orbit easing: soft start and end but fairly even speed in between. */
    private static double orbitEase(double u) {
        return 0.4 * u + 0.6 * smooth(u);
    }
}
