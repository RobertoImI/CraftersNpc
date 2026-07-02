package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.dialogue.DialogueBankStorage;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SaveNpcDialoguePayload(int entityId, String bankId, List<DialogueEntry> entries, Map<String, List<DialogueEntry>> bankEntries) implements CustomPacketPayload {
    public static final Type<SaveNpcDialoguePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "save_npc_dialogue"));
    public static final StreamCodec<FriendlyByteBuf, SaveNpcDialoguePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.bankId(), 64);
                writeEntries(buf, payload.entries(), CnpcEntity.MAX_DIALOGUE_PHRASES);
                buf.writeVarInt(Math.min(payload.bankEntries().size(), 512));
                payload.bankEntries().entrySet().stream().limit(512).forEach(entry -> {
                    buf.writeUtf(entry.getKey(), 64);
                    writeEntries(buf, entry.getValue(), DialogueBankStorage.MAX_BANK_PHRASES);
                });
            },
            buf -> {
                int entityId = buf.readInt();
                String bankId = buf.readUtf(64);
                List<DialogueEntry> entries = readEntries(buf, CnpcEntity.MAX_DIALOGUE_PHRASES);
                int bankSize = Math.min(buf.readVarInt(), 512);
                Map<String, List<DialogueEntry>> bankEntries = new HashMap<>();
                for (int index = 0; index < bankSize; index++) {
                    bankEntries.put(DialogueBankStorage.normalizeBankId(buf.readUtf(64)), readEntries(buf, DialogueBankStorage.MAX_BANK_PHRASES));
                }
                return new SaveNpcDialoguePayload(entityId, bankId, entries, bankEntries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void writeEntries(FriendlyByteBuf buf, List<DialogueEntry> entries, int limit) {
        buf.writeVarInt(Math.min(entries.size(), limit));
        for (DialogueEntry entry : entries.subList(0, Math.min(entries.size(), limit))) {
            writeEntry(buf, entry);
        }
    }

    static List<DialogueEntry> readEntries(FriendlyByteBuf buf, int limit) {
        int size = Math.min(buf.readVarInt(), limit);
        List<DialogueEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(readEntry(buf));
        }
        return entries;
    }

    static void writeEntry(FriendlyByteBuf buf, DialogueEntry entry) {
        buf.writeUtf(entry.text(), CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH);
    }

    static DialogueEntry readEntry(FriendlyByteBuf buf) {
        return new DialogueEntry(buf.readUtf(CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH));
    }

    public static void handle(SaveNpcDialoguePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            Entity entity = player.serverLevel().getEntity(payload.entityId());
            if (entity instanceof CnpcEntity npc && player.distanceToSqr(npc) <= 64.0D) {
                npc.setDialogueBankId(payload.bankId());
                npc.replaceDialogueEntries(payload.entries());
                DialogueBankStorage storage = DialogueBankStorage.get(player.serverLevel());
                payload.bankEntries().forEach(storage::saveBank);
            }
        });
    }
}
