package com.iceage.yantra;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers what every frozen block used to be (grass, dirt, water, ...) so that when Gotiya's hairdryer melts it the
 * ORIGINAL ground comes back instead of a hole. Saved with the world.
 */
public class FrozenMemory extends PersistentState {
    private final Long2ObjectOpenHashMap<BlockState> map = new Long2ObjectOpenHashMap<>();

    public static FrozenMemory get(ServerWorld w) {
        return w.getPersistentStateManager().getOrCreate(FrozenMemory::fromNbt, FrozenMemory::new, "ice_age_frozen_memory");
    }

    public void remember(BlockPos pos, BlockState original) {
        map.putIfAbsent(pos.asLong(), original);
        markDirty();
    }

    /** The block that was here before it froze, or null when unknown (e.g. a landed ice projectile). */
    public BlockState take(BlockPos pos) {
        BlockState s = map.remove(pos.asLong());
        if (s != null) markDirty();
        return s;
    }

    public static FrozenMemory fromNbt(NbtCompound tag) {
        FrozenMemory m = new FrozenMemory();
        NbtList pal = tag.getList("palette", NbtElement.COMPOUND_TYPE);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < pal.size(); i++) states.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), pal.getCompound(i)));
        long[] pos = tag.getLongArray("pos");
        int[] idx = tag.getIntArray("idx");
        for (int i = 0; i < pos.length && i < idx.length; i++) {
            if (idx[i] >= 0 && idx[i] < states.size()) m.map.put(pos[i], states.get(idx[i]));
        }
        return m;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        Map<BlockState, Integer> ids = new HashMap<>();
        NbtList pal = new NbtList();
        long[] pos = new long[map.size()];
        int[] idx = new int[map.size()];
        int i = 0;
        for (var e : map.long2ObjectEntrySet()) {
            BlockState s = e.getValue();
            Integer id = ids.get(s);
            if (id == null) {
                id = pal.size();
                ids.put(s, id);
                pal.add(NbtHelper.fromBlockState(s));
            }
            pos[i] = e.getLongKey();
            idx[i++] = id;
        }
        tag.put("palette", pal);
        tag.putLongArray("pos", pos);
        tag.putIntArray("idx", idx);
        return tag;
    }
}
