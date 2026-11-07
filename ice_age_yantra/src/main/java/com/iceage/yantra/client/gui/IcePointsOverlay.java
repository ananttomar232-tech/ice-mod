package com.iceage.yantra.client.gui;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.client.ClientProgress;
import com.iceage.yantra.entity.IceCreamTruckEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Top-left frozen-blocks counter: dark bevelled bar, ice cube icon, rolling odometer number
 * (" / 10,000,000"), and the live multiplier on the right (van combo while driving, otherwise x1.0).
 */
public final class IcePointsOverlay {
    private static final ResourceLocation BAR = IceAgeYantra.id("textures/gui/counter_bar.png");

    private IcePointsOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        double shown = Mth.lerp(pt, ClientProgress.prevDisplayed, ClientProgress.displayed);

        float mult = 1.0f;
        if (mc.player.getVehicle() instanceof IceCreamTruckEntity van) mult = van.combo();
        String multText = String.format("x%.1f", mult);
        String total = " / 10,000,000";

        int numW = font.width(String.format("%,d", (long) Math.floor(shown)));
        int barW = Math.max(200, 30 + numW + font.width(total) + 16 + font.width(multText) + 12);
        int barH = 24;
        int x = 4;
        int y = 4;

        float pop = Math.max(0f, (ClientProgress.pop - pt) / 8f);
        float s = 1f + 0.05f * pop;
        g.pose().pushPose();
        g.pose().translate(x + barW / 2f, y + barH / 2f, 0);
        g.pose().scale(s, s, 1f);
        g.pose().translate(-(x + barW / 2f), -(y + barH / 2f), 0);
        GuiTex.blitFull(g, BAR, x, y, barW, barH, 600, 96);
        g.pose().popPose();

        // ice cube icon
        float t = mc.player.tickCount + pt;
        g.pose().pushPose();
        g.pose().translate(x + 15, y + barH / 2f, 0);
        float bob = 1f + 0.05f * Mth.sin(t * 0.12f) + 0.15f * pop;
        g.pose().scale(bob, bob, 1f);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, -9, -9, 18, 18, 128, 128);
        g.pose().popPose();

        int ty = y + (barH - 8) / 2;
        int nx = x + 30;
        int numColor = pop > 0 ? 0xBFF6FF : 0xFFFFFF;
        int w = GuiTex.rolling(g, font, shown, nx, ty, numColor);
        g.drawString(font, total, nx + w, ty, 0x7F8890, true);

        int multColor = mult > 1.0f ? 0x7FE6FF : 0xFFFFFF;
        g.drawString(font, multText, x + barW - 10 - font.width(multText), ty, multColor, true);

        for (ClientProgress.Floater f : ClientProgress.FLOATERS) {
            float a = 1f - (f.age + pt) / 30f;
            int alpha = (int) (a * 255) & 0xFF;
            if (a <= 0 || alpha < 8) continue;
            String txt = "+" + String.format("%,d", f.amount);
            float fx = x + barW / 2f + (f.x - 0.5f) * 60;
            float fy = y + barH + 2 + (f.age + pt) * 0.8f;
            g.pose().pushPose();
            g.pose().translate(fx, fy, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, txt, -font.width(txt) / 2, 0, (alpha << 24) | 0x9FFFD8, true);
            g.pose().popPose();
        }
    }
}
