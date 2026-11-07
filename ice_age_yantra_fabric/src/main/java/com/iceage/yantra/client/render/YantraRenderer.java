package com.iceage.yantra.client.render;

import com.iceage.yantra.Tier;
import com.iceage.yantra.block.YantraBlock;
import com.iceage.yantra.block.YantraBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** One renderer for the four placeable Yantras: draws the baked OBJ rig and animates it while it runs. */
public class YantraRenderer implements BlockEntityRenderer<YantraBlockEntity> {
    public YantraRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(YantraBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        if (!(be.getCachedState().getBlock() instanceof YantraBlock yb)) return;
        Tier tier = yb.tier;
        ps.push();
        YantraRig.faceBlock(ps, be.getCachedState().get(YantraBlock.FACING));
        float angle = MathHelper.lerp(pt, be.prevAngle, be.angle);
        float run = Math.min(1f, be.speed / 40f);
        float time = be.age + pt;
        switch (tier) {
            case GOLA -> YantraRig.gola(ps, buf, light, overlay, angle, 0, 0);
            case FAN -> YantraRig.fan(ps, buf, light, overlay, angle, 22f * run * MathHelper.sin(time * 0.05f), run, time);
            case FRIDGE -> {
                float door = Math.max(0f, MathHelper.lerp(pt, be.prevDoor, be.door));
                YantraRig.fridge(ps, buf, light, overlay, door, door * 0.66f);
            }
            case AC -> YantraRig.ac(ps, buf, light, overlay, angle);
            default -> {
            }
        }
        ps.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(YantraBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
