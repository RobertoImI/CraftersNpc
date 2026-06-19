package org.crafterscr.craftersnpc.dialogue;

/** Runtime information used to select an NPC dialogue entry. */
public record DialogueContext(String category) {
    public static DialogueContext generic() {
        return new DialogueContext(DialogueEntry.GENERIC_CATEGORY);
    }

    public DialogueContext {
        category = DialogueEntry.normalizeCategory(category);
    }

    public boolean matchesCategory(String entryCategory) {
        String normalized = DialogueEntry.normalizeCategory(entryCategory);
        return category.equals(normalized) || DialogueEntry.GENERIC_CATEGORY.equals(normalized);
    }
}
