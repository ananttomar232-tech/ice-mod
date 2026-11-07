package com.iceage.yantra.client.render;

import com.iceage.yantra.client.obj.ObjParts;
import com.iceage.yantra.entity.GotiyaEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Gotiya: sweaty desert villager (vanilla texture, like the Forge version) with the giant baked-OBJ hairdryer. */
public class GotiyaRenderer extends MobEntityRenderer<GotiyaEntity, GotiyaRenderer.Model> {
    private static final Identifier TEXTURE = new Identifier("textures/entity/villager/villager.png");
    /** The hairdryer is drawn this many times bigger than its model (taller than Gotiya himself). */
    public static final float DRYER_SCALE = 4.0f;

    public GotiyaRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(EntityModelLayers.VILLAGER)), 0.5f);
        addFeature(new HeadFeatureRenderer<>(this, ctx.getModelLoader(), ctx.getHeldItemRenderer()));
        addFeature(new VillagerClothingFeatureRenderer<>(this, ctx.getResourceManager(), "villager"));
    }

    @Override
    public Identifier getTexture(GotiyaEntity e) {
        return TEXTURE;
    }

    @Override
    protected void scale(GotiyaEntity e, MatrixStack ps, float amount) {
        ps.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public void render(GotiyaEntity e, float yaw, float pt, MatrixStack ps, VertexConsumerProvider buf, int light) {
        super.render(e, yaw, pt, ps, buf, light);
        if (!e.hasHairdryer()) return;
        float t = e.age + pt;
        float body = MathHelper.lerpAngleDegrees(pt, e.prevBodyYaw, e.bodyYaw);
        ps.push();
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-body));
        // hands up at chest height, the whole thing sways and dips as he struggles with the weight
        ps.translate(MathHelper.sin(t * 0.21f) * 0.08f, 1.35 + MathHelper.sin(t * 0.33f) * 0.06f, 0.45);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 0.17f) * 9f + MathHelper.sin(t * 0.61f) * 2.5f));
        ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(14f + MathHelper.sin(t * 0.25f) * 6f));
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.09f) * 10f));
        ps.scale(DRYER_SCALE, DRYER_SCALE, DRYER_SCALE);
        ps.translate(0, 0.84, 0.19);   // grab it by the bottom of the handle
        ObjParts.HAIRDRYER.render(ps, buf, light, OverlayTexture.DEFAULT_UV);
        // glowing heat at the mouth of the nozzle
        YantraRig.glow(ps, buf, 0, 0, 0.74, 0.35f, 1.0f, 0.42f, 0.12f, 0.55f + 0.25f * MathHelper.sin(t * 0.9f));
        ps.pop();
    }

    @Override
    public boolean shouldRender(GotiyaEntity e, Frustum frustum, double x, double y, double z) {
        return e.hasHairdryer() || super.shouldRender(e, frustum, x, y, z);
    }

    /** Villager model with the arms posed for the struggle with the hairdryer / fleeing. */
    public static class Model extends VillagerResemblingModel<GotiyaEntity> {
        private final ModelPart arms, head;

        public Model(ModelPart root) {
            super(root);
            arms = root.getChild("arms");
            head = root.getChild("head");
        }

        @Override
        public void setAngles(GotiyaEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            arms.pitch = -0.75f;
            arms.roll = 0f;
            if (e.hasHairdryer()) {
                arms.pitch = -1.9f + MathHelper.sin(age * 0.4f) * 0.12f;
                head.pitch = -0.35f;
                head.roll = MathHelper.sin(age * 0.3f) * 0.12f;
            } else if (e.state() == GotiyaEntity.FLEEING) {
                arms.pitch = -2.6f + MathHelper.sin(age * 0.9f) * 0.35f;
                arms.roll = MathHelper.sin(age * 1.3f) * 0.25f;
                head.roll = MathHelper.sin(age * 0.8f) * 0.2f;
            }
        }
    }
}
