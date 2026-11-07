package com.iceage.yantra.client.render;

import com.iceage.yantra.entity.IceCreamTruckEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renders the van rig: body, decals, steering + spinning wheels, and the glowing giant cone. */
public class IceCreamTruckRenderer extends EntityRenderer<IceCreamTruckEntity> {
    public IceCreamTruckRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 1.3f;
        shadowStrength = 0.8f;
    }

    @Override
    public void render(IceCreamTruckEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(-y));
        float lean = Mth.lerp(pt, e.prevLean, e.lean);
        ps.translate(0, 0.0, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(lean));
        float bounce = Math.abs(e.groundSpeed) > 0.05f ? 0.015f * Mth.sin((e.tickCount + pt) * 1.7f) : 0f;
        ps.translate(0, bounce, 0);
        float wheel = Mth.lerp(pt, e.prevWheelAngle, e.wheelAngle);
        float steer = Mth.lerp(pt, e.prevSteer, e.steer);
        YantraRig.van(ps, buf, light, OverlayTexture.NO_OVERLAY, wheel, steer, e.tickCount + pt, true);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(IceCreamTruckEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
