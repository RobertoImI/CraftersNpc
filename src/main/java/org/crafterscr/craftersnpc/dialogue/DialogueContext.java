package org.crafterscr.craftersnpc.dialogue;

import java.util.UUID;

/** Runtime information used to select an NPC dialogue entry. */
public record DialogueContext(
    String category,
    UUID playerUuid,
    int reputation,
    boolean hasMetBefore,
    int interactionCount
) {
    public static DialogueContext generic() {
        return new DialogueContext(DialogueEntry.GENERIC_CATEGORY, null, 0, false, 0);
    }

    public DialogueContext(String category) {
        this(category, null, 0, false, 0);
    }

    public DialogueContext {
        category = DialogueEntry.normalizeCategory(category);
        interactionCount = Math.max(0, interactionCount);
    }

    public boolean matchesCategory(String entryCategory) {
        String normalized = DialogueEntry.normalizeCategory(entryCategory);
        return category.equals(normalized) || DialogueEntry.GENERIC_CATEGORY.equals(normalized);
    }
}
