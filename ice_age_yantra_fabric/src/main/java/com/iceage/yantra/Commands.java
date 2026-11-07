package com.iceage.yantra;

import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.entity.GotiyaEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * /iceage portal | shop | setup | rebuildshop | counter reset|set|speed | gotiya.
 * Commands never print chat messages (the screen stays clean).
 */
public final class Commands {
    private Commands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        d.register(CommandManager.literal("iceage")
                .then(CommandManager.literal("portal").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    placePortal(c.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("shop").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ShopBuilder.ensureBuilt(p.server);
                    ShopPortals.teleport(p, p.server.getOverworld(), ShopBuilder.ARRIVAL, 0f, 5f);
                    return 1;
                }))
                .then(CommandManager.literal("rebuildshop").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ShopBuilder.rebuild(c.getSource().getServer());
                    return 1;
                }))
                .then(CommandManager.literal("setup").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    give(p, new ItemStack(ModItems.VILLAGE_PORTAL));
                    give(p, new ItemStack(ModItems.GOLA_MACHINE));
                    give(p, new ItemStack(ModItems.FARRATA_FAN));
                    give(p, new ItemStack(ModItems.RETRO_FRIDGE));
                    give(p, new ItemStack(ModItems.AC_UNIT));
                    give(p, new ItemStack(ModItems.ICE_CREAM_VAN));
                    return 1;
                }))
                .then(CommandManager.literal("counter").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("reset").executes(c -> setCounter(c.getSource().getPlayerOrThrow(), 0L)))
                        .then(CommandManager.literal("set").then(CommandManager.argument("amount", LongArgumentType.longArg(0, Tier.FINAL_TARGET))
                                .executes(c -> setCounter(c.getSource().getPlayerOrThrow(), LongArgumentType.getLong(c, "amount")))))
                        .then(CommandManager.literal("speed").then(CommandManager.argument("multiplier", FloatArgumentType.floatArg(0.1f, 1000f))
                                .executes(c -> {
                                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                                    IcePoints.setSpeed(p.server, p.getUuid(), FloatArgumentType.getFloat(c, "multiplier"));
                                    return 1;
                                }))))
                .then(CommandManager.literal("gotiya").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
                    Vec3d at = p.getPos().add(look.multiply(16));
                    GotiyaEntity.spawnClimax((ServerWorld) p.getWorld(), at, p);
                    return 1;
                })));
    }

    private static int setCounter(ServerPlayerEntity p, long v) {
        IcePoints.set(p.server, p.getUuid(), v);
        return 1;
    }

    private static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    /** Places a Village Portal three blocks in front of the player, facing them. */
    private static void placePortal(ServerPlayerEntity p) throws CommandSyntaxException {
        ServerWorld w = (ServerWorld) p.getWorld();
        Direction look = p.getHorizontalFacing();
        BlockPos at = p.getBlockPos().offset(look, 3);
        Vec3d ground = ShopPortals.groundAt(w, new Vec3d(at.getX() + 0.5, p.getY(), at.getZ() + 0.5));
        BlockPos base = BlockPos.ofFloored(ground);
        BlockState lower = ModBlocks.VILLAGE_PORTAL.getDefaultState().with(PortalBlock.FACING, look.getOpposite()).with(PortalBlock.HALF, DoubleBlockHalf.LOWER);
        w.setBlockState(base, lower, Block.NOTIFY_ALL);
        w.setBlockState(base.up(), lower.with(PortalBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }
}
