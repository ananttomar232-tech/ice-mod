package com.iceage.yantra.registry;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IceAgeYantra.MODID);

    public static final RegistryObject<SoundEvent> JINGLE = reg("van_jingle");
    public static final RegistryObject<SoundEvent> HONK = reg("van_honk");
    public static final RegistryObject<SoundEvent> HANDLE_POP = reg("handle_pop");
    public static final RegistryObject<SoundEvent> CRANK = reg("crank");
    public static final RegistryObject<SoundEvent> SQUEAK = reg("squeak");
    public static final RegistryObject<SoundEvent> SPUTTER = reg("sputter");
    public static final RegistryObject<SoundEvent> WHEEL_SPIN = reg("wheel_spin");
    public static final RegistryObject<SoundEvent> FAN_ROAR = reg("fan_roar");
    public static final RegistryObject<SoundEvent> FAN_WHOOSH = reg("fan_whoosh");
    public static final RegistryObject<SoundEvent> FRIDGE_OPEN = reg("fridge_open");
    public static final RegistryObject<SoundEvent> FRIDGE_SLAM = reg("fridge_slam");
    public static final RegistryObject<SoundEvent> FRIDGE_RATTLE = reg("fridge_rattle");
    public static final RegistryObject<SoundEvent> VILLAGER_SCREAM = reg("villager_scream");
    public static final RegistryObject<SoundEvent> VILLAGER_RELIEF = reg("villager_relief");
    public static final RegistryObject<SoundEvent> AC_BLIZZARD = reg("ac_blizzard");
    public static final RegistryObject<SoundEvent> REMOTE_BEEP = reg("remote_beep");
    public static final RegistryObject<SoundEvent> REMOTE_STANDBY = reg("remote_standby");
    public static final RegistryObject<SoundEvent> DING = reg("ding");
    public static final RegistryObject<SoundEvent> FREEZE = reg("freeze");
    public static final RegistryObject<SoundEvent> PURCHASE = reg("purchase");
    public static final RegistryObject<SoundEvent> PORTAL_WHOOSH = reg("portal_whoosh");
    public static final RegistryObject<SoundEvent> TARGET_REACHED = reg("target_reached");
    public static final RegistryObject<SoundEvent> FANFARE = reg("fanfare");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(IceAgeYantra.id(name)));
    }

    private ModSounds() {
    }
}
