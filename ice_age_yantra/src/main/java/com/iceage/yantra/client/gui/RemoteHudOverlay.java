package com.iceage.yantra.client.gui;

import com.iceage.yantra.network.RemoteHudPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** The Turbo Remote's digital LCD: "16 C TURBO" / "STANDBY" / "PAIRED" / "NO SIGNAL". */
public final class RemoteHudOverlay {
    private static int mode = -1;
    private static int ticks;
    private static int age;

    private RemoteHudOverlay() {
    }

    public static void show(int m) {
        mode = m;
        ticks = m == RemoteHudPacket.STANDBY ? 140 : 90;
        age = 0;
    }

    public static void tick() {
        if (ticks > 0) ticks--;
        age++;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        if (ticks <= 0 || mode < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Font font = mc.font;
        float in = Math.min(1f, (age + pt) / 6f);
        float out = Math.min(1f, (ticks - pt) / 10f);
        float k = Math.max(0f, Math.min(in, out));
        int w = 150, h = 60;
        int x = (int) (width - 8 - w + (1f - k) * (w + 12));
        int y = height / 2 - h / 2 - 20;
        GuiTex.blitFull(g, GuiTex.REMOTE_LCD, x, y, w, h, 300, 120);
        int lcd = 0xFF062A33;
        int dim = 0xFF4A8A96;
        boolean blink = (age / 6) % 2 == 0;
        switch (mode) {
            case RemoteHudPacket.TURBO -> {
                g.pose().pushPose();
                g.pose().translate(x + 14, y + 12, 0);
                g.pose().scale(2.6f, 2.6f, 1f);
                g.drawString(font, "16°C", 0, 0, lcd, false);
                g.pose().popPose();
                if (blink) GuiTex.blitFull(g, GuiTex.ICE_CUBE, x + 70, y + 12, 11, 11, 128, 128);
                g.drawString(font, "TURBO", x + 84, y + 15, lcd, false);
                for (int i = 0; i < 4; i++) {
                    int bh = 4 + i * 4;
                    g.fill(x + 84 + i * 8, y + 44 - bh, x + 89 + i * 8, y + 44, lcd);
                }
                g.drawString(font, "FAN MAX", x + 82, y + 30, dim, false);
            }
            case RemoteHudPacket.STANDBY -> {
                g.pose().pushPose();
                g.pose().translate(x + 14, y + 12, 0);
                g.pose().scale(2.6f, 2.6f, 1f);
                g.drawString(font, "--°C", 0, 0, dim, false);
                g.pose().popPose();
                g.drawString(font, blink ? "STANDBY" : "", x + 82, y + 15, lcd, false);
                g.drawString(font, "50,000 ✔", x + 82, y + 30, lcd, false);
            }
            case RemoteHudPacket.PAIRED -> {
                g.pose().pushPose();
                g.pose().translate(x + 14, y + 16, 0);
                g.pose().scale(1.8f, 1.8f, 1f);
                g.drawString(font, "PAIRED ✔", 0, 0, lcd, false);
                g.pose().popPose();
                g.drawString(font, "AC UNIT LINKED", x + 14, y + 38, dim, false);
            }
            default -> {
                g.pose().pushPose();
                g.pose().translate(x + 14, y + 16, 0);
                g.pose().scale(1.8f, 1.8f, 1f);
                g.drawString(font, blink ? "NO SIGNAL" : "", 0, 0, lcd, false);
                g.pose().popPose();
                g.drawString(font, "PLACE AN AC UNIT", x + 14, y + 38, dim, false);
            }
        }
    }
}
