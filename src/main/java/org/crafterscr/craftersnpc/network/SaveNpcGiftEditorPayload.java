package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.gift.NpcGiftData;
import org.crafterscr.craftersnpc.gift.NpcGiftMessages;
import org.crafterscr.craftersnpc.gift.NpcGiftReward;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SaveNpcGiftEditorPayload(int entityId, boolean enabled, List<String> favoriteItems, List<String> likedItems,
                                       List<NpcGiftReward> rewards, double rewardChance,
                                       int cooldownSeconds, boolean consumeFavorite, boolean consumeLiked,
                                       List<String> favoriteMessages, List<String> likedMessages, List<String> unknownMessages, List<String> cooldownMessages,
                                       List<String> rewardMessages, List<String> noRewardMessages) implements CustomPacketPayload {
    public static final Type<SaveNpcGiftEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "save_npc_gift_editor"));
    public static final StreamCodec<FriendlyByteBuf, SaveNpcGiftEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeBoolean(payload.enabled());
                writeStrings(buf, payload.favoriteItems());
                writeStrings(buf, payload.likedItems());
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
                writeStrings(buf, payload.favoriteMessages());
                writeStrings(buf, payload.likedMessages());
                writeStrings(buf, payload.unknownMessages());
                writeStrings(buf, payload.cooldownMessages());
                writeStrings(buf, payload.rewardMessages());
                writeStrings(buf, payload.noRewardMessages());
            },
            buf -> new SaveNpcGiftEditorPayload(buf.readInt(), buf.readBoolean(), readStrings(buf, 128), readStrings(buf, 128), readRewards(buf),
                    buf.readDouble(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), readStrings(buf, 256), readStrings(buf, 256),
                    readStrings(buf, 256), readStrings(buf, 256), readStrings(buf, 256), readStrings(buf, 256)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveNpcGiftEditorPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> apply(payload, player));
    }

    private static void apply(SaveNpcGiftEditorPayload payload, ServerPlayer player) {
        if (!player.hasPermissions(2) || !(player.level() instanceof ServerLevel level)) return;
        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof CnpcEntity npc) || npc.distanceToSqr(player) > 100.0D) {
            player.sendSystemMessage(Component.literal("NPC no válido o demasiado lejos."));
            return;
        }
        NpcGiftData data = npc.getGiftData();
        data.setEnabled(payload.enabled());
        data.favoriteItems().clear();
        data.likedItems().clear();
        addValidItems(data, NpcGiftData.Category.FAVORITE, payload.favoriteItems());
        addValidItems(data, NpcGiftData.Category.LIKED, payload.likedItems());
        data.rewardPool().clear();
        for (NpcGiftReward reward : payload.rewards()) {
            if (isValidItem(reward.item())) data.rewardPool().add(new NpcGiftReward(reward.item(), Math.max(1, reward.min()), Math.max(Math.max(1, reward.min()), reward.max()), Math.max(1, reward.weight())));
        }
        data.setRewardChance(payload.rewardChance());
        data.setCooldownSeconds(payload.cooldownSeconds());
        data.setConsumeFavorite(payload.consumeFavorite());
        data.setConsumeLiked(payload.consumeLiked());
        replaceMessages(data.messages(), "favorite", payload.favoriteMessages());
        replaceMessages(data.messages(), "liked", payload.likedMessages());
        replaceMessages(data.messages(), "unknown", payload.unknownMessages());
        replaceMessages(data.messages(), "cooldown", payload.cooldownMessages());
        replaceMessages(data.messages(), "reward", payload.rewardMessages());
        replaceMessages(data.messages(), "noReward", payload.noRewardMessages());
        npc.markGiftDataChanged();
        player.sendSystemMessage(Component.literal("Sistema de regalos actualizado para " + npc.getNpcId() + "."));
    }

    private static void addValidItems(NpcGiftData data, NpcGiftData.Category category, List<String> items) {
        for (String item : items) if (isValidItem(item)) data.addToCategory(category, item);
    }

    private static boolean isValidItem(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    private static void replaceMessages(NpcGiftMessages messages, String type, List<String> values) {
        messages.clear(type);
        for (String value : values) messages.add(type, value);
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

    private static List<NpcGiftReward> readRewards(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 256);
        List<NpcGiftReward> rewards = new ArrayList<>(size);
        for (int index = 0; index < size; index++) rewards.add(new NpcGiftReward(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        return rewards;
    }
}
