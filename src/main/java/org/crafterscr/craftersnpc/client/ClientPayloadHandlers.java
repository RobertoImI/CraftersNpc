package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.client.gui.NpcDialogueEditorScreen;
import org.crafterscr.craftersnpc.client.gui.NpcEditorScreen;
import org.crafterscr.craftersnpc.client.gui.NpcGiftEditorScreen;
import org.crafterscr.craftersnpc.network.OpenNpcDialogueEditorPayload;
import org.crafterscr.craftersnpc.network.OpenNpcEditorPayload;
import org.crafterscr.craftersnpc.network.OpenNpcGiftEditorPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void openNpcEditor(OpenNpcEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcEditorScreen(payload.entityId(), payload.npcId(), payload.skinId(),
                payload.slimModel(), payload.speed(), payload.temperament(), payload.routeId(), payload.nightMode(), payload.damageEnabled(), payload.skinIds(), payload.routeIds())));
    }

    public static void openNpcDialogueEditor(OpenNpcDialogueEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcDialogueEditorScreen(payload.entityId(), payload.npcId(), payload.bankId(),
                payload.entries(), payload.bankIds(), payload.bankEntries())));
    }

    public static void openNpcGiftEditor(OpenNpcGiftEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcGiftEditorScreen(payload)));
    }
}
