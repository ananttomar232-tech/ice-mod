package com.iceage.yantra.progress;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide persistent progress: frozen blocks per player, purchases, showroom return points. */
public class IceProgress extends SavedData {
    public static final String NAME = "ice_age_yantra_progress";

    public static class Entry {
        public long points;
        public int purchasedMask;
        public boolean portalGiven;
        public boolean climaxDone;
        public float speed = 1.0f;
        public ResourceKey<Level> returnDim;
        public Vec3 returnPos;
        public float returnYaw;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("points", points);
            t.putInt("purchased", purchasedMask);
            t.putBoolean("portalGiven", portalGiven);
            t.putBoolean("climax", climaxDone);
            t.putFloat("speed", speed);
            if (returnDim != null && returnPos != null) {
                t.putString("rdim", returnDim.location().toString());
                t.putDouble("rx", returnPos.x);
                t.putDouble("ry", returnPos.y);
                t.putDouble("rz", returnPos.z);
                t.putFloat("ryaw", returnYaw);
            }
            return t;
        }

        @SuppressWarnings("removal")
        static Entry load(CompoundTag t) {
            Entry e = new Entry();
            e.points = t.getLong("points");
            e.purchasedMask = t.getInt("purchased");
            e.portalGiven = t.getBoolean("portalGiven");
            e.climaxDone = t.getBoolean("climax");
            e.speed = t.contains("speed") ? t.getFloat("speed") : 1.0f;
            if (t.contains("rdim")) {
                e.returnDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("rdim")));
                e.returnPos = new Vec3(t.getDouble("rx"), t.getDouble("ry"), t.getDouble("rz"));
                e.returnYaw = t.getFloat("ryaw");
            }
            return e;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static IceProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(IceProgress::load, IceProgress::new, NAME);
    }

    public Entry entry(UUID id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    public static IceProgress load(CompoundTag tag) {
        IceProgress p = new IceProgress();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("id")) {
                p.entries.put(c.getUUID("id"), Entry.load(c));
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((id, e) -> {
            CompoundTag c = e.save();
            c.putUUID("id", id);
            list.add(c);
        });
        tag.put("players", list);
        return tag;
    }
}
