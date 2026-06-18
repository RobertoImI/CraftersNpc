package org.crafterscr.craftersnpc;

import org.crafterscr.craftersnpc.client.CraftersNpcClient;
import org.crafterscr.craftersnpc.commands.CnpcCommands;
import org.crafterscr.craftersnpc.entity.ModEntities;
import org.crafterscr.craftersnpc.network.CnpcNetwork;
import org.crafterscr.craftersnpc.route.RouteWandManager;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(CraftersNpc.MODID)
public class CraftersNpc {
    public static final String MODID = "craftersnpc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftersNpc(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModEntities.registerAttributes(modEventBus);
        CnpcNetwork.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CraftersNpcClient.register(modEventBus);
        }
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RouteWandManager::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(RouteWandManager::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CnpcCommands.register(event.getDispatcher());
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RouteWandManager.clearPlayerState(event.getEntity());
    }
}
