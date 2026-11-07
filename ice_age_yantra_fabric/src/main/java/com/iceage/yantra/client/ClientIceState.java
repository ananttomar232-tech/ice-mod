package com.iceage.yantra.client;

/** Client mirror of the counter plus the animation state of the HUD (rolling number, pop, one floating +N). */
public final class ClientIceState {
    public static long points;
    public static float speed = 1.0f;
    public static double displayed, prevDisplayed;
    public static int pop;
    /** The single floating "+N" number: its amount and age in ticks (age >= LIFE means hidden). */
    public static long floaterAmount;
    public static int floaterAge = 999;
    public static final int FLOATER_LIFE = 30;
    private static boolean initialised;

    private ClientIceState() {
    }

    public static void update(long newPoints, float newSpeed, long gained) {
        points = newPoints;
        speed = newSpeed;
        if (!initialised) {
            displayed = prevDisplayed = newPoints;
            initialised = true;
            return;
        }
        if (gained > 0) {
            pop = 8;
            // exactly one floating number at a time: young ones merge, otherwise it restarts
            if (floaterAge < 12) {
                floaterAmount += gained;
            } else {
                floaterAmount = gained;
                floaterAge = 0;
            }
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
        if (floaterAge < 999) floaterAge++;
    }

    public static void reset() {
        initialised = false;
        points = 0;
        speed = 1.0f;
        displayed = prevDisplayed = 0;
        floaterAge = 999;
    }
}
