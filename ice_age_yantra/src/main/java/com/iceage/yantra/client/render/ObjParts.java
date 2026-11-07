package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * Access + rendering of the baked OBJ rig parts. Quads are drawn with their baked per-vertex normals,
 * baked ambient-occlusion vertex colours and baked emissive lightmap (Forge putBulkData extensions).
 */
public final class ObjParts {
    public static final List<ResourceLocation> ALL = new ArrayList<>();
    private static final Direction[] DIRS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    private static final float[] SHADE = {1f, 1f, 1f, 1f};
    private static final RandomSource RANDOM = RandomSource.create();

    public static final ResourceLocation GOLA_BODY = part("gola_body"), GOLA_WHEEL = part("gola_wheel"), GOLA_HANDLE = part("gola_handle");
    public static final ResourceLocation FAN_BODY = part("fan_body"), FAN_HEAD = part("fan_head"), FAN_ROTOR = part("fan_rotor");
    public static final ResourceLocation FRIDGE_BODY = part("fridge_body"), FRIDGE_DOOR_TOP = part("fridge_door_top"),
            FRIDGE_DOOR_BOTTOM = part("fridge_door_bottom");
    public static final ResourceLocation AC_BODY = part("ac_body"), AC_ROTOR = part("ac_rotor");
    public static final ResourceLocation TRUCK_BODY = part("truck_body"), TRUCK_CONE = part("truck_cone"), TRUCK_WHEEL_FL = part("truck_wheel_fl"),
            TRUCK_WHEEL_FR = part("truck_wheel_fr"), TRUCK_WHEEL_RL = part("truck_wheel_rl"), TRUCK_WHEEL_RR = part("truck_wheel_rr"),
            TRUCK_DECALS = part("truck_decals"), TRUCK_GLASS = part("truck_glass");
    public static final ResourceLocation SHOP_ORB = part("shop_orb_base");

    private ObjParts() {
    }

    private static ResourceLocation part(String name) {
        ResourceLocation rl = IceAgeYantra.id("block/rig/" + name);
        ALL.add(rl);
        return rl;
    }

    public static BakedModel get(ResourceLocation rl) {
        return Minecraft.getInstance().getModelManager().getModel(rl);
    }

    public static RenderType solid() {
        return RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    }

    public static RenderType translucent() {
        return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
    }

    public static void render(PoseStack ps, MultiBufferSource buf, ResourceLocation part, int light, int overlay) {
        render(ps, buf, part, light, overlay, 1f, 1f, 1f, 1f);
    }

    public static void render(PoseStack ps, MultiBufferSource buf, ResourceLocation part, int light, int overlay, float r, float g, float b, float a) {
        BakedModel model = get(part);
        if (model == null) return;
        VertexConsumer vc = buf.getBuffer(a < 0.999f ? translucent() : solid());
        PoseStack.Pose pose = ps.last();
        int[] lights = {light, light, light, light};
        for (Direction d : DIRS) {
            RANDOM.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, d, RANDOM, ModelData.EMPTY, null)) {
                vc.putBulkData(pose, q, SHADE, r, g, b, a, lights, overlay, true);
            }
        }
    }
}
