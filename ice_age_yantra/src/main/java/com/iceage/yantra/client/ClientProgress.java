package com.iceage.yantra.client;

import com.iceage.yantra.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.ArrayList;
import java.util.List;

/** Client mirror of the frozen-block counter + the state of the animated HUD counter. */
public final class ClientProgress {
    public static long points;
    public static int mask;
    public static double displayed;
    public static double prevDisplayed;
    public static int pop;
    public static int lastDingTick;
    public static final List<Floater> FLOATERS = new ArrayList<>();
    private static boolean initialised;

    public static class Floater {
        public final long amount;
        public int age;
        public final float x;

        Floater(long amount, float x) {
            this.amount = amount;
            this.x = x;
        }
    }

    private ClientProgress() {
    }

    public static void update(long newPoints, int newMask, long gained) {
        points = newPoints;
        mask = newMask;
        if (!initialised) {
            displayed = prevDisplayed = newPoints;
            initialised = true;
            return;
        }
        if (gained > 0) {
            pop = 8;
            Minecraft mc = Minecraft.getInstance();
            int now = mc.player == null ? 0 : mc.player.tickCount;
            if (now - lastDingTick >= 3) {
                lastDingTick = now;
                float pitch = (float) Math.min(2.0, 0.9 + Math.log10(Math.max(1, gained)) * 0.18);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DING.get(), pitch, 0.45f));
            }
            if (FLOATERS.size() < 8) FLOATERS.add(new Floater(gained, (float) Math.random()));
        }
        if (newPoints < displayed) displayed = prevDisplayed = newPoints;
    }

    public static void tick() {
        prevDisplayed = displayed;
        double diff = points - displayed;
        if (Math.abs(diff) < 0.5) {
            displayed = points;
        } else {
            double step = diff * 0.18;
            if (Math.abs(step) < 0.35) step = Math.signum(diff) * 0.35;
            displayed += step;
        }
        if (pop > 0) pop--;
        FLOATERS.removeIf(f -> ++f.age > 30);
    }

    public static void reset() {
        initialised = false;
        points = 0;
        mask = 0;
        displayed = prevDisplayed = 0;
        FLOATERS.clear();
    }
}
