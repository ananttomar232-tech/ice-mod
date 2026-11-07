package com.iceage.yantra.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** A looping sound that follows a moving source and fades with a target volume (van jingle, fan, blizzard). */
public class FollowingLoopSound extends AbstractTickableSoundInstance {
    private final Supplier<Vec3> position;
    private final DoubleSupplier targetVolume;
    private final BooleanSupplier alive;
    private final float maxVolume;
    private int quiet;

    public FollowingLoopSound(SoundEvent event, SoundSource source, Supplier<Vec3> position, DoubleSupplier targetVolume, BooleanSupplier alive,
            float maxVolume) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.position = position;
        this.targetVolume = targetVolume;
        this.alive = alive;
        this.maxVolume = maxVolume;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05f;
        Vec3 p = position.get();
        this.x = p.x;
        this.y = p.y;
        this.z = p.z;
    }

    @Override
    public void tick() {
        if (!alive.getAsBoolean()) {
            stop();
            return;
        }
        Vec3 p = position.get();
        x = p.x;
        y = p.y;
        z = p.z;
        float target = (float) Mth.clamp(targetVolume.getAsDouble(), 0, 1) * maxVolume;
        volume += (target - volume) * 0.12f;
        if (volume < 0.02f) {
            if (++quiet > 40) stop();
        } else {
            quiet = 0;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public boolean isDone() {
        return isStopped();
    }
}
