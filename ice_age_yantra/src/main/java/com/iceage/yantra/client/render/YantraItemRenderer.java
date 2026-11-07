package com.iceage.yantra.client.render;

import com.iceage.yantra.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Item rendering of the Yantras: the full animated 3D rig in hand, inventory and item frames. */
public class YantraItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static YantraItemRenderer instance;

    public static YantraItemRenderer get() {
        if (instance == null) instance = new YantraItemRenderer();
        return instance;
    }

    private YantraItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Item item = stack.getItem();
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + mc.getFrameTime();
        ps.pushPose();
        if (item == ModItems.GOLA_MACHINE.get()) {
            YantraRig.gola(ps, buf, light, overlay, time * 3f, 0, 0);
        } else if (item == ModItems.FARRATA_FAN.get()) {
            YantraRig.fan(ps, buf, light, overlay, time * 8f, 0, 0, time);
        } else if (item == ModItems.RETRO_FRIDGE.get()) {
            ps.translate(0.5, 0, 0.5);
            ps.scale(0.5f, 0.5f, 0.5f);
            ps.translate(-0.5, 0, -0.5);
            YantraRig.fridge(ps, buf, light, overlay, 0, 0);
        } else if (item == ModItems.AC_UNIT.get()) {
            YantraRig.ac(ps, buf, light, overlay, time * 6f);
        } else if (item == ModItems.ICE_CREAM_VAN.get()) {
            ps.translate(0.5, 0.05, 0.5);
            ps.scale(0.2f, 0.2f, 0.2f);
            YantraRig.van(ps, buf, light, overlay, 0, 0, time, false);
        }
        ps.popPose();
    }
}
