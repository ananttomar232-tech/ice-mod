package com.iceage.yantra.client.render;

import com.iceage.yantra.block.AcUnitBlockEntity;
import com.iceage.yantra.block.FarrataFanBlockEntity;
import com.iceage.yantra.block.GolaMachineBlockEntity;
import com.iceage.yantra.block.RetroFridgeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** Block entity renderers of the four placeable Yantras. */
public final class YantraRenderers {
    private YantraRenderers() {
    }

    public static class Gola implements BlockEntityRenderer<GolaMachineBlockEntity> {
        public Gola(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(GolaMachineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            YantraRig.faceBlock(ps, be.facing());
            float wheel = Mth.lerp(pt, be.prevWheelAngle, be.wheelAngle);
            int handle = 0;
            float t = 0f;
            if (be.snapped && be.getLevel() != null) {
                t = ((be.getLevel().getGameTime() - be.snapTime) + pt) / 26f;
                handle = t < 1f ? 1 : 2;
            }
            YantraRig.gola(ps, buf, light, overlay, wheel, handle, t);
            ps.popPose();
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    public static class Fan implements BlockEntityRenderer<FarrataFanBlockEntity> {
        public Fan(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(FarrataFanBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            YantraRig.faceBlock(ps, be.facing());
            float blade = Mth.lerp(pt, be.prevBladeAngle, be.bladeAngle);
            float time = be.age + pt;
            YantraRig.fan(ps, buf, light, overlay, blade, 0f, be.shake, time);
            ps.popPose();
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    public static class Fridge implements BlockEntityRenderer<RetroFridgeBlockEntity> {
        public Fridge(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(RetroFridgeBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            YantraRig.faceBlock(ps, be.facing());
            float door = Mth.lerp(pt, be.prevDoorAngle, be.doorAngle);
            float top = Mth.lerp(pt, be.prevTopDoorAngle, be.topDoorAngle);
            if (be.getLevel() != null) {
                float since = be.getLevel().getGameTime() - be.slamTime + pt;
                if (since < 14f && be.phase() == RetroFridgeBlockEntity.LOCKED) {
                    door += 7f * Mth.sin(since * 1.4f) * (1f - since / 14f);
                }
            }
            YantraRig.fridge(ps, buf, light, overlay, Math.max(0f, door), Math.max(0f, top));
            ps.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(RetroFridgeBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    public static class Ac implements BlockEntityRenderer<AcUnitBlockEntity> {
        public Ac(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(AcUnitBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            YantraRig.faceBlock(ps, be.facing());
            YantraRig.ac(ps, buf, light, overlay, Mth.lerp(pt, be.prevFanAngle, be.fanAngle));
            ps.popPose();
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }
}
