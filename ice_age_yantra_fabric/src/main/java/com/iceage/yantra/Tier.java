package com.iceage.yantra;

/** The five tiers of the shop. Price = frozen blocks needed to unlock the item at the showcase. */
public enum Tier {
    GOLA(1, "gola_machine", 0L, "Desi Baraf Gola Machine", 0x7FE6FF),
    FAN(2, "farrata_fan", 25L, "Farrata Table Fan", 0x8FD8FF),
    FRIDGE(3, "retro_fridge", 150L, "Retro Single-Door Fridge", 0xA8F0D8),
    AC(4, "ac_unit", 1000L, "Split AC Unit", 0xC0E0FF),
    VAN(5, "ice_cream_van", 50000L, "Musical Ice Cream Van", 0xFFB0E0);

    public static final long FINAL_TARGET = 10_000_000L;

    public final int number;
    public final String id;
    public final long price;
    public final String displayName;
    public final int color;

    Tier(int number, String id, long price, String displayName, int color) {
        this.number = number;
        this.id = id;
        this.price = price;
        this.displayName = displayName;
        this.color = color;
    }

    public static Tier byNumber(int n) {
        for (Tier t : values()) {
            if (t.number == n) return t;
        }
        return GOLA;
    }
}
