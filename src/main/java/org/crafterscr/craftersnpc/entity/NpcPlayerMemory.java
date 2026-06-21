package org.crafterscr.craftersnpc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.crafterscr.craftersnpc.reputation.NpcReputation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent relationship state between one NPC and one player. */
public class NpcPlayerMemory {
    private final UUID playerUuid;
    private String lastKnownName;
    private boolean greeted;
    private final long firstSeenGameTime;
    private long lastInteractionGameTime;
    private int interactionCount;
    private int reputation;
    private long lastDialogueReputationGameTime;
    private final Map<Integer, Long> dialogueEntryUseGameTimes = new HashMap<>();

    public NpcPlayerMemory(UUID playerUuid, String lastKnownName, long firstSeenGameTime) {
        this(playerUuid, lastKnownName, false, firstSeenGameTime, firstSeenGameTime, 0, NpcReputation.NEUTRAL, Long.MIN_VALUE);
    }

    private NpcPlayerMemory(
            UUID playerUuid,
            String lastKnownName,
            boolean greeted,
            long firstSeenGameTime,
            long lastInteractionGameTime,
            int interactionCount,
            int reputation,
            long lastDialogueReputationGameTime
    ) {
        this.playerUuid = playerUuid;
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
        this.greeted = greeted;
        this.firstSeenGameTime = firstSeenGameTime;
        this.lastInteractionGameTime = lastInteractionGameTime;
        this.interactionCount = Math.max(0, interactionCount);
        this.reputation = NpcReputation.clamp(reputation);
        this.lastDialogueReputationGameTime = lastDialogueReputationGameTime;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public boolean greeted() {
        return greeted;
    }

    public long firstSeenGameTime() {
        return firstSeenGameTime;
    }

    public long lastInteractionGameTime() {
        return lastInteractionGameTime;
    }

    public int interactionCount() {
        return interactionCount;
    }

    public int reputation() {
        return reputation;
    }

    public long lastDialogueReputationGameTime() {
        return lastDialogueReputationGameTime;
    }

    public boolean hasUsedDialogueEntry(int index) {
        return dialogueEntryUseGameTimes.containsKey(index);
    }

    public long ticksSinceDialogueEntryUsed(int index, long currentGameTime) {
        Long lastUsed = dialogueEntryUseGameTimes.get(index);
        return lastUsed == null ? -1L : Math.max(0L, currentGameTime - lastUsed);
    }

    public void markDialogueEntryUsed(int index, long gameTime) {
        if (index >= 0) {
            dialogueEntryUseGameTimes.put(index, gameTime);
        }
    }

    public int adjustReputation(int delta) {
        reputation = NpcReputation.clamp(reputation + delta);
        return reputation;
    }

    public void markDialogueReputationRewarded(long gameTime) {
        lastDialogueReputationGameTime = gameTime;
    }

    public void recordInteraction(String playerName, long gameTime) {
        greeted = true;
        lastKnownName = playerName == null ? "" : playerName;
        lastInteractionGameTime = gameTime;
        interactionCount++;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlayerUuid", playerUuid);
        tag.putString("LastKnownName", lastKnownName);
        tag.putBoolean("Greeted", greeted);
        tag.putLong("FirstSeenGameTime", firstSeenGameTime);
        tag.putLong("LastInteractionGameTime", lastInteractionGameTime);
        tag.putInt("InteractionCount", interactionCount);
        tag.putInt("Reputation", reputation);
        tag.putLong("LastDialogueReputationGameTime", lastDialogueReputationGameTime);
        net.minecraft.nbt.ListTag usedDialogueTag = new net.minecraft.nbt.ListTag();
        for (Map.Entry<Integer, Long> entry : dialogueEntryUseGameTimes.entrySet()) {
            CompoundTag usedTag = new CompoundTag();
            usedTag.putInt("Index", entry.getKey());
            usedTag.putLong("GameTime", entry.getValue());
            usedDialogueTag.add(usedTag);
        }
        tag.put("UsedDialogueEntries", usedDialogueTag);
        return tag;
    }

    public static Optional<NpcPlayerMemory> load(CompoundTag tag) {
        if (!tag.hasUUID("PlayerUuid")) {
            return Optional.empty();
        }
        UUID playerUuid = tag.getUUID("PlayerUuid");
        String lastKnownName = tag.contains("LastKnownName", Tag.TAG_STRING) ? tag.getString("LastKnownName") : "";
        boolean greeted = tag.getBoolean("Greeted");
        long firstSeenGameTime = tag.getLong("FirstSeenGameTime");
        long lastInteractionGameTime = tag.getLong("LastInteractionGameTime");
        int interactionCount = tag.getInt("InteractionCount");
        int reputation = tag.contains("Reputation", Tag.TAG_INT) ? tag.getInt("Reputation") : NpcReputation.NEUTRAL;
        long lastDialogueReputationGameTime = tag.contains("LastDialogueReputationGameTime", Tag.TAG_LONG)
                ? tag.getLong("LastDialogueReputationGameTime")
                : Long.MIN_VALUE;
        NpcPlayerMemory memory = new NpcPlayerMemory(
                playerUuid,
                lastKnownName,
                greeted,
                firstSeenGameTime,
                lastInteractionGameTime,
                interactionCount,
                reputation,
                lastDialogueReputationGameTime
        );
        net.minecraft.nbt.ListTag usedDialogueTag = tag.getList("UsedDialogueEntries", Tag.TAG_COMPOUND);
        for (Tag value : usedDialogueTag) {
            CompoundTag usedTag = (CompoundTag) value;
            int index = usedTag.getInt("Index");
            if (index >= 0) {
                memory.dialogueEntryUseGameTimes.put(index, usedTag.getLong("GameTime"));
            }
        }
        return Optional.of(memory);
    }
}
