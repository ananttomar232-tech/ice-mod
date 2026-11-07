package com.iceage.yantra.client;

import com.iceage.yantra.ModSounds;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import java.util.Map;
import java.util.WeakHashMap;

/** The van's musical jingle: a looping sound that follows the van while somebody is in it or it is rolling. */
public final class VanJingle extends MovingSoundInstance {
    private static final Map<IceVanEntity, VanJingle> ACTIVE = new WeakHashMap<>();
    private final IceVanEntity van;

    private VanJingle(IceVanEntity van) {
        super(ModSounds.VAN_JINGLE, SoundCategory.NEUTRAL, SoundInstance.createRandom());
        this.van = van;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 1.4f;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
    }

    public static void ensure(IceVanEntity van) {
        VanJingle j = ACTIVE.get(van);
        if (j != null && !j.isDone()) return;
        j = new VanJingle(van);
        ACTIVE.put(van, j);
        MinecraftClient.getInstance().getSoundManager().play(j);
    }

    @Override
    public void tick() {
        if (van.isRemoved() || (van.getPassengerList().isEmpty() && Math.abs(van.groundSpeed) < 0.02f && van.age % 200 > 120)) {
            setDone();
            return;
        }
        this.x = van.getX();
        this.y = van.getY();
        this.z = van.getZ();
    }
}
