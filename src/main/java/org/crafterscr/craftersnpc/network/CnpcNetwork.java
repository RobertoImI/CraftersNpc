package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.client.ClientPayloadHandlers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPayloads(registrar);
        } else {
            registerServerClientboundPayloads(registrar);
        }
    }

    private static void registerClientPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(OpenNpcEditorPayload.TYPE, OpenNpcEditorPayload.STREAM_CODEC, ClientPayloadHandlers::openNpcEditor);
        registrar.playToClient(OpenNpcDialogueEditorPayload.TYPE, OpenNpcDialogueEditorPayload.STREAM_CODEC, ClientPayloadHandlers::openNpcDialogueEditor);
        registrar.playToClient(OpenNpcGiftEditorPayload.TYPE, OpenNpcGiftEditorPayload.STREAM_CODEC, ClientPayloadHandlers::openNpcGiftEditor);
    }

    private static void registerServerClientboundPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(OpenNpcEditorPayload.TYPE, OpenNpcEditorPayload.STREAM_CODEC, (payload, context) -> { });
        registrar.playToClient(OpenNpcDialogueEditorPayload.TYPE, OpenNpcDialogueEditorPayload.STREAM_CODEC, (payload, context) -> { });
        registrar.playToClient(OpenNpcGiftEditorPayload.TYPE, OpenNpcGiftEditorPayload.STREAM_CODEC, (payload, context) -> { });
    }
}
