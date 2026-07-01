package org.crafterscr.craftersnpc.dialogue;

import net.minecraft.nbt.CompoundTag;

/** Simple dialogue phrase. Advanced metadata is intentionally disabled for now. */
public record DialogueEntry(String text) {
    public static DialogueEntry generic(String text) {
        return new DialogueEntry(text);
    }

    public DialogueEntry {
        text = normalizeText(text);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Text", text);
        return tag;
    }

    public static DialogueEntry load(CompoundTag tag) {
        return new DialogueEntry(tag.getString("Text"));
    }

    public static String normalizeText(String text) {
        return text == null ? "" : text.strip();
    }
}
