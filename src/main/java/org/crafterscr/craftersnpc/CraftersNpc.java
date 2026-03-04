package org.crafterscr.craftersnpc;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(CraftersNpc.MODID)
public class CraftersNpc {
    public static final String MODID = "craftersnpc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftersNpc(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModEntities.registerAttributes(modEventBus);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> CraftersNpcClient.register(modEventBus));
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CnpcCommands.register(event.getDispatcher());
    }
}
