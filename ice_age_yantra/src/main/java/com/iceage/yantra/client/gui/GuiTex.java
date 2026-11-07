package com.iceage.yantra.client.gui;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.progress.Tier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** GUI textures + small drawing helpers shared by the HUD, overlays and the shop screen. */
public final class GuiTex {
    public static final ResourceLocation HUD_PANEL = IceAgeYantra.id("textures/gui/hud_panel.png");
    public static final ResourceLocation ICE_CUBE = IceAgeYantra.id("textures/gui/ice_cube.png");
    public static final ResourceLocation PROGRESS = IceAgeYantra.id("textures/gui/progress.png");
    public static final ResourceLocation REMOTE_LCD = IceAgeYantra.id("textures/gui/remote_lcd.png");
    public static final ResourceLocation SHOP_PANEL = IceAgeYantra.id("textures/gui/shop_panel.png");
    public static final ResourceLocation CARD = IceAgeYantra.id("textures/gui/card.png");
    public static final ResourceLocation BTN_NORMAL = IceAgeYantra.id("textures/gui/button_normal.png");
    public static final ResourceLocation BTN_HOVER = IceAgeYantra.id("textures/gui/button_hover.png");
    public static final ResourceLocation BTN_LOCKED = IceAgeYantra.id("textures/gui/button_locked.png");
    public static final ResourceLocation BTN_OWNED = IceAgeYantra.id("textures/gui/button_owned.png");

    private GuiTex() {
    }

    public static void blitFull(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    /** Progress bar from the 2-row progress texture (top = empty, bottom = full). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, double frac) {
        RenderSystem.enableBlend();
        g.blit(PROGRESS, x, y, w, h, 0, 0, 256, 16, 256, 32);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) g.blit(PROGRESS, x, y, fw, h, 0, 16, (int) Math.max(1, 256 * (fw / (double) w)), 16, 256, 32);
    }

    public static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, -font.width(s) / 2, 0, color, true);
        g.pose().popPose();
    }

    /** Odometer-style rolling number: every digit scrolls into the next one. */
    public static int rolling(GuiGraphics g, Font font, double value, int x, int y, int color) {
        long whole = (long) Math.floor(Math.max(0, value));
        double frac = Math.max(0, value) - whole;
        String s = String.format("%,d", whole);
        int digits = 0;
        for (char c : s.toCharArray()) if (Character.isDigit(c)) digits++;
        int lh = 10;
        int w = font.width(s);
        g.enableScissor(x - 1, y - 1, x + w + 8, y + 9);
        int cx = x;
        int seen = 0;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                g.drawString(font, String.valueOf(c), cx, y, color, true);
                cx += font.width(String.valueOf(c));
                continue;
            }
            int place = digits - 1 - seen++;
            long p10 = pow10(place);
            double roll = (whole % p10) == p10 - 1 ? frac : 0;
            int off = (int) Math.round(roll * lh);
            int d = c - '0';
            g.drawString(font, String.valueOf(d), cx, y - off, color, true);
            if (off > 0) g.drawString(font, String.valueOf((d + 1) % 10), cx, y - off + lh, color, true);
            cx += font.width(String.valueOf(d));
        }
        g.disableScissor();
        return w;
    }

    private static long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }

    /** The tier whose target is the next milestone and the previous milestone. */
    public static long[] window(long points) {
        long prev = 0;
        for (Tier t : Tier.values()) {
            if (points < t.target) return new long[]{prev, t.target, t.number};
            prev = t.target;
        }
        return new long[]{Tier.VAN.target, Tier.VAN.target, 6};
    }
}
