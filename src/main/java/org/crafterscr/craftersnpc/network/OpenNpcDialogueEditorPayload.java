package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.client.gui.NpcDialogueEditorScreen;
import org.crafterscr.craftersnpc.dialogue.DialogueBankStorage;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record OpenNpcDialogueEditorPayload(int entityId, String npcId, String bankId, List<DialogueEntry> entries, List<String> bankIds, Map<String, List<DialogueEntry>> bankEntries) implements CustomPacketPayload {
    public static final Type<OpenNpcDialogueEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "open_npc_dialogue_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcDialogueEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.npcId(), 64);
                buf.writeUtf(payload.bankId(), 64);
                SaveNpcDialoguePayload.writeEntries(buf, payload.entries(), CnpcEntity.MAX_DIALOGUE_PHRASES);
                buf.writeVarInt(Math.min(payload.bankIds().size(), 512));
                for (String bankId : payload.bankIds().subList(0, Math.min(payload.bankIds().size(), 512))) {
                    buf.writeUtf(bankId, 64);
                    SaveNpcDialoguePayload.writeEntries(buf, payload.bankEntries().getOrDefault(bankId, List.of()), DialogueBankStorage.MAX_BANK_PHRASES);
                }
            },
            buf -> {
                int entityId = buf.readInt();
                String npcId = buf.readUtf(64);
                String bankId = buf.readUtf(64);
                List<DialogueEntry> entries = SaveNpcDialoguePayload.readEntries(buf, CnpcEntity.MAX_DIALOGUE_PHRASES);
                int bankSize = Math.min(buf.readVarInt(), 512);
                List<String> bankIds = new ArrayList<>(bankSize);
                Map<String, List<DialogueEntry>> bankEntries = new HashMap<>();
                for (int index = 0; index < bankSize; index++) {
                    String id = buf.readUtf(64);
                    bankIds.add(id);
                    bankEntries.put(id, SaveNpcDialoguePayload.readEntries(buf, DialogueBankStorage.MAX_BANK_PHRASES));
                }
                return new OpenNpcDialogueEditorPayload(entityId, npcId, bankId, entries, bankIds, bankEntries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenNpcDialogueEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcDialogueEditorScreen(payload.entityId(), payload.npcId(), payload.bankId(), payload.entries(), payload.bankIds(), payload.bankEntries())));
    }
}
