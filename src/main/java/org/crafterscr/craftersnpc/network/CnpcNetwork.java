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
        registrar.playToServer(SaveNpcEditorPayload.TYPE, SaveNpcEditorPayload.STREAM_CODEC, SaveNpcEditorPayload::handle);
        registrar.playToServer(SaveNpcDialoguePayload.TYPE, SaveNpcDialoguePayload.STREAM_CODEC, SaveNpcDialoguePayload::handle);
        registrar.playToServer(SaveNpcGiftEditorPayload.TYPE, SaveNpcGiftEditorPayload.STREAM_CODEC, SaveNpcGiftEditorPayload::handle);
        registrar.playToClient(OpenNpcEditorPayload.TYPE, OpenNpcEditorPayload.STREAM_CODEC, OpenNpcEditorPayload::handle);
        registrar.playToClient(OpenNpcDialogueEditorPayload.TYPE, OpenNpcDialogueEditorPayload.STREAM_CODEC, OpenNpcDialogueEditorPayload::handle);
        registrar.playToClient(OpenNpcGiftEditorPayload.TYPE, OpenNpcGiftEditorPayload.STREAM_CODEC, OpenNpcGiftEditorPayload::handle);
    }
}
