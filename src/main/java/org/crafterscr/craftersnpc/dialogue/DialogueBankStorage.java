package org.crafterscr.craftersnpc.dialogue;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stores reusable dialogue banks shared by multiple NPCs. */
public class DialogueBankStorage extends SavedData {
    private static final String DATA_NAME = "craftersnpc_dialogue_banks";
    public static final int MAX_BANK_PHRASES = 512;
    private final Map<String, List<DialogueEntry>> banks = new HashMap<>();

    public static DialogueBankStorage get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DialogueBankStorage::new, DialogueBankStorage::load), DATA_NAME);
    }

    public List<DialogueEntry> getBank(String bankId) {
        return banks.getOrDefault(normalizeBankId(bankId), List.of());
    }

    public Set<String> bankIds() {
        return Collections.unmodifiableSet(new HashSet<>(banks.keySet()));
    }

    public boolean hasBank(String bankId) {
        return banks.containsKey(normalizeBankId(bankId));
    }

    public void saveBank(String bankId, List<DialogueEntry> entries) {
        banks.put(normalizeBankId(bankId), sanitize(entries));
        setDirty();
    }

    public boolean addPhrase(String bankId, String phrase) {
        String normalized = normalizeBankId(bankId);
        List<DialogueEntry> entries = new ArrayList<>(banks.getOrDefault(normalized, List.of()));
        DialogueEntry entry = DialogueEntry.generic(phrase);
        if (entry.text().isEmpty()) {
            return false;
        }
        entries.add(entry);
        banks.put(normalized, sanitize(entries));
        setDirty();
        return true;
    }

    public boolean removePhrase(String bankId, int index) {
        String normalized = normalizeBankId(bankId);
        List<DialogueEntry> entries = new ArrayList<>(banks.getOrDefault(normalized, List.of()));
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        entries.remove(index);
        banks.put(normalized, List.copyOf(entries));
        setDirty();
        return true;
    }

    public boolean removeBank(String bankId) {
        if (banks.remove(normalizeBankId(bankId)) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public static DialogueBankStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        DialogueBankStorage storage = new DialogueBankStorage();
        CompoundTag banksTag = tag.getCompound("Banks");
        for (String key : banksTag.getAllKeys()) {
            ListTag entriesTag = banksTag.getList(key, Tag.TAG_COMPOUND);
            List<DialogueEntry> entries = new ArrayList<>();
            for (Tag value : entriesTag) {
                entries.add(DialogueEntry.load((CompoundTag) value));
            }
            storage.banks.put(normalizeBankId(key), sanitize(entries));
        }
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag banksTag = new CompoundTag();
        banks.forEach((key, entries) -> {
            ListTag entriesTag = new ListTag();
            for (DialogueEntry entry : entries) {
                entriesTag.add(entry.save());
            }
            banksTag.put(key, entriesTag);
        });
        tag.put("Banks", banksTag);
        return tag;
    }

    private static List<DialogueEntry> sanitize(List<DialogueEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.text().isEmpty())
                .limit(MAX_BANK_PHRASES)
                .toList();
    }

    public static String normalizeBankId(String bankId) {
        String normalized = bankId == null ? "" : bankId.strip().toLowerCase(Locale.ROOT);
        return normalized.replace(' ', '_');
    }
}
