package com.iceage.yantra;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The sounds of the Forge version (same OGG files and sounds.json). */
public final class ModSounds {
    public static final SoundEvent VAN_JINGLE = sound("van_jingle");
    public static final SoundEvent VAN_HONK = sound("van_honk");
    public static final SoundEvent HANDLE_POP = sound("handle_pop");
    public static final SoundEvent CRANK = sound("crank");
    public static final SoundEvent WHEEL_SPIN = sound("wheel_spin");
    public static final SoundEvent SPUTTER = sound("sputter");
    public static final SoundEvent SQUEAK = sound("squeak");
    public static final SoundEvent FAN_WHOOSH = sound("fan_whoosh");
    public static final SoundEvent FAN_ROAR = sound("fan_roar");
    public static final SoundEvent FRIDGE_OPEN = sound("fridge_open");
    public static final SoundEvent FRIDGE_SLAM = sound("fridge_slam");
    public static final SoundEvent AC_BLIZZARD = sound("ac_blizzard");
    public static final SoundEvent REMOTE_BEEP = sound("remote_beep");
    public static final SoundEvent REMOTE_STANDBY = sound("remote_standby");
    public static final SoundEvent DING = sound("ding");
    public static final SoundEvent FREEZE = sound("freeze");
    public static final SoundEvent PURCHASE = sound("purchase");
    public static final SoundEvent TARGET_REACHED = sound("target_reached");
    public static final SoundEvent FANFARE = sound("fanfare");
    public static final SoundEvent PORTAL_WHOOSH = sound("portal_whoosh");

    private ModSounds() {
    }

    private static SoundEvent sound(String name) {
        Identifier id = new Identifier(IceAgeYantra.MODID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }
}
