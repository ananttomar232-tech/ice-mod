package com.iceage.yantra;

import net.minecraft.util.Identifier;

/** Network channel ids. */
public final class Net {
    /** Server -> client: counter value, counter speed multiplier, gain since last packet. */
    public static final Identifier POINTS = new Identifier(IceAgeYantra.MODID, "points");

    private Net() {
    }
}
