package com.iceage.yantra;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide persistent data: per-player counter, counter speed, portal return position, shop build version. */
public class IceData extends PersistentState {
    public static final String KEY = "ice_age_yantra";
    /** NBT key of the saved portal return position (x, y, z, yaw). */
    public static final String RETURN_POS = "IcePortalReturnPos";

    public static class Entry {
        public long points;
        public float speed = 1.0f;
        public boolean gotStarter;
        public int golaBought;
        public boolean hasReturn;
        public double rx, ry, rz;
        public float ryaw;

        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putLong("points", points);
            t.putFloat("speed", speed);
            t.putBoolean("starter", gotStarter);
            t.putInt("golaBought", golaBought);
            if (hasReturn) {
                NbtCompound r = new NbtCompound();
                r.putDouble("x", rx);
                r.putDouble("y", ry);
                r.putDouble("z", rz);
                r.putFloat("yaw", ryaw);
                t.put(RETURN_POS, r);
            }
            return t;
        }

        static Entry load(NbtCompound t) {
            Entry e = new Entry();
            e.points = t.getLong("points");
            e.speed = t.contains("speed") ? t.getFloat("speed") : 1.0f;
            e.gotStarter = t.getBoolean("starter");
            e.golaBought = t.getInt("golaBought");
            if (t.contains(RETURN_POS, NbtElement.COMPOUND_TYPE)) {
                NbtCompound r = t.getCompound(RETURN_POS);
                e.hasReturn = true;
                e.rx = r.getDouble("x");
                e.ry = r.getDouble("y");
                e.rz = r.getDouble("z");
                e.ryaw = r.getFloat("yaw");
            }
            return e;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    public int shopVersion;

    public static IceData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(IceData::fromNbt, IceData::new, KEY);
    }

    public Entry entry(UUID id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    public static IceData fromNbt(NbtCompound tag) {
        IceData d = new IceData();
        d.shopVersion = tag.getInt("shopVersion");
        NbtList list = tag.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            if (c.containsUuid("id")) d.entries.put(c.getUuid("id"), Entry.load(c));
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("shopVersion", shopVersion);
        NbtList list = new NbtList();
        entries.forEach((id, e) -> {
            NbtCompound c = e.save();
            c.putUuid("id", id);
            list.add(c);
        });
        tag.put("players", list);
        return tag;
    }
}
