package com.iceage.yantra.client.gui;

import com.iceage.yantra.client.ClientProgress;
import com.iceage.yantra.progress.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Top-centre live counter: "BLOCKS FROZEN: [current] / 10,000,000" with odometer digits, ding and +N pops. */
public final class IcePointsOverlay {
    private IcePointsOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        double shown = Mth.lerp(pt, ClientProgress.prevDisplayed, ClientProgress.displayed);
        String label = "BLOCKS FROZEN: ";
        String total = " / 10,000,000";
        int numW = font.width(String.format("%,d", (long) Math.floor(shown)));
        int textW = font.width(label) + numW + font.width(total);
        int panelW = Math.max(236, textW + 44);
        int panelH = 38;
        int x = (width - panelW) / 2;
        int y = 3;
        float pop = Math.max(0f, (ClientProgress.pop - pt) / 8f);
        float s = 1f + 0.07f * pop;
        g.pose().pushPose();
        g.pose().translate(width / 2f, y + panelH / 2f, 0);
        g.pose().scale(s, s, 1f);
        g.pose().translate(-width / 2f, -(y + panelH / 2f), 0);
        GuiTex.blitFull(g, GuiTex.HUD_PANEL, x, y, panelW, panelH, 420, 64);
        g.pose().popPose();

        float t = mc.player.tickCount + pt;
        g.pose().pushPose();
        g.pose().translate(x + 17, y + 13, 0);
        float bob = 1f + 0.06f * Mth.sin(t * 0.12f) + 0.15f * pop;
        g.pose().scale(bob, bob, 1f);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, -10, -10, 20, 20, 128, 128);
        g.pose().popPose();

        int tx = x + 32;
        int ty = y + 7;
        g.drawString(font, label, tx, ty, 0xBFEFFF, true);
        int numColor = pop > 0 ? 0xFFFFFF : 0x7FE6FF;
        int w = GuiTex.rolling(g, font, shown, tx + font.width(label), ty, numColor);
        g.drawString(font, total, tx + font.width(label) + w, ty, 0x8FB8D8, true);

        long pts = ClientProgress.points;
        long[] win = GuiTex.window(pts);
        double frac = win[1] == win[0] ? 1 : (shown - win[0]) / (double) (win[1] - win[0]);
        int barX = x + 32, barW = panelW - 44, barY = y + 20;
        GuiTex.bar(g, barX, barY, barW, 6, frac);
        Tier next = Tier.byNumber((int) win[2]);
        String sub = next == null ? "ICE AGE COMPLETE!" : Component.translatable("hud.ice_age_yantra.next", next.displayName(), String.format("%,d", next.target)).getString();
        g.pose().pushPose();
        g.pose().translate(barX + barW / 2f, barY + 8, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, sub, -font.width(sub) / 2, 0, 0xD6F3FF, true);
        g.pose().popPose();

        for (ClientProgress.Floater f : ClientProgress.FLOATERS) {
            float a = 1f - (f.age + pt) / 30f;
            if (a <= 0) continue;
            int alpha = (int) (a * 255) & 0xFF;
            if (alpha < 8) continue;
            String txt = "+" + String.format("%,d", f.amount);
            float fx = x + panelW - 30 + f.x * 26;
            float fy = y + panelH - 4 + (f.age + pt) * 0.9f;
            g.pose().pushPose();
            g.pose().translate(fx, fy, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, txt, -font.width(txt) / 2, 0, (alpha << 24) | 0x9FFFD8, true);
            g.pose().popPose();
        }
    }
}
