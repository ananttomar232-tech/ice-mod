package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.RigData;
import com.iceage.yantra.Tier;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Poses and draws every Yantra rig (ported 1:1 from the Forge version). Block rigs live in 0..1 block space facing
 * north; the van is centred and faces +Z.
 */
public final class YantraRig {
    public static final Identifier HALO = new Identifier(IceAgeYantra.MODID, "textures/entity/glow_halo.png");
    public static final Identifier BEAM = new Identifier(IceAgeYantra.MODID, "textures/entity/light_beam.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private YantraRig() {
    }

    // ------------------------------------------------------------------------------------------------ gola
    /** @param handleState 0 = attached, 1 = flying (t = handleT 0..1), 2 = lying on the ground */
    public static void gola(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float wheelAngle, int handleState, float handleT) {
        ObjParts.GOLA_BODY.render(ps, buf, light, overlay);
        ps.push();
        pivotY(ps, RigData.GOLA_WHEEL_X, RigData.GOLA_WHEEL_Y, RigData.GOLA_WHEEL_Z, wheelAngle);
        ObjParts.GOLA_WHEEL.render(ps, buf, light, overlay);
        if (handleState == 0) ObjParts.GOLA_HANDLE.render(ps, buf, light, overlay);
        ps.pop();
        if (handleState != 0) {
            double hx = RigData.GOLA_HANDLE_TOP_X, hy = RigData.GOLA_HANDLE_TOP_Y - 0.26, hz = RigData.GOLA_HANDLE_TOP_Z;
            double ex = hx + 0.95, ey = 0.035, ez = hz - 0.25;
            float t = handleState == 1 ? MathHelper.clamp(handleT, 0f, 1f) : 1f;
            double px = MathHelper.lerp(t, hx, ex), pz = MathHelper.lerp(t, hz, ez);
            double py = MathHelper.lerp(t, hy, ey) + 1.5 * 4.0 * t * (1.0 - t);
            ps.push();
            ps.translate(px, py, pz);
            ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25f * t));
            ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f - 720f * (1f - t) * (handleState == 1 ? 1f : 0f)));
            ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(handleState == 1 ? 540f * (1f - t) : 0f));
            ps.translate(-hx, -hy, -hz);
            ObjParts.GOLA_HANDLE.render(ps, buf, light, overlay);
            ps.pop();
        }
    }

    // ------------------------------------------------------------------------------------------------- fan
    public static void fan(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float bladeAngle, float swing, float shake, float time) {
        ObjParts.FAN_BODY.render(ps, buf, light, overlay);
        ps.push();
        float jx = shake * 0.012f * MathHelper.sin(time * 2.9f), jy = shake * 0.008f * MathHelper.sin(time * 3.7f + 1f);
        ps.translate(jx, jy, 0);
        pivotY(ps, RigData.FAN_NECK_X, RigData.FAN_NECK_Y, RigData.FAN_NECK_Z, swing);
        ObjParts.FAN_HEAD.render(ps, buf, light, overlay);
        ps.translate(RigData.FAN_ROTOR_X, RigData.FAN_ROTOR_Y, RigData.FAN_ROTOR_Z);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bladeAngle));
        ps.translate(-RigData.FAN_ROTOR_X, -RigData.FAN_ROTOR_Y, -RigData.FAN_ROTOR_Z);
        ObjParts.FAN_ROTOR.render(ps, buf, light, overlay);
        ps.pop();
    }

    // ---------------------------------------------------------------------------------------------- fridge
    public static void fridge(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float door, float topDoor) {
        ObjParts.FRIDGE_BODY.render(ps, buf, light, overlay);
        ps.push();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, door);
        ObjParts.FRIDGE_DOOR_BOTTOM.render(ps, buf, light, overlay);
        ps.pop();
        ps.push();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, topDoor);
        ObjParts.FRIDGE_DOOR_TOP.render(ps, buf, light, overlay);
        ps.pop();
    }

    // -------------------------------------------------------------------------------------------------- ac
    public static void ac(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float fanAngle) {
        ObjParts.AC_BODY.render(ps, buf, light, overlay);
        ps.push();
        ps.translate(RigData.AC_ROTOR_X, RigData.AC_ROTOR_Y, RigData.AC_ROTOR_Z);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fanAngle));
        ps.translate(-RigData.AC_ROTOR_X, -RigData.AC_ROTOR_Y, -RigData.AC_ROTOR_Z);
        ObjParts.AC_ROTOR.render(ps, buf, light, overlay);
        ps.pop();
    }

    // ------------------------------------------------------------------------------------------------- van
    public static void van(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float wheelAngle, float steer, float time, boolean halo) {
        ObjParts.TRUCK_BODY.render(ps, buf, light, overlay);
        ObjParts.TRUCK_DECALS.render(ps, buf, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FL, RigData.TRUCK_WHEEL_FL_X, RigData.TRUCK_WHEEL_FL_Y, RigData.TRUCK_WHEEL_FL_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FR, RigData.TRUCK_WHEEL_FR_X, RigData.TRUCK_WHEEL_FR_Y, RigData.TRUCK_WHEEL_FR_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RL, RigData.TRUCK_WHEEL_RL_X, RigData.TRUCK_WHEEL_RL_Y, RigData.TRUCK_WHEEL_RL_Z, wheelAngle, 0, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RR, RigData.TRUCK_WHEEL_RR_X, RigData.TRUCK_WHEEL_RR_Y, RigData.TRUCK_WHEEL_RR_Z, wheelAngle, 0, light, overlay);
        // tinted see-through windscreen + windows (the driver can see out in first person)
        ObjParts.TRUCK_GLASS.render(ps, buf, light, overlay, 0.85f, 0.95f, 1.0f, 0.42f);
        // the glowing giant ice cream cone (full bright, gentle spin + pulse)
        float pulse = 0.88f + 0.12f * MathHelper.sin(time * 0.15f);
        ps.push();
        ps.translate(RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y, RigData.TRUCK_CONE_Z);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.0f));
        ps.translate(-RigData.TRUCK_CONE_X, -RigData.TRUCK_CONE_Y, -RigData.TRUCK_CONE_Z);
        ObjParts.TRUCK_CONE.render(ps, buf, FULL, overlay, pulse, pulse, pulse, 1f);
        ps.pop();
        if (halo) {
            glow(ps, buf, RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y + 0.9, RigData.TRUCK_CONE_Z, 1.9f * pulse, 1.0f, 0.62f, 0.86f, 0.55f);
        }
    }

    private static void wheel(MatrixStack ps, VertexConsumerProvider buf, ObjParts.Part part, double x, double y, double z, float spin, float steer,
            int light, int overlay) {
        ps.push();
        ps.translate(x, y, z);
        if (steer != 0) ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer));
        ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        ps.translate(-x, -y, -z);
        part.render(ps, buf, light, overlay);
        ps.pop();
    }

    // -------------------------------------------------------------------------------------------- generic
    public static void tier(Tier t, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float time) {
        switch (t) {
            case GOLA -> gola(ps, buf, light, overlay, time * 6f, 0, 0);
            case FAN -> fan(ps, buf, light, overlay, time * 25f, 25f * MathHelper.sin(time * 0.05f), 0, time);
            case FRIDGE -> fridge(ps, buf, light, overlay, 0, 0);
            case AC -> ac(ps, buf, light, overlay, time * 12f);
            case VAN -> van(ps, buf, light, overlay, time * 10f, 0, time, false);
        }
    }

    private static void pivotY(MatrixStack ps, double x, double y, double z, float deg) {
        ps.translate(x, y, z);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
        ps.translate(-x, -y, -z);
    }

    /** Camera-facing additive glow sprite. */
    public static void glow(MatrixStack ps, VertexConsumerProvider buf, double x, double y, double z, float size, float r, float g, float b, float a) {
        ps.push();
        ps.translate(x, y, z);
        Quaternionf cam = MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation();
        ps.multiply(cam);
        float s = size / 2f;
        VertexConsumer vc = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(HALO));
        Matrix4f m = ps.peek().getPositionMatrix();
        Matrix3f n = ps.peek().getNormalMatrix();
        int ov = OverlayTexture.DEFAULT_UV;
        vc.vertex(m, -s, -s, 0).color(r, g, b, a).texture(0, 1).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, s, -s, 0).color(r, g, b, a).texture(1, 1).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, s, s, 0).color(r, g, b, a).texture(1, 0).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, -s, s, 0).color(r, g, b, a).texture(0, 0).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        ps.pop();
    }

    /** Soft vertical light beam (two crossed additive quads). */
    public static void beam(MatrixStack ps, VertexConsumerProvider buf, double x, double y, double z, float w, float h, float r, float g, float b, float a, float rot) {
        VertexConsumer vc = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(BEAM));
        int ov = OverlayTexture.DEFAULT_UV;
        for (int k = 0; k < 2; k++) {
            ps.push();
            ps.translate(x, y, z);
            ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot + k * 90f));
            Matrix4f m = ps.peek().getPositionMatrix();
            Matrix3f n = ps.peek().getNormalMatrix();
            float s = w / 2f;
            for (int side = 0; side < 2; side++) {
                float x0 = side == 0 ? -s : s, x1 = side == 0 ? s : -s;
                vc.vertex(m, x0, 0, 0).color(r, g, b, a).texture(0, 1).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x1, 0, 0).color(r, g, b, a).texture(1, 1).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x1, h, 0).color(r, g, b, 0).texture(1, 0).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x0, h, 0).color(r, g, b, 0).texture(0, 0).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
            }
            ps.pop();
        }
    }

    /** Rotates a block-space (0..1) rig to face the given block facing around the block centre. */
    public static void faceBlock(MatrixStack ps, Direction facing) {
        ps.translate(0.5, 0, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()));
        ps.translate(-0.5, 0, -0.5);
    }
}
