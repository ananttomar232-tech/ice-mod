package com.iceage.yantra.command;

import com.iceage.yantra.block.IcePortalBlock;
import com.iceage.yantra.event.CommonEvents;
import com.iceage.yantra.network.CelebratePacket;
import com.iceage.yantra.network.ModNetwork;
import com.iceage.yantra.progress.IceProgress;
import com.iceage.yantra.progress.PacingEngine;
import com.iceage.yantra.registry.ModBlocks;
import com.iceage.yantra.registry.ModItems;
import com.iceage.yantra.shop.ShowroomBuilder;
import com.iceage.yantra.shop.ShowroomDimension;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

/** /iceage setup | points set/add | reset | shop | showroom | rebuildshowroom */
public final class IceAgeCommand {
    private IceAgeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("iceage")
                .then(Commands.literal("setup").requires(s -> s.hasPermission(2)).executes(IceAgeCommand::setup))
                .then(Commands.literal("shop").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CelebratePacket(CelebratePacket.OPEN_SHOP, 0));
                    return 1;
                }))
                .then(Commands.literal("showroom").executes(c -> {
                    ShowroomDimension.toggle(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("rebuildshowroom").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerLevel shop = c.getSource().getServer().getLevel(ShowroomDimension.KEY);
                    if (shop != null) ShowroomBuilder.rebuild(shop);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))))
                .then(Commands.literal("counter").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reset").executes(c -> counterSet(c, 0L)))
                        .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0, 10_000_000L))
                                .executes(c -> counterSet(c, LongArgumentType.getLong(c, "amount")))))
                        .then(Commands.literal("speed").then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.1f, 1000f))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    PacingEngine.setSpeed(p.server, p.getUUID(), FloatArgumentType.getFloat(c, "multiplier"));
                                    PacingEngine.sync(p);
                                    return 1;
                                }))))
                .then(Commands.literal("gotiya").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("tier_number", IntegerArgumentType.integer(1, 5)).executes(IceAgeCommand::gotiya)))
                .then(Commands.literal("points").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0, 10_000_000L))
                                .executes(c -> points(c, List.of(c.getSource().getPlayerOrException()), true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> points(c, EntityArgument.getPlayers(c, "targets"), true)))))
                        .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg(0, 10_000_000L))
                                .executes(c -> points(c, List.of(c.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> points(c, EntityArgument.getPlayers(c, "targets"), false))))))
        );
    }

    private static int counterSet(CommandContext<CommandSourceStack> c, long value) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PacingEngine.set(p.server, p.getUUID(), value);
        PacingEngine.sync(p);
        return 1;
    }

    /** /iceage gotiya <tier>: makes Gotiya show up at the nearest Yantra of that tier (5 = hairdryer finale). */
    private static int gotiya(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int tier = IntegerArgumentType.getInteger(c, "tier_number");
        ServerLevel level = p.serverLevel();
        if (tier == 5) {
            com.iceage.yantra.entity.ChillVillagerEntity.spawnHairdryerFinale(p);
            return 1;
        }
        com.iceage.yantra.block.YantraBlockEntity best = null;
        double bestD = 96 * 96;
        BlockPos at = p.blockPosition();
        for (int cx = (at.getX() >> 4) - 6; cx <= (at.getX() >> 4) + 6; cx++) {
            for (int cz = (at.getZ() >> 4) - 6; cz <= (at.getZ() >> 4) + 6; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof com.iceage.yantra.block.YantraBlockEntity y && y.tier().number == tier) {
                        double d = be.getBlockPos().distSqr(at);
                        if (d < bestD) {
                            bestD = d;
                            best = y;
                        }
                    }
                }
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("Place a Tier " + tier + " Yantra nearby first."));
            return 0;
        }
        com.iceage.yantra.entity.ChillVillagerEntity v;
        if (best instanceof com.iceage.yantra.block.GolaMachineBlockEntity g) {
            v = com.iceage.yantra.entity.ChillVillagerEntity.forGola(level, g);
        } else if (best instanceof com.iceage.yantra.block.RetroFridgeBlockEntity f) {
            v = f.summonVillager(level, p);
        } else {
            v = com.iceage.yantra.entity.ChillVillagerEntity.forFront(level, best);
        }
        return v != null ? 1 : 0;
    }

    private static int points(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets, boolean set) {
        long amount = LongArgumentType.getLong(c, "amount");
        for (ServerPlayer p : targets) {
            if (set) PacingEngine.set(p.server, p.getUUID(), amount);
            else PacingEngine.award(p.server, p.getUUID(), amount);
            PacingEngine.sync(p);
        }
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) {
            IceProgress data = IceProgress.get(p.server);
            IceProgress.Entry e = data.entry(p.getUUID());
            e.points = 0;
            e.purchasedMask = 0;
            e.climaxDone = false;
            data.setDirty();
            PacingEngine.sync(p);
        }
        return targets.size();
    }

    private static int setup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition();
        int r = 20;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = base.getX() + dx, z = base.getZ() + dz;
                for (int y = base.getY(); y <= base.getY() + 28; y++) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, air, flags);
                }
                level.setBlock(m.set(x, base.getY() - 1, z), grass, flags);
                for (int y = base.getY() - 4; y <= base.getY() - 2; y++) level.setBlock(m.set(x, y, z), dirt, flags);
            }
        }
        CommonEvents.givePortal(p, true);
        give(p, new ItemStack(ModItems.CHILL_VILLAGER_SPAWN_EGG.get(), 4));
        give(p, new ItemStack(ModItems.ICE_CREAM_VAN_SPAWN_EGG.get(), 2));
        give(p, new ItemStack(ModItems.AC_REMOTE.get()));
        // a portal to the Frozen Showroom right behind the player
        Direction back = p.getDirection().getOpposite();
        BlockPos portal = base.relative(back, 5);
        BlockState lower = ModBlocks.ICE_PORTAL.get().defaultBlockState().setValue(IcePortalBlock.FACING, p.getDirection())
                .setValue(IcePortalBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(portal, lower, Block.UPDATE_ALL);
        level.setBlock(portal.above(), lower.setValue(IcePortalBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        PacingEngine.sync(p);
        return 1;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }
}
