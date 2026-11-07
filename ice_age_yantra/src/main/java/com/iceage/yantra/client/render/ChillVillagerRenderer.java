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
            } else if (e.state() == ChillVillagerEntity.FLEEING) {
                arms.xRot = -2.6f + Mth.sin(age * 0.9f) * 0.35f;
                arms.zRot = Mth.sin(age * 1.3f) * 0.25f;
                head.zRot = Mth.sin(age * 0.8f) * 0.2f;
            }
        }
    }
}
