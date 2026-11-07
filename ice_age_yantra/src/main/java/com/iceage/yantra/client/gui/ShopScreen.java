package com.iceage.yantra.client.gui;

import com.iceage.yantra.client.ClientProgress;
import com.iceage.yantra.client.render.YantraRig;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.network.PurchasePacket;
import com.iceage.yantra.network.ShowroomPacket;
import com.iceage.yantra.progress.Tier;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** The 3D Yantra Progression Shop: five live-rendered Yantras on frosted cards with buy buttons. */
public class ShopScreen extends Screen {
    private int px, py, pw, ph;
    private final float[] spin = new float[5];
    private final float[] hoverGlow = new float[5];
    private int age;

    public ShopScreen() {
        super(Component.translatable("gui.ice_age_yantra.shop.title"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 500);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cardW() {
        return (pw - 28 - 4 * 8) / 5;
    }

    private int cardX(int i) {
        return px + 14 + i * (cardW() + 8);
    }

    private int cardY() {
        return py + 60;
    }

    private int cardH() {
        return ph - 60 - 40;
    }

    private int[] buttonRect(int i) {
        int w = cardW() - 12;
        return new int[]{cardX(i) + 6, cardY() + cardH() - 22, w, 16};
    }

    private int[] showroomRect() {
        return new int[]{px + 14, py + ph - 32, 190, 20};
    }

    private int[] closeRect() {
        return new int[]{px + pw - 14 - 90, py + ph - 32, 90, 20};
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && my >= r[1] && mx < r[0] + r[2] && my < r[1] + r[3];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        float t = age + pt;
        GuiTex.blitFull(g, GuiTex.SHOP_PANEL, px, py, pw, ph, 512, 320);
        // title + counter
        GuiTex.centered(g, font, title.getString(), width / 2, py + 10, 0xEFFFFF, 1.6f);
        int tw = (int) (font.width(title.getString()) * 1.6f / 2f);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, width / 2 - tw - 22, py + 6, 16, 16, 128, 128);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, width / 2 + tw + 6, py + 6, 16, 16, 128, 128);
        long pts = ClientProgress.points;
        double shown = Mth.lerp(pt, ClientProgress.prevDisplayed, ClientProgress.displayed);
        String label = "BLOCKS FROZEN: ";
        String total = " / 10,000,000";
        int numW = font.width(String.format("%,d", (long) shown));
        int lineW = font.width(label) + numW + font.width(total);
        int lx = width / 2 - lineW / 2;
        g.drawString(font, label, lx, py + 30, 0xBFEFFF, true);
        GuiTex.rolling(g, font, shown, lx + font.width(label), py + 30, 0x7FE6FF);
        g.drawString(font, total, lx + font.width(label) + numW, py + 30, 0x8FB8D8, true);
        long[] win = GuiTex.window(pts);
        double frac = win[1] == win[0] ? 1 : (shown - win[0]) / (double) (win[1] - win[0]);
        GuiTex.bar(g, px + 60, py + 42, pw - 120, 7, frac);

        List<FormattedCharSequence> tooltip = null;
        for (int i = 0; i < 5; i++) {
            Tier tier = Tier.values()[i];
            int cx = cardX(i), cy = cardY(), cw = cardW(), chh = cardH();
            boolean hover = mouseX >= cx && mouseY >= cy && mouseX < cx + cw && mouseY < cy + chh;
            hoverGlow[i] += ((hover ? 1f : 0f) - hoverGlow[i]) * 0.2f;
            spin[i] += hover ? 3.0f : 0.8f;
            boolean owned = (ClientProgress.mask & tier.bit()) != 0;
            boolean affordable = pts >= tier.price;
            float lift = hoverGlow[i] * 3f;
            g.pose().pushPose();
            g.pose().translate(0, -lift, 0);
            GuiTex.blitFull(g, GuiTex.CARD, cx, cy, cw, chh, 200, 260);
            if (hoverGlow[i] > 0.02f) {
                int a = (int) (hoverGlow[i] * 90);
                g.fill(cx + 2, cy + 2, cx + cw - 2, cy + 4, (a << 24) | (tier.color & 0xFFFFFF));
            }
            GuiTex.centered(g, font, "TIER " + tier.number, cx + cw / 2, cy + 6, affordable ? 0x9FF0FF : 0x6C7A8A, 0.8f);
            renderModel(g, tier, cx + cw / 2f, cy + 16 + (chh - 70) / 2f, Math.min(cw * 0.55f, (chh - 70) * 0.55f), spin[i] + pt, t, !affordable);
            if (!affordable) {
                GuiTex.centered(g, font, "🔒", cx + cw / 2, cy + (chh - 70) / 2 + 8, 0xFFFFFF, 1.5f);
            }
            List<FormattedCharSequence> name = font.split(tier.displayName(), cw - 6);
            int ny = cy + chh - 64;
            for (FormattedCharSequence line : name) {
                g.drawString(font, line, cx + cw / 2 - font.width(line) / 2, ny, tier.color, true);
                ny += 9;
            }
            String price = tier.price == 0 ? "FREE" : String.format("%,d", tier.price);
            int pw2 = (int) (font.width(price) * 0.85f);
            GuiTex.blitFull(g, GuiTex.ICE_CUBE, cx + cw / 2 - pw2 / 2 - 11, cy + chh - 44, 9, 9, 128, 128);
            GuiTex.centered(g, font, price, cx + cw / 2 + 5, cy + chh - 42, 0xEFFFFF, 0.85f);
            GuiTex.centered(g, font, "target " + String.format("%,d", tier.target), cx + cw / 2, cy + chh - 32, 0x8FB8D8, 0.7f);
            int[] br = buttonRect(i);
            boolean bh = in(br, mouseX, mouseY + lift);
            ResourceLocation btn = !affordable ? GuiTex.BTN_LOCKED : owned ? GuiTex.BTN_OWNED : bh ? GuiTex.BTN_HOVER : GuiTex.BTN_NORMAL;
            GuiTex.blitFull(g, btn, br[0], br[1], br[2], br[3], 180, 40);
            String bl = !affordable ? "LOCKED" : owned ? "BUY AGAIN" : "BUY";
            GuiTex.centered(g, font, bl, br[0] + br[2] / 2, br[1] + 4, 0xFFFFFF, 1f);
            g.pose().popPose();
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(tier.displayName().getVisualOrderText());
                tooltip.addAll(font.split(tier.gag(), 200));
                if (!affordable) {
                    tooltip.add(Component.translatable("hud.ice_age_yantra.tag.locked", String.format("%,d", tier.price - pts)).getVisualOrderText());
                }
            }
        }
        int[] sr = showroomRect();
        GuiTex.blitFull(g, in(sr, mouseX, mouseY) ? GuiTex.BTN_HOVER : GuiTex.BTN_NORMAL, sr[0], sr[1], sr[2], sr[3], 180, 40);
        GuiTex.centered(g, font, Component.translatable("gui.ice_age_yantra.shop.showroom").getString(), sr[0] + sr[2] / 2, sr[1] + 6, 0xFFFFFF, 1f);
        int[] cr = closeRect();
        GuiTex.blitFull(g, in(cr, mouseX, mouseY) ? GuiTex.BTN_HOVER : GuiTex.BTN_LOCKED, cr[0], cr[1], cr[2], cr[3], 180, 40);
        GuiTex.centered(g, font, Component.translatable("gui.ice_age_yantra.shop.close").getString(), cr[0] + cr[2] / 2, cr[1] + 6, 0xFFFFFF, 1f);
        GuiTex.centered(g, font, Component.translatable("gui.ice_age_yantra.shop.hint").getString(), px + pw / 2 + 50, py + ph - 26, 0x8FB8D8, 0.7f);
        super.render(g, mouseX, mouseY, pt);
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderModel(GuiGraphics g, Tier tier, float cx, float cy, float size, float spinDeg, float time, boolean locked) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 150);
        float s = size;
        float h;
        switch (tier) {
            case GOLA -> {
                h = 1.2f;
                s *= 1.25f;
            }
            case FAN -> h = 1.3f;
            case FRIDGE -> {
                h = 2.0f;
                s *= 0.62f;
            }
            case AC -> {
                h = 0.75f;
                s *= 0.82f;
            }
            default -> {
                h = 3.4f;
                s *= 0.2f;
            }
        }
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(-18f));
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg + 200f));
        if (tier == Tier.VAN) ps.translate(0, -h / 2f, 0);
        else ps.translate(-0.5, -h / 2f, -0.5);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        int light = locked ? LightTexture.pack(4, 4) : LightTexture.FULL_BRIGHT;
        YantraRig.tier(tier, ps, buf, light, OverlayTexture.NO_OVERLAY, time);
        buf.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < 5; i++) {
                if (in(buttonRect(i), mx, my)) {
                    Tier tier = Tier.values()[i];
                    ModNetwork.CHANNEL.sendToServer(new PurchasePacket(tier.number));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    return true;
                }
            }
            if (in(showroomRect(), mx, my)) {
                ModNetwork.CHANNEL.sendToServer(new ShowroomPacket());
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                onClose();
                return true;
            }
            if (in(closeRect(), mx, my)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
