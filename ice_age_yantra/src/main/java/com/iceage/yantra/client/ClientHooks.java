package com.iceage.yantra.client;

import com.iceage.yantra.block.AcUnitBlockEntity;
import com.iceage.yantra.block.FarrataFanBlockEntity;
import com.iceage.yantra.client.gui.ShopScreen;
import com.iceage.yantra.client.sound.FollowingLoopSound;
import com.iceage.yantra.entity.IceCreamTruckEntity;
import com.iceage.yantra.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-only entry points called from common code through DistExecutor. */
public final class ClientHooks {
    private static final Map<Object, FollowingLoopSound> LOOPS = new WeakHashMap<>();

    private ClientHooks() {
    }

    public static void openShop() {
        Minecraft.getInstance().setScreen(new ShopScreen());
    }

    public static void ensureJingle(IceCreamTruckEntity van) {
        FollowingLoopSound s = LOOPS.get(van);
        if (s != null && !s.isDone()) return;
        FollowingLoopSound snd = new FollowingLoopSound(ModSounds.JINGLE.get(), SoundSource.NEUTRAL, van::position,
                () -> van.getPassengers().isEmpty() ? 0.0 : 0.45 + Math.min(1.0, Math.abs(van.groundSpeed) / 0.35) * 0.55,
                () -> !van.isRemoved(), 1.0f);
        LOOPS.put(van, snd);
        Minecraft.getInstance().getSoundManager().play(snd);
    }

    public static void ensureFanLoop(FarrataFanBlockEntity fan) {
        FollowingLoopSound s = LOOPS.get(fan);
        if (s != null && !s.isDone()) return;
        Vec3 p = Vec3.atCenterOf(fan.getBlockPos());
        FollowingLoopSound snd = new FollowingLoopSound(ModSounds.FAN_ROAR.get(), SoundSource.BLOCKS, () -> p,
                () -> fan.bladeSpeed / 62.0, () -> !fan.isRemoved(), 1.0f);
        LOOPS.put(fan, snd);
        Minecraft.getInstance().getSoundManager().play(snd);
    }

    public static void ensureAcLoop(AcUnitBlockEntity ac) {
        FollowingLoopSound s = LOOPS.get(ac);
        if (s != null && !s.isDone()) return;
        Vec3 p = Vec3.atCenterOf(ac.getBlockPos());
        FollowingLoopSound snd = new FollowingLoopSound(ModSounds.AC_BLIZZARD.get(), SoundSource.BLOCKS, () -> p,
                () -> ac.fanSpeed / 55.0, () -> !ac.isRemoved(), 1.0f);
        LOOPS.put(ac, snd);
        Minecraft.getInstance().getSoundManager().play(snd);
    }
}
