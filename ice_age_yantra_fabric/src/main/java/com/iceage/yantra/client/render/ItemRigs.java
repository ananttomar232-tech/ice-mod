package com.iceage.yantra.client.render;

import com.iceage.yantra.ModItems;
import com.iceage.yantra.client.obj.ObjParts;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Item rendering: the full animated 3D rigs in the inventory, in hand and in item frames (ported from the Forge BEWLR). */
public final class ItemRigs {
    private ItemRigs() {
    }

    public static void register() {
        for (Item item : new Item[]{ModItems.GOLA_MACHINE, ModItems.FARRATA_FAN, ModItems.RETRO_FRIDGE, ModItems.AC_UNIT,
                ModItems.ICE_CREAM_VAN, ModItems.VILLAGE_PORTAL}) {
            BuiltinItemRendererRegistry.INSTANCE.register(item, ItemRigs::render);
        }
    }

    private static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        Item item = stack.getItem();
        MinecraftClient mc = MinecraftClient.getInstance();
        float time = (mc.world == null ? 0 : mc.world.getTime()) + mc.getTickDelta();
        ps.push();
        if (item == ModItems.GOLA_MACHINE) {
            YantraRig.gola(ps, buf, light, overlay, time * 3f, 0, 0);
        } else if (item == ModItems.FARRATA_FAN) {
            YantraRig.fan(ps, buf, light, overlay, time * 8f, 0, 0, time);
        } else if (item == ModItems.RETRO_FRIDGE) {
            ps.translate(0.5, 0, 0.5);
            ps.scale(0.62f, 0.62f, 0.62f);
            ps.translate(-0.5, 0, -0.5);
            YantraRig.fridge(ps, buf, light, overlay, 0, 0);
        } else if (item == ModItems.AC_UNIT) {
            YantraRig.ac(ps, buf, light, overlay, time * 6f);
        } else if (item == ModItems.ICE_CREAM_VAN) {
            ps.translate(0.5, 0.05, 0.5);
            ps.scale(0.23f, 0.23f, 0.23f);
            YantraRig.van(ps, buf, light, overlay, 0, 0, time, false);
        } else if (item == ModItems.VILLAGE_PORTAL) {
            ps.translate(0.5, 0.0, 0.5);
            ps.scale(0.36f, 0.36f, 0.36f);
            ps.translate(-0.5, 0.0, -0.5);
            ObjParts.PORTAL_FRAME.render(ps, buf, light, overlay);
            PortalRenderer.renderVortex(ps, buf, time, 0f);   // the swirling blue centre, not just an empty border
        }
        ps.pop();
    }
}
