package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.client.gui.NpcDialogueEditorScreen;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenNpcDialogueEditorPayload(int entityId, String npcId, List<DialogueEntry> entries, List<PlayerReputation> reputations) implements CustomPacketPayload {
    public static final Type<OpenNpcDialogueEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "open_npc_dialogue_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcDialogueEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.npcId(), 64);
                buf.writeVarInt(Math.min(payload.entries().size(), CnpcEntity.MAX_DIALOGUE_PHRASES));
                for (DialogueEntry entry : payload.entries().subList(0, Math.min(payload.entries().size(), CnpcEntity.MAX_DIALOGUE_PHRASES))) {
                    SaveNpcDialoguePayload.writeEntry(buf, entry);
                }
                buf.writeVarInt(payload.reputations().size());
                for (PlayerReputation reputation : payload.reputations()) {
                    buf.writeUtf(reputation.playerName(), 64);
                    buf.writeInt(reputation.reputation());
                    buf.writeVarInt(reputation.interactions());
                    buf.writeInt(reputation.lastPhraseIndex());
                }
            },
            buf -> {
                int entityId = buf.readInt();
                String npcId = buf.readUtf(64);
                int entrySize = Math.min(buf.readVarInt(), CnpcEntity.MAX_DIALOGUE_PHRASES);
                List<DialogueEntry> entries = new ArrayList<>(entrySize);
                for (int index = 0; index < entrySize; index++) entries.add(SaveNpcDialoguePayload.readEntry(buf));
                int reputationSize = Math.min(buf.readVarInt(), 512);
                List<PlayerReputation> reputations = new ArrayList<>(reputationSize);
                for (int index = 0; index < reputationSize; index++) reputations.add(new PlayerReputation(buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readInt()));
                return new OpenNpcDialogueEditorPayload(entityId, npcId, entries, reputations);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenNpcDialogueEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcDialogueEditorScreen(payload.entityId(), payload.npcId(), payload.entries(), payload.reputations())));
    }

    public record PlayerReputation(String playerName, int reputation, int interactions, int lastPhraseIndex) {}
}
