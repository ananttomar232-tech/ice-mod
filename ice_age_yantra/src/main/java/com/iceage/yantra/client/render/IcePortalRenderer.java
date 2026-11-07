package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.IcePortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The swirling frost-vortex gateway inside the crystal frame: three counter-rotating emissive layers. */
public class IcePortalRenderer implements BlockEntityRenderer<IcePortalBlockEntity> {
    public static final ResourceLocation VORTEX = IceAgeYantra.id("textures/entity/frost_vortex.png");

    public IcePortalRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(IcePortalBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        float t = be.age + pt;
        ps.pushPose();
        ps.translate(0.5, 1.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - be.facing().toYRot()));
        // dark core so the swirl reads as a hole in space
        disc(ps, buf.getBuffer(RenderType.entityTranslucent(VORTEX)), 0.56f, 1.02f, t * 1.2f, 0.10f, 0.25f, 0.55f, 0.92f, 0.0f);
        VertexConsumer glow = buf.getBuffer(RenderType.entityTranslucentEmissive(VORTEX));
        disc(glow, ps, 0.55f, 1.0f, -t * 2.6f, 0.75f, 0.92f, 1.0f, 0.95f, 0.004f);
        disc(glow, ps, 0.47f, 0.86f, t * 4.1f, 0.55f, 0.85f, 1.0f, 0.75f, 0.008f);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.08f);
        disc(glow, ps, 0.30f + 0.04f * pulse, 0.55f + 0.06f * pulse, -t * 7.0f, 1f, 1f, 1f, 0.55f, 0.012f);
        ps.popPose();
        YantraRig.glow(ps, buf, 0.5, 1.05, 0.5, 2.6f, 0.35f, 0.75f, 1.0f, 0.35f + 0.15f * pulse);
    }

    private static void disc(PoseStack ps, VertexConsumer vc, float rx, float ry, float rotDeg, float r, float g, float b, float a, float z) {
        disc(vc, ps, rx, ry, rotDeg, r, g, b, a, z);
    }

    /** Elliptical disc made of quads, textured with a rotating polar mapping, drawn on both faces. */
    private static void disc(VertexConsumer vc, PoseStack ps, float rx, float ry, float rotDeg, float r, float g, float b, float a, float z) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int seg = 40;
        float rot = rotDeg * Mth.DEG_TO_RAD;
        for (int face = 0; face < 2; face++) {
            float zz = face == 0 ? z : -z;
            float nz = face == 0 ? 1 : -1;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (2 * Math.PI * i / seg), a1 = (float) (2 * Math.PI * (i + 1) / seg);
                float x0 = Mth.cos(a0) * rx, y0 = Mth.sin(a0) * ry, x1 = Mth.cos(a1) * rx, y1 = Mth.sin(a1) * ry;
                float u0 = 0.5f + 0.5f * Mth.cos(a0 + rot), v0 = 0.5f + 0.5f * Mth.sin(a0 + rot);
                float u1 = 0.5f + 0.5f * Mth.cos(a1 + rot), v1 = 0.5f + 0.5f * Mth.sin(a1 + rot);
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
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, 0, nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(IcePortalBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
