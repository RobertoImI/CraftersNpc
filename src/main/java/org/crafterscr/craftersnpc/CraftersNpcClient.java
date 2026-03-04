package org.crafterscr.craftersnpc;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = CraftersNpc.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CraftersNpcClient {
    private CraftersNpcClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CNPC.get(), CnpcRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
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
