package org.crafterscr.craftersnpc.entity.ai;

import org.crafterscr.craftersnpc.dialogue.DialogueContext;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcPlayerMemory;
import org.crafterscr.craftersnpc.network.ReputationIndicatorPayload;
import org.crafterscr.craftersnpc.reputation.NpcReputation;
import org.crafterscr.craftersnpc.reputation.ReputationReason;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

public class NpcSocialController {
    private final CnpcEntity npc;
    private final Map<UUID, NpcPlayerMemory> playerMemories = new HashMap<>();

    public NpcSocialController(CnpcEntity npc) {
        this.npc = npc;
    }

    public NpcPlayerMemory getOrCreatePlayerMemory(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        return playerMemories.computeIfAbsent(
                playerUuid,
                uuid -> new NpcPlayerMemory(uuid, player.getGameProfile().getName(), npc.level().getGameTime())
        );
    }

    public NpcPlayerMemory getPlayerMemory(UUID playerUuid) {
        return playerMemories.get(playerUuid);
    }

    public Collection<NpcPlayerMemory> playerMemories() {
        return playerMemories.values();
    }

    public DialogueContext createDialogueContext(NpcPlayerMemory memory) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (!memory.greeted()) {
            categories.add("saludo");
        }

        int reputation = memory.reputation();
        String category;
        if (NpcReputation.isFriendly(reputation)) {
            category = NpcReputation.CATEGORY_FRIENDLY;
        } else if (NpcReputation.isHostile(reputation)) {
            category = NpcReputation.CATEGORY_HOSTILE;
            categories.add(NpcReputation.CATEGORY_FORGIVENESS);
        } else if (reputation <= NpcReputation.ANNOYED_THRESHOLD) {
            category = NpcReputation.CATEGORY_ANNOYED;
        } else {
            category = NpcReputation.CATEGORY_NEUTRAL;
        }
        categories.add(category);
        categories.add(DialogueEntry.GENERIC_CATEGORY);

        return new DialogueContext(
                category,
                memory.playerUuid(),
                reputation,
                memory.greeted(),
                memory.interactionCount(),
                categories
        );
    }

    public void maybeRewardDialogueReputation(ServerPlayer player, NpcPlayerMemory memory) {
        long gameTime = npc.level().getGameTime();
        if (gameTime - memory.lastDialogueReputationGameTime() < NpcReputation.DIALOGUE_REWARD_COOLDOWN_TICKS) {
            return;
        }
        adjustReputation(player, NpcReputation.DIALOGUE_REWARD, ReputationReason.DIALOGUE);
        memory.markDialogueReputationRewarded(gameTime);
    }

    public int getReputation(ServerPlayer player) {
        return getOrCreatePlayerMemory(player).reputation();
    }

    public int adjustReputation(ServerPlayer player, int delta, ReputationReason reason) {
        NpcPlayerMemory memory = getOrCreatePlayerMemory(player);
        int previousReputation = memory.reputation();
        int newReputation = memory.adjustReputation(delta);
        int appliedDelta = newReputation - previousReputation;
        if (appliedDelta != 0) {
            sendReputationIndicator(player, appliedDelta, newReputation);
        }
        return newReputation;
    }

    private void sendReputationIndicator(ServerPlayer player, int appliedDelta, int currentReputation) {
        PacketDistributor.sendToPlayer(
                player,
                new ReputationIndicatorPayload(npc.getId(), appliedDelta, currentReputation, CnpcEntity.REPUTATION_INDICATOR_DURATION_TICKS)
        );
    }

    public void savePlayerMemories(ListTag tag) {
        for (NpcPlayerMemory memory : playerMemories.values()) {
            tag.add(memory.save());
        }
    }

    public void loadPlayerMemories(ListTag tag) {
        playerMemories.clear();
        for (Tag value : tag) {
            NpcPlayerMemory.load((CompoundTag) value).ifPresent(memory -> playerMemories.put(memory.playerUuid(), memory));
        }
    }
}
