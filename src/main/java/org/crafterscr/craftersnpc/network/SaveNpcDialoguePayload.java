package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
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
import java.util.List;

public record SaveNpcDialoguePayload(int entityId, List<DialogueEntry> entries) implements CustomPacketPayload {
    public static final Type<SaveNpcDialoguePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "save_npc_dialogue"));
    public static final StreamCodec<FriendlyByteBuf, SaveNpcDialoguePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeVarInt(Math.min(payload.entries().size(), CnpcEntity.MAX_DIALOGUE_PHRASES));
                for (DialogueEntry entry : payload.entries().subList(0, Math.min(payload.entries().size(), CnpcEntity.MAX_DIALOGUE_PHRASES))) {
                    writeEntry(buf, entry);
                }
            },
            buf -> {
                int entityId = buf.readInt();
                int size = Math.min(buf.readVarInt(), CnpcEntity.MAX_DIALOGUE_PHRASES);
                List<DialogueEntry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(readEntry(buf));
                }
                return new SaveNpcDialoguePayload(entityId, entries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void writeEntry(FriendlyByteBuf buf, DialogueEntry entry) {
        buf.writeUtf(entry.text(), CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH);
        buf.writeUtf(entry.category(), 64);
        buf.writeVarInt(entry.weight());
        buf.writeInt(entry.minReputation());
        buf.writeInt(entry.maxReputation());
        buf.writeBoolean(entry.oncePerPlayer());
        buf.writeVarInt(entry.cooldownTicks());
        buf.writeInt(entry.priority());
    }

    static DialogueEntry readEntry(FriendlyByteBuf buf) {
        return new DialogueEntry(buf.readUtf(CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH), buf.readUtf(64), buf.readVarInt(),
                buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readVarInt(), buf.readInt());
    }

    public static void handle(SaveNpcDialoguePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            Entity entity = player.serverLevel().getEntity(payload.entityId());
            if (entity instanceof CnpcEntity npc && player.distanceToSqr(npc) <= 64.0D) {
                npc.replaceDialogueEntries(payload.entries());
            }
        });
    }
}
