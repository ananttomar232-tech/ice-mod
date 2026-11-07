package com.iceage.yantra.client.gui;

import com.iceage.yantra.client.ClientHooks;
import com.iceage.yantra.network.CelebratePacket;
import com.iceage.yantra.progress.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Big animated banners: tier target reached, Yantra acquired, and the 10,000,000 climax. */
public final class CelebrationOverlay {
    private static String title = "";
    private static String sub = "";
    private static int color = 0x7FE6FF;
    private static int ticks;
    private static int duration;
    private static boolean rainbow;

    private CelebrationOverlay() {
    }

    public static void show(int kind, long value) {
        rainbow = false;
        switch (kind) {
            case CelebratePacket.OPEN_SHOP -> {
                ClientHooks.openShop();
                return;
            }
            case CelebratePacket.TARGET -> {
                Tier t = Tier.byNumber((int) value);
                Tier next = t == null ? null : Tier.byNumber(t.number + 1);
                title = Component.translatable("hud.ice_age_yantra.banner.target").getString();
                sub = t == null ? "" : Component.translatable("hud.ice_age_yantra.banner.target_sub", t.displayName(),
                        String.format("%,d", t.target), next == null ? Component.empty() : next.displayName()).getString();
                color = 0x7FE6FF;
                duration = 110;
            }
            case CelebratePacket.PURCHASE -> {
                Tier t = Tier.byNumber((int) value);
                title = Component.translatable("hud.ice_age_yantra.banner.purchase").getString();
                sub = t == null ? "" : t.displayName().getString();
                color = t == null ? 0xFFE27A : t.color;
                duration = 80;
            }
            case CelebratePacket.CLIMAX -> {
                title = Component.translatable("hud.ice_age_yantra.banner.climax").getString();
                sub = Component.translatable("hud.ice_age_yantra.banner.climax_sub").getString();
                color = 0xFFFFFF;
                rainbow = true;
                duration = 260;
            }
            default -> {
                return;
            }
        }
        ticks = duration;
    }

    public static void tick() {
        if (ticks > 0) ticks--;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        if (ticks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        float age = duration - ticks + pt;
        float in = Math.min(1f, age / 8f);
        float out = Math.min(1f, (ticks - pt) / 15f);
        float k = Math.max(0f, Math.min(in, out));
        float overshoot = in < 1f ? 1f + 0.25f * Mth.sin(in * Mth.PI) : 1f;
        float scale = 2.4f * overshoot;
        int cy = (int) (height * 0.27f);
        int bw = (int) Math.max(font.width(title) * scale, font.width(sub) * 1.2f) + 60;
        int bh = 52;
        int alpha = (int) (k * 255);
        if (alpha < 6) return;
        g.pose().pushPose();
        g.pose().translate(width / 2f, cy, 0);
        g.pose().scale(k, k, 1f);
        GuiTex.blitFull(g, GuiTex.HUD_PANEL, -bw / 2, -bh / 2, bw, bh, 420, 64);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, -bw / 2 + 10, -12, 24, 24, 128, 128);
        GuiTex.blitFull(g, GuiTex.ICE_CUBE, bw / 2 - 34, -12, 24, 24, 128, 128);
        int c = color;
        if (rainbow) {
            float hue = (age * 0.02f) % 1f;
            c = Mth.hsvToRgb(hue, 0.45f, 1f);
        }
        g.pose().pushPose();
        g.pose().translate(0, -bh / 2f + 7, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, (alpha << 24) | c, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, bh / 2f - 14, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(font, sub, -font.width(sub) / 2, 0, (alpha << 24) | 0xE8F7FF, true);
        g.pose().popPose();
        g.pose().popPose();
    }
}
