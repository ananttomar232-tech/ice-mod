package com.iceage.yantra.client.render;

import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Renders the Musical Ice Cream Van with the same baked OBJ rig as the Forge version, 1.4x bigger. */
public class VanRenderer extends EntityRenderer<IceVanEntity> {
    public VanRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.8f;
    }

    @Override
    public void render(IceVanEntity e, float yaw, float tickDelta, MatrixStack ps, VertexConsumerProvider buf, int light) {
        ps.push();
        float y = MathHelper.lerpAngleDegrees(tickDelta, e.prevYaw, e.getYaw());
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-y));
        float lean = MathHelper.lerp(tickDelta, e.prevLean, e.lean);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lean));
        float bounce = Math.abs(e.groundSpeed) > 0.05f ? 0.015f * MathHelper.sin((e.age + tickDelta) * 1.7f) : 0f;
        ps.translate(0, bounce, 0);
        ps.scale(IceVanEntity.SCALE, IceVanEntity.SCALE, IceVanEntity.SCALE);
        float wheel = MathHelper.lerp(tickDelta, e.prevWheelAngle, e.wheelAngle);
        float steer = MathHelper.lerp(tickDelta, e.prevSteer, e.steer);
        YantraRig.van(ps, buf, light, OverlayTexture.DEFAULT_UV, wheel, steer, e.age + tickDelta, true);
        ps.pop();
        super.render(e, yaw, tickDelta, ps, buf, light);
    }

    @Override
    public Identifier getTexture(IceVanEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
