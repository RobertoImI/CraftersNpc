package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.client.gui.NpcGiftEditorScreen;
import org.crafterscr.craftersnpc.gift.NpcGiftReward;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenNpcGiftEditorPayload(int entityId, String npcId, boolean enabled, List<String> favoriteItems,
                                       List<String> likedItems, List<String> dislikedItems, List<NpcGiftReward> rewards,
                                       double rewardChance, int cooldownSeconds, boolean consumeFavorite,
                                       boolean consumeLiked, boolean consumeDisliked, List<String> favoriteMessages,
                                       List<String> likedMessages, List<String> dislikedMessages,
                                       List<String> unknownMessages, List<String> cooldownMessages,
                                       List<String> rewardMessages, List<String> noRewardMessages) implements CustomPacketPayload {
    public static final Type<OpenNpcGiftEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "open_npc_gift_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcGiftEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.npcId(), 64);
                buf.writeBoolean(payload.enabled());
                writeStrings(buf, payload.favoriteItems());
                writeStrings(buf, payload.likedItems());
                writeStrings(buf, payload.dislikedItems());
                buf.writeVarInt(payload.rewards().size());
                for (NpcGiftReward reward : payload.rewards()) {
                    buf.writeUtf(reward.item(), 128);
                    buf.writeVarInt(reward.min());
                    buf.writeVarInt(reward.max());
                    buf.writeVarInt(reward.weight());
                }
                buf.writeDouble(payload.rewardChance());
                buf.writeVarInt(payload.cooldownSeconds());
                buf.writeBoolean(payload.consumeFavorite());
                buf.writeBoolean(payload.consumeLiked());
                buf.writeBoolean(payload.consumeDisliked());
                writeStrings(buf, payload.favoriteMessages());
                writeStrings(buf, payload.likedMessages());
                writeStrings(buf, payload.dislikedMessages());
                writeStrings(buf, payload.unknownMessages());
                writeStrings(buf, payload.cooldownMessages());
                writeStrings(buf, payload.rewardMessages());
                writeStrings(buf, payload.noRewardMessages());
            },
            buf -> {
                int entityId = buf.readInt();
                String npcId = buf.readUtf(64);
                boolean enabled = buf.readBoolean();
                List<String> favoriteItems = readStrings(buf, 128);
                List<String> likedItems = readStrings(buf, 128);
                List<String> dislikedItems = readStrings(buf, 128);
                int rewardCount = Math.min(buf.readVarInt(), 256);
                List<NpcGiftReward> rewards = new ArrayList<>(rewardCount);
                for (int index = 0; index < rewardCount; index++) {
                    rewards.add(new NpcGiftReward(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new OpenNpcGiftEditorPayload(entityId, npcId, enabled, favoriteItems, likedItems, dislikedItems, rewards,
                        buf.readDouble(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        readStrings(buf, 256), readStrings(buf, 256), readStrings(buf, 256), readStrings(buf, 256),
                        readStrings(buf, 256), readStrings(buf, 256), readStrings(buf, 256));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenNpcGiftEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcGiftEditorScreen(payload)));
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) buf.writeUtf(value, 256);
    }

    private static List<String> readStrings(FriendlyByteBuf buf, int maxLength) {
        int size = Math.min(buf.readVarInt(), 512);
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) values.add(buf.readUtf(maxLength));
        return values;
    }
}
