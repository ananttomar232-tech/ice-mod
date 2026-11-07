package com.iceage.yantra.client;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Locale;

/**
 * Top-left frozen-blocks counter (ported unchanged from the Forge version): dark bevelled bar, ice cube icon, rolling
 * odometer number (" / 10,000,000") and the live multiplier on the right (counter speed x van combo).
 */
public final class IceHud {
    private static final Identifier BAR = new Identifier(IceAgeYantra.MODID, "textures/gui/counter_bar.png");
    private static final Identifier ICE_CUBE = new Identifier(IceAgeYantra.MODID, "textures/gui/ice_cube.png");

    private IceHud() {
    }

    private static void blitFull(DrawContext g, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.drawTexture(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    public static void render(DrawContext g, float pt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        TextRenderer font = mc.textRenderer;
        double shown = MathHelper.lerp(pt, ClientIceState.prevDisplayed, ClientIceState.displayed);

        float mult = ClientIceState.speed;
        if (mc.player.getVehicle() instanceof IceVanEntity van) mult *= van.combo();
        String multText = String.format(Locale.US, "x%.1f", mult);
        String total = " / 10,000,000";

        int numW = font.getWidth(String.format(Locale.US, "%,d", (long) Math.floor(shown)));
        int barW = Math.max(200, 30 + numW + font.getWidth(total) + 16 + font.getWidth(multText) + 12);
        int barH = 24;
        int x = 4;
        int y = 4;

        float pop = Math.max(0f, (ClientIceState.pop - pt) / 8f);
        float s = 1f + 0.05f * pop;
        g.getMatrices().push();
        g.getMatrices().translate(x + barW / 2f, y + barH / 2f, 0);
        g.getMatrices().scale(s, s, 1f);
        g.getMatrices().translate(-(x + barW / 2f), -(y + barH / 2f), 0);
        blitFull(g, BAR, x, y, barW, barH, 600, 96);
        g.getMatrices().pop();

        // ice cube icon
        float t = mc.player.age + pt;
        g.getMatrices().push();
        g.getMatrices().translate(x + 15, y + barH / 2f, 0);
        float bob = 1f + 0.05f * MathHelper.sin(t * 0.12f) + 0.15f * pop;
        g.getMatrices().scale(bob, bob, 1f);
        blitFull(g, ICE_CUBE, -9, -9, 18, 18, 128, 128);
        g.getMatrices().pop();

        int ty = y + (barH - 8) / 2;
        int nx = x + 30;
        int numColor = pop > 0 ? 0xFFBFF6FF : 0xFFFFFFFF;
        int w = rolling(g, font, shown, nx, ty, numColor);
        g.drawText(font, total, nx + w, ty, 0xFF7F8890, true);

        int multColor = mult > 1.0f ? 0xFF7FE6FF : 0xFFFFFFFF;
        g.drawText(font, multText, x + barW - 10 - font.getWidth(multText), ty, multColor, true);

        // exactly one floating number: drifts up and fades
        if (ClientIceState.floaterAge < ClientIceState.FLOATER_LIFE) {
            float life = (ClientIceState.floaterAge + pt) / ClientIceState.FLOATER_LIFE;
            int alpha = (int) (MathHelper.clamp(1f - life * life, 0f, 1f) * 255f);
            if (alpha >= 8) {
                String txt = "+" + String.format(Locale.US, "%,d", ClientIceState.floaterAmount);
                float fx = nx + font.getWidth(txt) * 0.4f;
                float fy = y + barH + 14 - life * 12f;
                g.getMatrices().push();
                g.getMatrices().translate(fx, fy, 0);
                g.getMatrices().scale(0.8f, 0.8f, 1f);
                g.drawText(font, txt, -font.getWidth(txt) / 2, 0, (alpha << 24) | 0x9FFFD8, true);
                g.getMatrices().pop();
            }
        }
    }

    /** Odometer-style rolling number: every digit scrolls into the next one. */
    private static int rolling(DrawContext g, TextRenderer font, double value, int x, int y, int color) {
        long whole = (long) Math.floor(Math.max(0, value));
        double frac = Math.max(0, value) - whole;
        String s = String.format(Locale.US, "%,d", whole);
        int digits = 0;
        for (char c : s.toCharArray()) if (Character.isDigit(c)) digits++;
        int lh = 10;
        int w = font.getWidth(s);
        g.enableScissor(x - 1, y - 1, x + w + 8, y + 9);
        int cx = x;
        int seen = 0;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                g.drawText(font, String.valueOf(c), cx, y, color, true);
                cx += font.getWidth(String.valueOf(c));
                continue;
            }
            int place = digits - 1 - seen++;
            long p10 = 1;
            for (int i = 0; i < place; i++) p10 *= 10;
            double roll = (whole % p10) == p10 - 1 ? frac : 0;
            int off = (int) Math.round(roll * lh);
            int d = c - '0';
            g.drawText(font, String.valueOf(d), cx, y - off, color, true);
            if (off > 0) g.drawText(font, String.valueOf((d + 1) % 10), cx, y - off + lh, color, true);
            cx += font.getWidth(String.valueOf(d));
        }
        g.disableScissor();
        return w;
    }
}
