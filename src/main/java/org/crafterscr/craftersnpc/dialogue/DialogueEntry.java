package org.crafterscr.craftersnpc.dialogue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/** Structured dialogue phrase with metadata used for contextual selection. */
public record DialogueEntry(String text, String category, int weight) {
    public static final String GENERIC_CATEGORY = "generic";
    public static final int DEFAULT_WEIGHT = 1;

    public DialogueEntry {
        text = normalizeText(text);
        category = normalizeCategory(category);
        weight = Math.max(1, weight);
    }

    public static DialogueEntry generic(String text) {
        return new DialogueEntry(text, GENERIC_CATEGORY, DEFAULT_WEIGHT);
    }

    public boolean canUse(DialogueContext context) {
        return context == null || context.matchesCategory(category);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Text", text);
        tag.putString("Category", category);
        tag.putInt("Weight", weight);
        return tag;
    }

    public static DialogueEntry load(CompoundTag tag) {
        String text = tag.getString("Text");
        String category = tag.contains("Category", Tag.TAG_STRING) ? tag.getString("Category") : GENERIC_CATEGORY;
        int weight = tag.contains("Weight", Tag.TAG_INT) ? tag.getInt("Weight") : DEFAULT_WEIGHT;
        return new DialogueEntry(text, category, Mth.clamp(weight, 1, 10_000));
    }

    public static String normalizeText(String text) {
        return text == null ? "" : text.strip();
    }

    public static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? GENERIC_CATEGORY : normalized;
    }
}
