package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.entity.ModEntities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.crafterscr.craftersnpc.client.CnpcRenderer;

import java.io.IOException;

public final class CraftersNpcClient {
    private CraftersNpcClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CraftersNpcClient::registerRenderers);
        modEventBus.addListener(CraftersNpcClient::onClientSetup);
        RouteWandKeybinds.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(RouteWandKeybinds::onClientTick);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CNPC.get(), CnpcRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                var folder = SkinTextureManager.ensureFolder();
                CraftersNpc.LOGGER.info("Carpeta de skins CNPC lista en {}", folder);
            } catch (IOException e) {
                CraftersNpc.LOGGER.error("No se pudo crear la carpeta de skins de CNPC", e);
            }
        });
    }
}
