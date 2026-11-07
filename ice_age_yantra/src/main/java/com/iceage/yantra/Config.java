package com.iceage.yantra;

import net.minecraftforge.common.ForgeConfigSpec;

/** Tuning knobs for pacing (config/ice_age_yantra-common.toml). Tier targets are fixed by design. */
public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue GIVE_PORTAL_ON_JOIN;
    public static final ForgeConfigSpec.BooleanValue PROTECT_CONTAINERS;

    public static final ForgeConfigSpec.IntValue GOLA_CRANKS_TO_SNAP;
    public static final ForgeConfigSpec.IntValue GOLA_TICKS_PER_BLOCK;

    public static final ForgeConfigSpec.IntValue FAN_TICKS_PER_FREEZE;
    public static final ForgeConfigSpec.IntValue FAN_CONE_LENGTH;
    public static final ForgeConfigSpec.DoubleValue FAN_NUDGE_STRENGTH;

    public static final ForgeConfigSpec.IntValue FRIDGE_BLOCKS_PER_PULSE;
    public static final ForgeConfigSpec.IntValue FRIDGE_RADIUS;
    public static final ForgeConfigSpec.IntValue FRIDGE_LAUNCH_INTERVAL;

    public static final ForgeConfigSpec.IntValue AC_COLUMNS_PER_TICK;
    public static final ForgeConfigSpec.IntValue AC_AREA;
    public static final ForgeConfigSpec.IntValue AC_POINTS_PER_BLOCK;

    public static final ForgeConfigSpec.DoubleValue VAN_MAX_SPEED;
    public static final ForgeConfigSpec.IntValue VAN_POINTS_PER_BLOCK;
    public static final ForgeConfigSpec.IntValue VAN_MAX_COMBO;
    public static final ForgeConfigSpec.IntValue VAN_COMBO_RAMP_SECONDS;
    public static final ForgeConfigSpec.IntValue VAN_FREEZE_RADIUS;
    public static final ForgeConfigSpec.IntValue VAN_SAMPLES_PER_TICK;
    public static final ForgeConfigSpec.IntValue VAN_HIGHWAY_WIDTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("General").push("general");
        GIVE_PORTAL_ON_JOIN = b.comment("Give every new player the Yantra Shop Portal in hotbar slot 0").define("givePortalOnJoin", true);
        PROTECT_CONTAINERS = b.comment("Never freeze blocks that hold a block entity (chests, furnaces, ...)").define("protectContainers", true);
        b.pop();

        b.comment("Tier 1 - Desi Baraf Gola machine").push("gola");
        GOLA_CRANKS_TO_SNAP = b.defineInRange("cranksBeforeHandleSnaps", 2, 1, 10);
        GOLA_TICKS_PER_BLOCK = b.defineInRange("ticksPerFrozenBlock", 4, 1, 40);
        b.pop();

        b.comment("Tier 2 - Farrata high-speed table fan").push("fan");
        FAN_TICKS_PER_FREEZE = b.defineInRange("ticksPerFrozenBlock", 2, 1, 40);
        FAN_CONE_LENGTH = b.defineInRange("coneLength", 15, 4, 32);
        FAN_NUDGE_STRENGTH = b.comment("Backwards nudge velocity (0.9 ~ 2.5 blocks)").defineInRange("nudgeStrength", 0.9, 0.0, 3.0);
        b.pop();

        b.comment("Tier 3 - Retro single-door fridge").push("fridge");
        FRIDGE_BLOCKS_PER_PULSE = b.defineInRange("blocksPerPulse", 5, 1, 64);
        FRIDGE_RADIUS = b.defineInRange("freezeRadius", 12, 4, 32);
        FRIDGE_LAUNCH_INTERVAL = b.defineInRange("iceLaunchIntervalTicks", 10, 2, 200);
        b.pop();

        b.comment("Tier 4 - Split AC unit + turbo remote").push("ac");
        AC_COLUMNS_PER_TICK = b.defineInRange("columnsPerTick", 10, 1, 400);
        AC_AREA = b.defineInRange("areaSize", 40, 8, 96);
        AC_POINTS_PER_BLOCK = b.comment("16 C TURBO multiplier").defineInRange("pointsPerBlock", 16, 1, 1000);
        b.pop();

        b.comment("Tier 5 - Musical ice cream van").push("van");
        VAN_MAX_SPEED = b.comment("Blocks per tick").defineInRange("maxSpeed", 0.62, 0.1, 1.5);
        VAN_POINTS_PER_BLOCK = b.defineInRange("pointsPerBlock", 30, 1, 100000);
        VAN_MAX_COMBO = b.defineInRange("maxCombo", 10, 1, 100);
        VAN_COMBO_RAMP_SECONDS = b.defineInRange("comboRampSeconds", 30, 1, 600);
        VAN_FREEZE_RADIUS = b.defineInRange("freezeRadius", 9, 3, 24);
        VAN_SAMPLES_PER_TICK = b.defineInRange("freezeSamplesPerTick", 140, 10, 2000);
        VAN_HIGHWAY_WIDTH = b.defineInRange("highwayWidth", 10, 3, 32);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
