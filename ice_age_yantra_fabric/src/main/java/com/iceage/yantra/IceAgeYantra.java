package com.iceage.yantra;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Ice Age Yantra (Fabric). No mixins: fully compatible with Sodium, Lithium, Iris and Replay Mod. */
public class IceAgeYantra implements ModInitializer {
    public static final String MODID = "ice_age_yantra";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
        CommandRegistrationCallback.EVENT.register(Commands::register);
        ServerTickEvents.END_SERVER_TICK.register(IcePoints::flush);
        // build the secret overworld shop once (it is rebuilt automatically if the world was edited)
        ServerLifecycleEvents.SERVER_STARTED.register(ShopBuilder::ensureBuilt);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            IceData data = IceData.get(server);
            IceData.Entry e = data.entry(p.getUuid());
            if (!e.gotStarter) {
                e.gotStarter = true;
                data.markDirty();
                ItemStack portal = new ItemStack(ModItems.VILLAGE_PORTAL);
                if (!p.getInventory().insertStack(portal)) p.dropItem(portal, false);
            }
            IcePoints.send(p, 0L);
        });
    }
}
