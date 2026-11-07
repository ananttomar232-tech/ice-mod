package com.iceage.yantra.client.render;

import com.iceage.yantra.block.ShopSignBlockEntity;
import com.iceage.yantra.block.ShowcasePedestalBlockEntity;
import com.iceage.yantra.client.ClientProgress;
import com.iceage.yantra.progress.Tier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Showroom pedestals (floating Yantra + glowing price tag) and the giant shop title. */
public final class ShowroomRenderers {
    private ShowroomRenderers() {
    }

    public static class Pedestal implements BlockEntityRenderer<ShowcasePedestalBlockEntity> {
        private final Font font;

        public Pedestal(BlockEntityRendererProvider.Context ctx) {
            font = ctx.getFont();
        }

        @Override
        public void render(ShowcasePedestalBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            Tier tier = be.tier();
            float time = be.age + pt;
            float bob = 0.06f * Mth.sin(time * 0.06f);
            boolean affordable = ClientProgress.points >= tier.price;
            boolean owned = (ClientProgress.mask & tier.bit()) != 0;
            int full = LightTexture.FULL_BRIGHT;
            int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            float cr = ((tier.color >> 16) & 255) / 255f, cg = ((tier.color >> 8) & 255) / 255f, cb = (tier.color & 255) / 255f;

            // beam of light from the pedestal
            YantraRig.beam(ps, buf, 0.5, 0.96, 0.5, tier == Tier.VAN ? 2.4f : 0.9f, 2.4f, cr * 0.6f + 0.2f, cg * 0.6f + 0.35f, 1f, 0.45f, time * 0.4f);

            // floating, slowly spinning Yantra
            ps.pushPose();
            ps.translate(0.5, 1.12 + bob, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(time * 0.9f));
            switch (tier) {
                case GOLA -> { ps.scale(0.85f, 0.85f, 0.85f); ps.translate(-0.5, 0, -0.5); }
                case FAN -> { ps.scale(0.8f, 0.8f, 0.8f); ps.translate(-0.5, 0, -0.5); }
                case FRIDGE -> { ps.scale(0.62f, 0.62f, 0.62f); ps.translate(-0.5, 0, -0.5); }
                case AC -> { ps.scale(0.95f, 0.95f, 0.95f); ps.translate(-0.5, 0, -0.5); }
                case VAN -> ps.scale(0.5f, 0.5f, 0.5f);
            }
            YantraRig.tier(tier, ps, buf, full, ov, time);
            ps.popPose();
            float top = switch (tier) {
                case GOLA -> 2.35f;
                case FAN -> 2.35f;
                case FRIDGE -> 2.6f;
                case AC -> 2.45f;
                case VAN -> 3.65f;
            };
            YantraRig.glow(ps, buf, 0.5, 1.6 + bob, 0.5, tier == Tier.VAN ? 4.2f : 2.0f, cr, cg, cb, 0.28f);

            // price tag (billboard)
            ps.pushPose();
            ps.translate(0.5, top + bob, 0.5);
            ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            ps.scale(-0.022f, -0.022f, 0.022f);
            Matrix4f m = ps.last().pose();
            Component name = tier.displayName();
            String price = tier.price == 0 ? "FREE" : String.format("%,d", tier.price) + " BLOCKS FROZEN";
            Component status;
            int statusColor;
            if (owned) {
                status = Component.translatable("hud.ice_age_yantra.tag.owned");
                statusColor = 0x7DFFB2;
            } else if (affordable) {
                status = Component.translatable("hud.ice_age_yantra.tag.buy");
                statusColor = 0xFFE27A;
            } else {
                status = Component.translatable("hud.ice_age_yantra.tag.locked", String.format("%,d", tier.price - ClientProgress.points));
                statusColor = 0xFF6B6B;
            }
            float pulse = 0.75f + 0.25f * Mth.sin(time * 0.2f);
            drawCentered(font, Component.literal("- TIER " + tier.number + " -"), m, buf, -30, 0xBFEFFF, 0x66000000);
            drawCentered(font, name, m, buf, -18, tier.color, 0x88001A3A);
            drawCentered(font, Component.literal(price), m, buf, -6, 0x9FF0FF, 0x66000000);
            drawCentered(font, Component.literal("Target: " + String.format("%,d", tier.target)), m, buf, 6, 0xD8E8F0, 0x55000000);
            int sc = ((int) (((statusColor >> 16) & 255) * pulse) << 16) | ((int) (((statusColor >> 8) & 255) * pulse) << 8) | (int) ((statusColor & 255) * pulse);
            drawCentered(font, status, m, buf, 18, sc, 0x77000000);
            ps.popPose();
        }

        private static void drawCentered(Font font, Component text, Matrix4f m, MultiBufferSource buf, int y, int color, int bg) {
            FormattedCharSequence seq = text.getVisualOrderText();
            float x = -font.width(seq) / 2f;
            font.drawInBatch(seq, x, y, 0xFF000000 | color, false, m, buf, Font.DisplayMode.NORMAL, bg, LightTexture.FULL_BRIGHT);
        }

        @Override
        public boolean shouldRenderOffScreen(ShowcasePedestalBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    public static class Sign implements BlockEntityRenderer<ShopSignBlockEntity> {
        private final Font font;

        public Sign(BlockEntityRendererProvider.Context ctx) {
            font = ctx.getFont();
        }

        @Override
        public void render(ShopSignBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
            ps.pushPose();
            ps.translate(0.5, 0.6 + 0.08 * Mth.sin(time * 0.05f), 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(180f));
            ps.scale(-0.09f, -0.09f, 0.09f);
            Matrix4f m = ps.last().pose();
            Component title = Component.translatable("hud.ice_age_yantra.showroom_title");
            float w = font.width(title);
            int glowA = (int) (120 + 60 * Mth.sin(time * 0.1f));
            for (int k = 0; k < 4; k++) {
                float ox = (k == 0 ? 1 : k == 1 ? -1 : 0) * 0.8f, oy = (k == 2 ? 1 : k == 3 ? -1 : 0) * 0.8f;
                ps.pushPose();
                ps.translate(ox, oy, 0.4f);
                font.drawInBatch(title.getVisualOrderText(), -w / 2f, -8, (glowA << 24) | 0x2FB8FF, false, ps.last().pose(), buf,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                ps.popPose();
            }
            font.drawInBatch(title.getVisualOrderText(), -w / 2f, -8, 0xFFEFFFFF, false, m, buf, Font.DisplayMode.NORMAL, 0x5508203F,
                    LightTexture.FULL_BRIGHT);
            ps.scale(0.5f, 0.5f, 0.5f);
            Component sub = Component.translatable("hud.ice_age_yantra.showroom_sub");
            float sw = font.width(sub);
            font.drawInBatch(sub.getVisualOrderText(), -sw / 2f, 10, 0xFF9FE8FF, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0,
                    LightTexture.FULL_BRIGHT);
            ps.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(ShopSignBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 128;
        }
    }
}
