package org.crafterscr.craftersnpc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.crafterscr.craftersnpc.reputation.NpcReputation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private int lastDialoguePhraseIndex = -1;
    private final Set<Integer> usedOnceDialogueIndexes = new HashSet<>();
    private final Map<Integer, Long> dialogueCooldownUntil = new HashMap<>();

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

    public int lastDialoguePhraseIndex() {
        return lastDialoguePhraseIndex;
    }

    public boolean hasUsedDialogueEntry(int index) {
        return usedOnceDialogueIndexes.contains(index) || dialogueCooldownUntil.containsKey(index);
    }

    public long ticksSinceDialogueEntryUsed(int index, long currentGameTime) {
        Long cooldownUntil = dialogueCooldownUntil.get(index);
        if (cooldownUntil == null) {
            return usedOnceDialogueIndexes.contains(index) ? 0L : -1L;
        }
        return currentGameTime >= cooldownUntil ? Long.MAX_VALUE : 0L;
    }

    public boolean hasUsedOnceDialogueEntry(int index) {
        return usedOnceDialogueIndexes.contains(index);
    }

    public boolean isDialogueOnCooldown(int index, long currentGameTime) {
        Long cooldownUntil = dialogueCooldownUntil.get(index);
        return cooldownUntil != null && cooldownUntil > currentGameTime;
    }

    public void markDialogueEntryUsed(int index, int cooldownTicks, long gameTime) {
        if (index >= 0) {
            lastDialoguePhraseIndex = index;
            usedOnceDialogueIndexes.add(index);
            if (cooldownTicks > 0) {
                dialogueCooldownUntil.put(index, gameTime + cooldownTicks);
            }
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
        tag.putInt("LastDialoguePhraseIndex", lastDialoguePhraseIndex);
        net.minecraft.nbt.ListTag usedOnceTag = new net.minecraft.nbt.ListTag();
        for (int index : usedOnceDialogueIndexes) {
            CompoundTag usedTag = new CompoundTag();
            usedTag.putInt("Index", index);
            usedOnceTag.add(usedTag);
        }
        tag.put("UsedOnceDialogueIndexes", usedOnceTag);
        net.minecraft.nbt.ListTag cooldownTag = new net.minecraft.nbt.ListTag();
        for (Map.Entry<Integer, Long> entry : dialogueCooldownUntil.entrySet()) {
            CompoundTag cooldownEntryTag = new CompoundTag();
            cooldownEntryTag.putInt("Index", entry.getKey());
            cooldownEntryTag.putLong("Until", entry.getValue());
            cooldownTag.add(cooldownEntryTag);
        }
        tag.put("DialogueCooldownUntil", cooldownTag);
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
        memory.lastDialoguePhraseIndex = tag.contains("LastDialoguePhraseIndex", Tag.TAG_INT) ? tag.getInt("LastDialoguePhraseIndex") : -1;
        net.minecraft.nbt.ListTag usedOnceTag = tag.getList("UsedOnceDialogueIndexes", Tag.TAG_COMPOUND);
        for (Tag value : usedOnceTag) {
            CompoundTag usedTag = (CompoundTag) value;
            int index = usedTag.getInt("Index");
            if (index >= 0) {
                memory.usedOnceDialogueIndexes.add(index);
            }
        }
        net.minecraft.nbt.ListTag cooldownTag = tag.getList("DialogueCooldownUntil", Tag.TAG_COMPOUND);
        for (Tag value : cooldownTag) {
            CompoundTag cooldownEntryTag = (CompoundTag) value;
            int index = cooldownEntryTag.getInt("Index");
            if (index >= 0) {
                memory.dialogueCooldownUntil.put(index, cooldownEntryTag.getLong("Until"));
            }
        }
        net.minecraft.nbt.ListTag legacyUsedDialogueTag = tag.getList("UsedDialogueEntries", Tag.TAG_COMPOUND);
        for (Tag value : legacyUsedDialogueTag) {
            CompoundTag usedTag = (CompoundTag) value;
            int index = usedTag.getInt("Index");
            if (index >= 0) {
                memory.usedOnceDialogueIndexes.add(index);
            }
        }
        return Optional.of(memory);
    }
}
