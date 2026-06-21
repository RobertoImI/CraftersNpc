package org.crafterscr.craftersnpc.dialogue;

import java.util.Set;
import java.util.UUID;

/** Runtime information used to select an NPC dialogue entry. */
public record DialogueContext(
    String category,
    UUID playerUuid,
    int reputation,
    boolean hasMetBefore,
    int interactionCount,
    Set<String> activeCategories,
    boolean hasUsedDialogueEntry,
    long ticksSinceDialogueEntryUsed
) {
    public static DialogueContext generic() {
        return new DialogueContext(DialogueEntry.GENERIC_CATEGORY, null, 0, false, 0);
    }

    public DialogueContext(String category) {
        this(category, null, 0, false, 0);
    }

    public DialogueContext(String category, UUID playerUuid, int reputation, boolean hasMetBefore, int interactionCount) {
        this(category, playerUuid, reputation, hasMetBefore, interactionCount, Set.of(DialogueEntry.normalizeCategory(category)));
    }

    public DialogueContext(String category, UUID playerUuid, int reputation, boolean hasMetBefore, int interactionCount, Set<String> activeCategories) {
        this(category, playerUuid, reputation, hasMetBefore, interactionCount, activeCategories, false, -1L);
    }

    public DialogueContext {
        category = DialogueEntry.normalizeCategory(category);
        interactionCount = Math.max(0, interactionCount);
        activeCategories = activeCategories == null || activeCategories.isEmpty()
            ? Set.of(category)
            : activeCategories.stream()
                .map(DialogueEntry::normalizeCategory)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ticksSinceDialogueEntryUsed = Math.max(-1L, ticksSinceDialogueEntryUsed);
    }

    public boolean matchesCategory(String entryCategory) {
        return activeCategories.contains(DialogueEntry.normalizeCategory(entryCategory));
    }

    public boolean matchesGenericCategory(String entryCategory) {
        return DialogueEntry.GENERIC_CATEGORY.equals(DialogueEntry.normalizeCategory(entryCategory));
    }

    public DialogueContext withDialogueEntryState(boolean used, long ticksSinceUsed) {
        return new DialogueContext(category, playerUuid, reputation, hasMetBefore, interactionCount, activeCategories, used, ticksSinceUsed);
    }
}
