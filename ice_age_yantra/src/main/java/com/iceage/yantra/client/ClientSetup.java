package com.iceage.yantra.client;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.client.gui.CelebrationOverlay;
import com.iceage.yantra.client.gui.IcePointsOverlay;
import com.iceage.yantra.client.gui.RemoteHudOverlay;
import com.iceage.yantra.client.particle.FrostParticles;
import com.iceage.yantra.client.render.ChillVillagerRenderer;
import com.iceage.yantra.client.render.IceCreamTruckRenderer;
import com.iceage.yantra.client.render.IcePortalRenderer;
import com.iceage.yantra.client.render.ObjParts;
import com.iceage.yantra.client.render.ShowroomRenderers;
import com.iceage.yantra.client.render.YantraRenderers;
import com.iceage.yantra.registry.ModBlockEntities;
import com.iceage.yantra.registry.ModEntities;
import com.iceage.yantra.registry.ModParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IceAgeYantra.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ModBlockEntities.GOLA.get(), YantraRenderers.Gola::new);
        e.registerBlockEntityRenderer(ModBlockEntities.FAN.get(), YantraRenderers.Fan::new);
        e.registerBlockEntityRenderer(ModBlockEntities.FRIDGE.get(), YantraRenderers.Fridge::new);
        e.registerBlockEntityRenderer(ModBlockEntities.AC.get(), YantraRenderers.Ac::new);
        e.registerBlockEntityRenderer(ModBlockEntities.PORTAL.get(), IcePortalRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.PEDESTAL.get(), ShowroomRenderers.Pedestal::new);
        e.registerBlockEntityRenderer(ModBlockEntities.SHOP_SIGN.get(), ShowroomRenderers.Sign::new);
        e.registerEntityRenderer(ModEntities.ICE_CREAM_TRUCK.get(), IceCreamTruckRenderer::new);
        e.registerEntityRenderer(ModEntities.CHILL_VILLAGER.get(), ChillVillagerRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        for (ResourceLocation rl : ObjParts.ALL) e.register(rl);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("ice_points", IcePointsOverlay::render);
        e.registerAboveAll("remote_lcd", RemoteHudOverlay::render);
        e.registerAboveAll("celebration", CelebrationOverlay::render);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.FROST_SWIRL.get(), s -> new FrostParticles.Provider(s, FrostParticles.Kind.SWIRL));
        e.registerSpriteSet(ModParticles.ICE_SHARD.get(), s -> new FrostParticles.Provider(s, FrostParticles.Kind.SHARD));
        e.registerSpriteSet(ModParticles.SNOWFLAKE.get(), s -> new FrostParticles.Provider(s, FrostParticles.Kind.SNOW));
        e.registerSpriteSet(ModParticles.FROST_MIST.get(), s -> new FrostParticles.Provider(s, FrostParticles.Kind.MIST));
        e.registerSpriteSet(ModParticles.SPARKLE.get(), s -> new FrostParticles.Provider(s, FrostParticles.Kind.SPARKLE));
    }
}
