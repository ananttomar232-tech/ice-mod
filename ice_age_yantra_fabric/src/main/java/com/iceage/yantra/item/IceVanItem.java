package com.iceage.yantra.item;

import com.iceage.yantra.ModEntities;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Place the Musical Ice Cream Van (a real custom vehicle entity). */
public class IceVanItem extends Item {
    public IceVanItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        IceVanEntity van = ModEntities.ICE_VAN.create((ServerWorld) world);
        if (van == null) return ActionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0f;
        van.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        if (!world.isSpaceEmpty(van)) return ActionResult.FAIL;
        if (ctx.getPlayer() != null) van.setOwner(ctx.getPlayer().getUuid());
        world.spawnEntity(van);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) ctx.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
