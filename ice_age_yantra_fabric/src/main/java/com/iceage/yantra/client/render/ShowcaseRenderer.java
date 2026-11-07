package com.iceage.yantra.client.render;

import com.iceage.yantra.Tier;
import com.iceage.yantra.block.ShowcaseBlock;
import com.iceage.yantra.block.ShowcaseBlockEntity;
import com.iceage.yantra.client.ClientIceState;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Locale;

/** Ice Shop showcase (ported from the Forge showroom): carved pedestal, light beam, floating spinning Yantra, price tag. */
public class ShowcaseRenderer implements BlockEntityRenderer<ShowcaseBlockEntity> {
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private final TextRenderer font;

    public ShowcaseRenderer(BlockEntityRendererFactory.Context ctx) {
        font = ctx.getTextRenderer();
    }

    @Override
    public void render(ShowcaseBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        Tier tier = Tier.byNumber(be.getCachedState().get(ShowcaseBlock.TIER));
        ObjParts.PEDESTAL.render(ps, buf, light, overlay);
        float time = be.age + pt;
        float bob = 0.06f * MathHelper.sin(time * 0.06f);
        boolean affordable = ClientIceState.points >= tier.price;
        int ov = OverlayTexture.DEFAULT_UV;
        float cr = ((tier.color >> 16) & 255) / 255f, cg = ((tier.color >> 8) & 255) / 255f, cb = (tier.color & 255) / 255f;

        YantraRig.beam(ps, buf, 0.5, 0.96, 0.5, tier == Tier.VAN ? 2.4f : 0.9f, 2.4f, cr * 0.6f + 0.2f, cg * 0.6f + 0.35f, 1f, 0.45f, time * 0.4f);

        ps.push();
        ps.translate(0.5, 1.12 + bob, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.9f));
        switch (tier) {
            case GOLA -> {
                ps.scale(0.85f, 0.85f, 0.85f);
                ps.translate(-0.5, 0, -0.5);
            }
            case FAN -> {
                ps.scale(0.8f, 0.8f, 0.8f);
                ps.translate(-0.5, 0, -0.5);
            }
            case FRIDGE -> {
                ps.scale(0.62f, 0.62f, 0.62f);
                ps.translate(-0.5, 0, -0.5);
            }
            case AC -> {
                ps.scale(0.95f, 0.95f, 0.95f);
                ps.translate(-0.5, 0, -0.5);
            }
            case VAN -> ps.scale(0.5f, 0.5f, 0.5f);
        }
        YantraRig.tier(tier, ps, buf, FULL, ov, time);
        ps.pop();
        float top = switch (tier) {
            case GOLA, FAN -> 2.35f;
            case FRIDGE -> 2.6f;
            case AC -> 2.45f;
            case VAN -> 3.65f;
        };
        YantraRig.glow(ps, buf, 0.5, 1.6 + bob, 0.5, tier == Tier.VAN ? 4.2f : 2.0f, cr, cg, cb, 0.28f);

        // price tag (billboard)
        ps.push();
        ps.translate(0.5, top + bob, 0.5);
        ps.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        ps.scale(-0.022f, -0.022f, 0.022f);
        Matrix4f m = ps.peek().getPositionMatrix();
        String price = tier.price == 0 ? "FREE" : String.format(Locale.US, "%,d", tier.price) + " BLOCKS FROZEN";
        String status;
        int statusColor;
        if (affordable) {
            status = "RIGHT-CLICK TO TAKE";
            statusColor = 0xFFE27A;
        } else {
            status = "LOCKED - " + String.format(Locale.US, "%,d", tier.price - ClientIceState.points) + " MORE";
            statusColor = 0xFF6B6B;
        }
        float pulse = 0.75f + 0.25f * MathHelper.sin(time * 0.2f);
        int sc = ((int) (((statusColor >> 16) & 255) * pulse) << 16) | ((int) (((statusColor >> 8) & 255) * pulse) << 8) | (int) ((statusColor & 255) * pulse);
        line(m, buf, "- TIER " + tier.number + " -", -30, 0xBFEFFF, 0x66000000);
        line(m, buf, tier.displayName, -18, tier.color, 0x88001A3A);
        line(m, buf, price, -6, 0x9FF0FF, 0x66000000);
        line(m, buf, status, 8, sc, 0x77000000);
        ps.pop();
    }

    private void line(Matrix4f m, VertexConsumerProvider buf, String s, int y, int color, int bg) {
        float x = -font.getWidth(s) / 2f;
        font.draw(Text.literal(s), x, y, 0xFF000000 | color, false, m, buf, TextRenderer.TextLayerType.NORMAL, bg, FULL);
    }

    @Override
    public boolean rendersOutsideBoundingBox(ShowcaseBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
