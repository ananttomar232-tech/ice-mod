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
            case GOLA -> {
                YantraRig.gola(ps, buf, light, overlay, angle, be.handleGone ? 3 : 0, 0);
                float ft = (be.flightTicks - 1 + pt) / YantraBlockEntity.HANDLE_FLIGHT_TICKS;
                if (be.handleGone && be.flightTicks < 100 && ft >= 0 && ft < 1f) flyingHandle(be, ps, buf, light, overlay, ft, pt);
            }
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

    /** The snapped-off handle flies straight into the face of whoever pressed the button. */
    private static void flyingHandle(YantraBlockEntity be, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float t, float pt) {
        net.minecraft.entity.Entity p = be.getWorld() == null ? null : be.getWorld().getEntityById(be.playerId);
        double hx = com.iceage.yantra.RigData.GOLA_HANDLE_TOP_X, hy = com.iceage.yantra.RigData.GOLA_HANDLE_TOP_Y - 0.26,
                hz = com.iceage.yantra.RigData.GOLA_HANDLE_TOP_Z;
        double tx = hx, ty = hy + 2, tz = hz + 2;
        if (p != null) {
            net.minecraft.util.math.Vec3d eye = p.getCameraPosVec(pt);
            double wx = eye.x - be.getPos().getX(), wy = eye.y - be.getPos().getY() - 0.1, wz = eye.z - be.getPos().getZ();
            // undo the block's facing rotation (the rig is drawn in a rotated frame)
            double th = Math.toRadians(-(180 - be.facing().asRotation()));
            double rx = wx - 0.5, rz = wz - 0.5;
            tx = rx * Math.cos(th) + rz * Math.sin(th) + 0.5;
            tz = -rx * Math.sin(th) + rz * Math.cos(th) + 0.5;
            ty = wy;
        }
        float e = t * t * (3 - 2 * t);
        double px = MathHelper.lerp(e, hx, tx), py = MathHelper.lerp(e, hy, ty) + 0.8 * 4 * t * (1 - t), pz = MathHelper.lerp(e, hz, tz);
        ps.push();
        ps.translate(px, py, pz);
        ps.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(t * 540f));
        ps.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-90f + t * 720f));
        ps.translate(-hx, -hy, -hz);
        com.iceage.yantra.client.obj.ObjParts.GOLA_HANDLE.render(ps, buf, light, overlay);
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
