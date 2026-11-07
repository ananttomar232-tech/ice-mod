package com.iceage.yantra.client.render;

import com.iceage.yantra.entity.ChillVillagerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Sweaty desert villager: sits (legs out, shivering) inside the fridge, flails arms while fleeing. */
public class ChillVillagerRenderer extends MobRenderer<ChillVillagerEntity, ChillVillagerRenderer.Model> {
    @SuppressWarnings("removal")
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/villager/villager.png");

    public ChillVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        addLayer(new VillagerProfessionLayer<>(this, ctx.getResourceManager(), "villager"));
        addLayer(new CrossedArmsItemLayer<>(this, ctx.getItemInHandRenderer()));
    }

    /** Size of the giant hairdryer relative to its model (about 5 blocks long, towering over Gotiya). */
    public static final float DRYER_SCALE = 4.0f;

    @Override
    public void render(ChillVillagerEntity e, float yaw, float pt, PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, int light) {
        super.render(e, yaw, pt, ps, buf, light);
        if (!e.hasHairdryer()) return;
        float t = e.tickCount + pt;
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-body));
        // hands up at chest height, the whole thing sways and dips as he struggles with the weight
        ps.translate(Mth.sin(t * 0.21f) * 0.08f, 1.35 + Mth.sin(t * 0.33f) * 0.06f, 0.45);
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(t * 0.17f) * 9f + Mth.sin(t * 0.61f) * 2.5f));
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(14f + Mth.sin(t * 0.25f) * 6f));
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Mth.sin(t * 0.09f) * 10f));
        ps.scale(DRYER_SCALE, DRYER_SCALE, DRYER_SCALE);
        ps.translate(0, 0.84, 0.19);   // grab it by the bottom of the handle
        ObjParts.render(ps, buf, ObjParts.HAIRDRYER, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        // glowing heat at the mouth of the nozzle
        YantraRig.glow(ps, buf, 0, 0, 0.74, 0.35f, 1.0f, 0.42f, 0.12f, 0.55f + 0.25f * Mth.sin(t * 0.9f));
        ps.popPose();
    }

    @Override
    public boolean shouldRender(ChillVillagerEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.hasHairdryer() || super.shouldRender(e, f, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(ChillVillagerEntity e) {
        return TEXTURE;
    }

    @Override
    protected void scale(ChillVillagerEntity e, PoseStack ps, float pt) {
        float s = 0.9375f;
        if (e.isSitting()) {
            float t = e.tickCount + pt;
            ps.translate(Mth.sin(t * 2.3f) * 0.012f, -0.4f, Mth.cos(t * 2.9f) * 0.012f);
            s *= 0.82f;
        }
        ps.scale(s, s, s);
    }

    public static class Model extends VillagerModel<ChillVillagerEntity> {
        private final ModelPart rightLeg, leftLeg, head, arms;

        public Model(ModelPart root) {
            super(root);
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
            head = root.getChild("head");
            arms = root.getChild("arms");
        }

        @Override
        public void setupAnim(ChillVillagerEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            arms.xRot = -0.75f;
            arms.zRot = 0f;
            rightLeg.zRot = 0f;
            leftLeg.zRot = 0f;
            if (e.isSitting()) {
                rightLeg.xRot = -1.4137167f;
                rightLeg.yRot = 0.31415927f;
                rightLeg.zRot = 0.07853982f;
                leftLeg.xRot = -1.4137167f;
                leftLeg.yRot = -0.31415927f;
                leftLeg.zRot = -0.07853982f;
                head.zRot = Mth.sin(age * 1.9f) * 0.045f;
                arms.xRot = -0.95f + Mth.sin(age * 2.2f) * 0.03f;
            } else if (e.hasHairdryer()) {
                // arms straining up under the giant hairdryer
                arms.xRot = -1.9f + Mth.sin(age * 0.4f) * 0.12f;
                head.xRot = -0.35f;
                head.zRot = Mth.sin(age * 0.3f) * 0.12f;
            } else if (e.state() == ChillVillagerEntity.BLASTED) {
                head.xRot = -0.6f;
                head.zRot = Mth.sin(age * 2.5f) * 0.25f;
                arms.xRot = -2.2f;
            } else if (e.state() == ChillVillagerEntity.FLEEING) {
                arms.xRot = -2.6f + Mth.sin(age * 0.9f) * 0.35f;
                arms.zRot = Mth.sin(age * 1.3f) * 0.25f;
                head.zRot = Mth.sin(age * 0.8f) * 0.2f;
            }
        }
    }
}
