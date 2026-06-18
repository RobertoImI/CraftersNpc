package org.crafterscr.craftersnpc.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CnpcNetwork {
    private CnpcNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CnpcNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(WandWaitAdjustPayload.TYPE, WandWaitAdjustPayload.STREAM_CODEC, WandWaitAdjustPayload::handle);
    }
}
