package org.crafterscr.craftersnpc.dialogue;

import org.crafterscr.craftersnpc.reputation.NpcReputation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/** Structured dialogue phrase with metadata used for contextual selection. */
public record DialogueEntry(
    String text,
    String category,
    int weight,
    int minReputation,
    int maxReputation,
    boolean oncePerPlayer,
    int cooldownTicks,
    int priority
) {
    public static final String GENERIC_CATEGORY = "generic";
    public static final int DEFAULT_WEIGHT = 1;
    public static final int DEFAULT_MIN_REPUTATION = NpcReputation.MIN;
    public static final int DEFAULT_MAX_REPUTATION = NpcReputation.MAX;
    public static final int DEFAULT_PRIORITY = 0;

    public DialogueEntry(String text, String category, int weight) {
        this(text, category, weight, DEFAULT_MIN_REPUTATION, DEFAULT_MAX_REPUTATION, false, 0, DEFAULT_PRIORITY);
    }

    public DialogueEntry {
        text = normalizeText(text);
        category = normalizeCategory(category);
        weight = Mth.clamp(weight, 1, 10_000);
        minReputation = NpcReputation.clamp(minReputation);
        maxReputation = NpcReputation.clamp(maxReputation);
        if (minReputation > maxReputation) {
            int previousMin = minReputation;
            minReputation = maxReputation;
            maxReputation = previousMin;
        }
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public static DialogueEntry generic(String text) {
        return new DialogueEntry(text, GENERIC_CATEGORY, DEFAULT_WEIGHT);
    }

    public boolean canUse(DialogueContext context) {
        if (context == null) {
            return true;
        }
        return context.matchesCategory(category)
            && context.reputation() >= minReputation
            && context.reputation() <= maxReputation
            && (!oncePerPlayer || !context.hasUsedDialogueEntry())
            && (cooldownTicks <= 0 || context.ticksSinceDialogueEntryUsed() < 0 || context.ticksSinceDialogueEntryUsed() >= cooldownTicks);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Text", text);
        tag.putString("Category", category);
        tag.putInt("Weight", weight);
        tag.putInt("MinReputation", minReputation);
        tag.putInt("MaxReputation", maxReputation);
        tag.putBoolean("OncePerPlayer", oncePerPlayer);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putInt("Priority", priority);
        return tag;
    }

    public static DialogueEntry load(CompoundTag tag) {
        String text = tag.getString("Text");
        String category = tag.contains("Category", Tag.TAG_STRING) ? tag.getString("Category") : GENERIC_CATEGORY;
        int weight = tag.contains("Weight", Tag.TAG_INT) ? tag.getInt("Weight") : DEFAULT_WEIGHT;
        int minReputation = tag.contains("MinReputation", Tag.TAG_INT) ? tag.getInt("MinReputation") : DEFAULT_MIN_REPUTATION;
        int maxReputation = tag.contains("MaxReputation", Tag.TAG_INT) ? tag.getInt("MaxReputation") : DEFAULT_MAX_REPUTATION;
        boolean oncePerPlayer = tag.getBoolean("OncePerPlayer");
        int cooldownTicks = tag.contains("CooldownTicks", Tag.TAG_INT) ? tag.getInt("CooldownTicks") : 0;
        int priority = tag.contains("Priority", Tag.TAG_INT) ? tag.getInt("Priority") : DEFAULT_PRIORITY;
        return new DialogueEntry(text, category, weight, minReputation, maxReputation, oncePerPlayer, cooldownTicks, priority);
    }

    public static String normalizeText(String text) {
        return text == null ? "" : text.strip();
    }

    public static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? GENERIC_CATEGORY : normalized;
    }
}
