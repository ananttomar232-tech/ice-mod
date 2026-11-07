package com.iceage.yantra.progress;

import com.iceage.yantra.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** The five Yantras. price = frozen blocks needed to unlock, target = auto-stop threshold. */
public enum Tier {
    GOLA(1, "gola_machine", 0L, 25L, 0xFF5A6E),
    FAN(2, "farrata_fan", 25L, 150L, 0x6FB8FF),
    FRIDGE(3, "retro_fridge", 150L, 1_000L, 0xFF8A8A),
    AC(4, "ac_unit", 1_000L, 50_000L, 0x8FF3FF),
    VAN(5, "ice_cream_van", 50_000L, 10_000_000L, 0xFF9CE0);

    public static final long FINAL_TARGET = 10_000_000L;

    public final int number;
    public final String id;
    public final long price;
    public final long target;
    public final int color;

    Tier(int number, String id, long price, long target, int color) {
        this.number = number;
        this.id = id;
        this.price = price;
        this.target = target;
        this.color = color;
    }

    public Item item() {
        return switch (this) {
            case GOLA -> ModItems.GOLA_MACHINE.get();
            case FAN -> ModItems.FARRATA_FAN.get();
            case FRIDGE -> ModItems.RETRO_FRIDGE.get();
            case AC -> ModItems.AC_UNIT.get();
            case VAN -> ModItems.ICE_CREAM_VAN.get();
        };
    }

    public Component displayName() {
        return Component.translatable("tier.ice_age_yantra." + id);
    }

    public Component gag() {
        return Component.translatable("tier.ice_age_yantra." + id + ".gag");
    }

    public int bit() {
        return 1 << (number - 1);
    }

    public static Tier byNumber(int n) {
        for (Tier t : values()) {
            if (t.number == n) return t;
        }
        return null;
    }

    /** The highest tier whose target the given point total has not reached yet (what you should be running). */
    public static Tier current(long points) {
        for (Tier t : values()) {
            if (points < t.target) return t;
        }
        return VAN;
    }
}
