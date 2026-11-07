package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

/**
 * Holds only the client animation state of a Yantra (wheel, blades, rotor, fridge door). The server just sends a
 * synced block event whenever somebody uses the machine.
 */
public class YantraBlockEntity extends BlockEntity {
    public static final int EVENT_ACTIVATE = 1;
    public static final int RUN_TICKS = 90;

    public int age;
    public int active;
    public float angle, prevAngle, speed, door, prevDoor;

    public YantraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.YANTRA_BE, pos, state);
    }

    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type == EVENT_ACTIVATE) {
            active = RUN_TICKS;
            return true;
        }
        return super.onSyncedBlockEvent(type, data);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, YantraBlockEntity be) {
        be.age++;
        be.prevAngle = be.angle;
        be.prevDoor = be.door;
        float target = be.active > 0 ? 1f : 0f;
        if (be.active > 0) be.active--;
        be.speed += (target * 52f - be.speed) * (target > 0 ? 0.12f : 0.04f);
        if (be.speed < 0.05f) be.speed = 0;
        be.angle += be.speed;
        // fridge: door swings open while running, then closes
        float doorTarget = be.active > 6 ? 108f : 0f;
        be.door += (doorTarget - be.door) * (doorTarget > be.door ? 0.16f : 0.3f);
    }
}
