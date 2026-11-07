package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.RigData;
import com.iceage.yantra.progress.Tier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Poses and draws every Yantra rig (shared by block entity renderers, item renderer, showroom pedestals and
 * the shop GUI). Block rigs live in 0..1 block space facing north; the van is centred and faces +Z.
 */
public final class YantraRig {
    public static final ResourceLocation HALO = IceAgeYantra.id("textures/entity/glow_halo.png");
    public static final ResourceLocation BEAM = IceAgeYantra.id("textures/entity/light_beam.png");

    private YantraRig() {
    }

    // ------------------------------------------------------------------------------------------------ gola
    /**
     * @param handleState 0 = attached, 1 = flying (t = handleT 0..1), 2 = lying on the ground
     */
    public static void gola(PoseStack ps, MultiBufferSource buf, int light, int overlay, float wheelAngle, int handleState, float handleT) {
        ObjParts.render(ps, buf, ObjParts.GOLA_BODY, light, overlay);
        ps.pushPose();
        pivotY(ps, RigData.GOLA_WHEEL_X, RigData.GOLA_WHEEL_Y, RigData.GOLA_WHEEL_Z, wheelAngle);
        ObjParts.render(ps, buf, ObjParts.GOLA_WHEEL, light, overlay);
        if (handleState == 0) ObjParts.render(ps, buf, ObjParts.GOLA_HANDLE, light, overlay);
        ps.popPose();
        if (handleState != 0) {
            // the handle pivots around its lower end (where it snapped off the wheel)
            double hx = RigData.GOLA_HANDLE_TOP_X, hy = RigData.GOLA_HANDLE_TOP_Y - 0.26, hz = RigData.GOLA_HANDLE_TOP_Z;
            double ex = hx + 0.95, ey = 0.035, ez = hz - 0.25;
            float t = handleState == 1 ? Mth.clamp(handleT, 0f, 1f) : 1f;
            double px = Mth.lerp(t, hx, ex), pz = Mth.lerp(t, hz, ez);
            double py = Mth.lerp(t, hy, ey) + 1.5 * 4.0 * t * (1.0 - t);
            ps.pushPose();
            ps.translate(px, py, pz);
            ps.mulPose(Axis.YP.rotationDegrees(-25f * t));
            ps.mulPose(Axis.ZP.rotationDegrees(-90f - 720f * (1f - t) * (handleState == 1 ? 1f : 0f)));
            ps.mulPose(Axis.XP.rotationDegrees(handleState == 1 ? 540f * (1f - t) : 0f));
            ps.translate(-hx, -hy, -hz);
            ObjParts.render(ps, buf, ObjParts.GOLA_HANDLE, light, overlay);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------------- fan
    public static void fan(PoseStack ps, MultiBufferSource buf, int light, int overlay, float bladeAngle, float swing, float shake, float time) {
        ObjParts.render(ps, buf, ObjParts.FAN_BODY, light, overlay);
        ps.pushPose();
        float jx = shake * 0.012f * Mth.sin(time * 2.9f), jy = shake * 0.008f * Mth.sin(time * 3.7f + 1f);
        ps.translate(jx, jy, 0);
        pivotY(ps, RigData.FAN_NECK_X, RigData.FAN_NECK_Y, RigData.FAN_NECK_Z, swing);
        ObjParts.render(ps, buf, ObjParts.FAN_HEAD, light, overlay);
        ps.translate(RigData.FAN_ROTOR_X, RigData.FAN_ROTOR_Y, RigData.FAN_ROTOR_Z);
        ps.mulPose(Axis.ZP.rotationDegrees(bladeAngle));
        ps.translate(-RigData.FAN_ROTOR_X, -RigData.FAN_ROTOR_Y, -RigData.FAN_ROTOR_Z);
        ObjParts.render(ps, buf, ObjParts.FAN_ROTOR, light, overlay);
        ps.popPose();
    }

    // ---------------------------------------------------------------------------------------------- fridge
    public static void fridge(PoseStack ps, MultiBufferSource buf, int light, int overlay, float door, float topDoor) {
        ObjParts.render(ps, buf, ObjParts.FRIDGE_BODY, light, overlay);
        ps.pushPose();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, door);
        ObjParts.render(ps, buf, ObjParts.FRIDGE_DOOR_BOTTOM, light, overlay);
        ps.popPose();
        ps.pushPose();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, topDoor);
        ObjParts.render(ps, buf, ObjParts.FRIDGE_DOOR_TOP, light, overlay);
        ps.popPose();
    }

    // -------------------------------------------------------------------------------------------------- ac
    public static void ac(PoseStack ps, MultiBufferSource buf, int light, int overlay, float fanAngle) {
        ObjParts.render(ps, buf, ObjParts.AC_BODY, light, overlay);
        ps.pushPose();
        ps.translate(RigData.AC_ROTOR_X, RigData.AC_ROTOR_Y, RigData.AC_ROTOR_Z);
        ps.mulPose(Axis.ZP.rotationDegrees(fanAngle));
        ps.translate(-RigData.AC_ROTOR_X, -RigData.AC_ROTOR_Y, -RigData.AC_ROTOR_Z);
        ObjParts.render(ps, buf, ObjParts.AC_ROTOR, light, overlay);
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------- van
    public static void van(PoseStack ps, MultiBufferSource buf, int light, int overlay, float wheelAngle, float steer, float time, boolean halo) {
        ObjParts.render(ps, buf, ObjParts.TRUCK_BODY, light, overlay);
        ObjParts.render(ps, buf, ObjParts.TRUCK_DECALS, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FL, RigData.TRUCK_WHEEL_FL_X, RigData.TRUCK_WHEEL_FL_Y, RigData.TRUCK_WHEEL_FL_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FR, RigData.TRUCK_WHEEL_FR_X, RigData.TRUCK_WHEEL_FR_Y, RigData.TRUCK_WHEEL_FR_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RL, RigData.TRUCK_WHEEL_RL_X, RigData.TRUCK_WHEEL_RL_Y, RigData.TRUCK_WHEEL_RL_Z, wheelAngle, 0, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RR, RigData.TRUCK_WHEEL_RR_X, RigData.TRUCK_WHEEL_RR_Y, RigData.TRUCK_WHEEL_RR_Z, wheelAngle, 0, light, overlay);
        // tinted see-through windscreen + windows (the driver can see out in first person)
        ObjParts.render(ps, buf, ObjParts.TRUCK_GLASS, light, overlay, 0.85f, 0.95f, 1.0f, 0.42f);
        // the glowing giant ice cream cone (full bright, gentle bob + pulse)
        float pulse = 0.88f + 0.12f * Mth.sin(time * 0.15f);
        ps.pushPose();
        ps.translate(RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y, RigData.TRUCK_CONE_Z);
        ps.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
        ps.translate(-RigData.TRUCK_CONE_X, -RigData.TRUCK_CONE_Y, -RigData.TRUCK_CONE_Z);
        ObjParts.render(ps, buf, ObjParts.TRUCK_CONE, LightTexture.FULL_BRIGHT, overlay, pulse, pulse, pulse, 1f);
        ps.popPose();
        if (halo) {
            glow(ps, buf, RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y + 0.9, RigData.TRUCK_CONE_Z, 1.9f * pulse, 1.0f, 0.62f, 0.86f, 0.55f);
        }
    }

    private static void wheel(PoseStack ps, MultiBufferSource buf, ResourceLocation part, double x, double y, double z, float spin, float steer,
            int light, int overlay) {
        ps.pushPose();
        ps.translate(x, y, z);
        if (steer != 0) ps.mulPose(Axis.YP.rotationDegrees(steer));
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        ps.translate(-x, -y, -z);
        ObjParts.render(ps, buf, part, light, overlay);
        ps.popPose();
    }

    // -------------------------------------------------------------------------------------------- generic
    public static void tier(Tier t, PoseStack ps, MultiBufferSource buf, int light, int overlay, float time) {
        switch (t) {
            case GOLA -> gola(ps, buf, light, overlay, time * 6f, 0, 0);
            case FAN -> fan(ps, buf, light, overlay, time * 25f, 25f * Mth.sin(time * 0.05f), 0, time);
            case FRIDGE -> fridge(ps, buf, light, overlay, 0, 0);
            case AC -> ac(ps, buf, light, overlay, time * 12f);
            case VAN -> van(ps, buf, light, overlay, time * 10f, 0, time, false);
        }
    }

    private static void pivotY(PoseStack ps, double x, double y, double z, float deg) {
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(deg));
        ps.translate(-x, -y, -z);
    }

    /** Camera-facing additive glow sprite. */
    public static void glow(PoseStack ps, MultiBufferSource buf, double x, double y, double z, float size, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(x, y, z);
        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        ps.mulPose(cam);
        float s = size / 2f;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        vc.vertex(m, -s, -s, 0).color(r, g, b, a).uv(0, 1).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, s, -s, 0).color(r, g, b, a).uv(1, 1).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, s, s, 0).color(r, g, b, a).uv(1, 0).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, -s, s, 0).color(r, g, b, a).uv(0, 0).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        ps.popPose();
    }

    /** Soft vertical light beam (two crossed additive quads). */
    public static void beam(PoseStack ps, MultiBufferSource buf, double x, double y, double z, float w, float h, float r, float g, float b, float a, float rot) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(BEAM));
        int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(Axis.YP.rotationDegrees(rot + k * 90f));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float s = w / 2f;
            for (int side = 0; side < 2; side++) {
                float zz = 0;
                float x0 = side == 0 ? -s : s, x1 = side == 0 ? s : -s;
                vc.vertex(m, x0, 0, zz).color(r, g, b, a).uv(0, 1).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
                vc.vertex(m, x1, 0, zz).color(r, g, b, a).uv(1, 1).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
                vc.vertex(m, x1, h, zz).color(r, g, b, 0).uv(1, 0).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
                vc.vertex(m, x0, h, zz).color(r, g, b, 0).uv(0, 0).overlayCoords(ov).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
            }
            ps.popPose();
        }
    }

    /** Rotates a block-space (0..1) rig to face the given block facing around the block centre. */
    public static void faceBlock(PoseStack ps, net.minecraft.core.Direction facing) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        ps.translate(-0.5, 0, -0.5);
    }
}
