package com.iceage.yantra.event;

import com.iceage.yantra.Config;
import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.RetroFridgeBlock;
import com.iceage.yantra.block.RetroFridgeBlockEntity;
import com.iceage.yantra.command.IceAgeCommand;
import com.iceage.yantra.progress.IceProgress;
import com.iceage.yantra.progress.PacingEngine;
import com.iceage.yantra.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IceAgeYantra.MODID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        IceAgeCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (Config.GIVE_PORTAL_ON_JOIN.get()) givePortal(p, false);
            PacingEngine.sync(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PacingEngine.sync(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PacingEngine.sync(p);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer() != null) PacingEngine.flush(e.getServer());
    }

    /** Left-clicking a fridge with a villager inside scares him instead of mining (also in creative). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!(s.getBlock() instanceof RetroFridgeBlock fridgeBlock)) return;
        BlockPos ctrl = fridgeBlock.controllerPos(s, e.getPos());
        if (!(e.getLevel().getBlockEntity(ctrl) instanceof RetroFridgeBlockEntity fridge) || !fridge.villagerInside) return;
        e.setCanceled(true);
        if (!e.getLevel().isClientSide) fridge.scareVillager(e.getEntity());
    }

    /** Puts the Yantra Shop Portal into hotbar slot 0 (once per player unless forced). */
    public static void givePortal(ServerPlayer p, boolean force) {
        IceProgress data = IceProgress.get(p.server);
        IceProgress.Entry entry = data.entry(p.getUUID());
        Inventory inv = p.getInventory();
        boolean has = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.YANTRA_SHOP_PORTAL.get())) {
                has = true;
                if (i != 0 && force) {
                    ItemStack portal = inv.getItem(i);
                    ItemStack zero = inv.getItem(0);
                    inv.setItem(0, portal);
                    inv.setItem(i, zero);
                }
                break;
            }
        }
        if ((entry.portalGiven && !force) || has) {
            entry.portalGiven = true;
            data.setDirty();
            return;
        }
        ItemStack old = inv.getItem(0);
        inv.setItem(0, new ItemStack(ModItems.YANTRA_SHOP_PORTAL.get()));
        if (!old.isEmpty() && !inv.add(old)) p.drop(old, false);
        inv.selected = 0;
        entry.portalGiven = true;
        data.setDirty();
        p.sendSystemMessage(Component.translatable("msg.ice_age_yantra.welcome").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }
}
