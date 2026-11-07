package com.iceage.yantra.client;

import com.iceage.yantra.ModBlocks;
import com.iceage.yantra.ModEntities;
import com.iceage.yantra.Net;
import com.iceage.yantra.client.obj.ObjParts;
import com.iceage.yantra.client.render.GotiyaRenderer;
import com.iceage.yantra.client.render.ItemRigs;
import com.iceage.yantra.client.render.PortalRenderer;
import com.iceage.yantra.client.render.ShowcaseRenderer;
import com.iceage.yantra.client.render.VanRenderer;
import com.iceage.yantra.client.render.YantraRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.iceage.yantra.ModParticles;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Client side: HUD counter, OBJ rig renderers, entity renderers. Pure Fabric API, no mixins. */
public class IceAgeYantraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlocks.YANTRA_BE, YantraRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.PORTAL_BE, PortalRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.SHOWCASE_BE, ShowcaseRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_VAN, VanRenderer::new);
        EntityRendererRegistry.register(ModEntities.GOTIYA, GotiyaRenderer::new);
        ItemRigs.register();
        VanCamera.register();
        ParticleFactoryRegistry pf = ParticleFactoryRegistry.getInstance();
        pf.register(ModParticles.FROST_SWIRL, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SWIRL));
        pf.register(ModParticles.ICE_SHARD, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SHARD));
        pf.register(ModParticles.SNOWFLAKE, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SNOW));
        pf.register(ModParticles.FROST_MIST, s -> new FrostParticles.Provider(s, FrostParticles.Kind.MIST));
        pf.register(ModParticles.SPARKLE, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SPARKLE));
        com.iceage.yantra.entity.IceVanEntity.jingleHook = VanJingle::ensure;

        // re-parse the OBJ rigs whenever resources reload
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier("ice_age_yantra", "obj_rigs");
            }

            @Override
            public void reload(ResourceManager manager) {
                ObjParts.clearAll();
            }
        });

        HudRenderCallback.EVENT.register(IceHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(c -> ClientIceState.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientIceState.reset());
        ClientPlayNetworking.registerGlobalReceiver(Net.POINTS, (client, handler, buf, sender) -> {
            long points = buf.readLong();
            float speed = buf.readFloat();
            long gained = buf.readLong();
            client.execute(() -> ClientIceState.update(points, speed, gained));
        });
    }
}
