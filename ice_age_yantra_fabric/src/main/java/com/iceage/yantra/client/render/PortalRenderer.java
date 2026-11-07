package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.block.PortalBlockEntity;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The crystal arch (baked OBJ frame) with the swirling frost vortex: three counter-rotating emissive layers. */
public class PortalRenderer implements BlockEntityRenderer<PortalBlockEntity> {
    public static final Identifier VORTEX = new Identifier(IceAgeYantra.MODID, "textures/entity/frost_vortex.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public PortalRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(PortalBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        float t = be.age + pt;
        float yaw = 180f - be.getCachedState().get(PortalBlock.FACING).asRotation();
        // frame
        ps.push();
        ps.translate(0.5, 0, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ps.translate(-0.5, 0, -0.5);
        ObjParts.PORTAL_FRAME.render(ps, buf, light, overlay);
        ps.pop();
        renderVortex(ps, buf, t, yaw);
    }

    /** The swirling blue magical centre of the arch (also used by the portal item). */
    public static void renderVortex(MatrixStack ps, VertexConsumerProvider buf, float t, float yaw) {
        ps.push();
        ps.translate(0.5, 1.3, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ps.scale(1.3f, 1.3f, 1.3f);
        disc(buf.getBuffer(RenderLayer.getEntityTranslucent(VORTEX)), ps, 0.56f, 1.02f, t * 1.2f, 0.10f, 0.25f, 0.55f, 0.92f, 0.0f);
        VertexConsumer glow = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(VORTEX));
        disc(glow, ps, 0.55f, 1.0f, -t * 2.6f, 0.75f, 0.92f, 1.0f, 0.95f, 0.004f);
        disc(glow, ps, 0.47f, 0.86f, t * 4.1f, 0.55f, 0.85f, 1.0f, 0.75f, 0.008f);
        float pulse = 0.5f + 0.5f * MathHelper.sin(t * 0.08f);
        disc(glow, ps, 0.30f + 0.04f * pulse, 0.55f + 0.06f * pulse, -t * 7.0f, 1f, 1f, 1f, 0.55f, 0.012f);
        ps.pop();
        YantraRig.glow(ps, buf, 0.5, 1.35, 0.5, 3.3f, 0.35f, 0.75f, 1.0f, 0.35f + 0.15f * pulse);
    }

    /** Elliptical disc made of quads, textured with a rotating polar mapping, drawn on both faces. */
    private static void disc(VertexConsumer vc, MatrixStack ps, float rx, float ry, float rotDeg, float r, float g, float b, float a, float z) {
        Matrix4f m = ps.peek().getPositionMatrix();
        Matrix3f n = ps.peek().getNormalMatrix();
        int seg = 40;
        float rot = rotDeg * MathHelper.RADIANS_PER_DEGREE;
        for (int face = 0; face < 2; face++) {
            float zz = face == 0 ? z : -z;
            float nz = face == 0 ? 1 : -1;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (2 * Math.PI * i / seg), a1 = (float) (2 * Math.PI * (i + 1) / seg);
                float x0 = MathHelper.cos(a0) * rx, y0 = MathHelper.sin(a0) * ry, x1 = MathHelper.cos(a1) * rx, y1 = MathHelper.sin(a1) * ry;
                float u0 = 0.5f + 0.5f * MathHelper.cos(a0 + rot), v0 = 0.5f + 0.5f * MathHelper.sin(a0 + rot);
                float u1 = 0.5f + 0.5f * MathHelper.cos(a1 + rot), v1 = 0.5f + 0.5f * MathHelper.sin(a1 + rot);
                if (face == 0) {
                    put(vc, m, n, 0, 0, zz, 0.5f, 0.5f, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                } else {
                    put(vc, m, n, 0, 0, zz, 0.5f, 0.5f, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                }
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float a, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(n, 0, 0, nz).next();
    }

    @Override
    public boolean rendersOutsideBoundingBox(PortalBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }
}
