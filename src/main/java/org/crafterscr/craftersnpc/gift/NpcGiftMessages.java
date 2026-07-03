package org.crafterscr.craftersnpc.gift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.*;

public class NpcGiftMessages {
    public static final List<String> TYPES = List.of("favorite", "liked", "unknown", "cooldown", "reward", "noReward");
    private static final Map<String, String> DEFAULTS = Map.of(
            "favorite", "¡Wow! ¡Esto me encanta!",
            "liked", "¡Muchas gracias! Esto me gusta mucho.",
            "cooldown", "Ahora no quiero recibir más regalos. Vuelve luego.",
            "reward", "Toma, quiero darte esto a cambio.",
            "noReward", "Gracias por el regalo."
    );
    private final Map<String, List<String>> messages = new HashMap<>();

    public static boolean isValidType(String type) { return TYPES.contains(type); }
    public List<String> get(String type) { return List.copyOf(messages.getOrDefault(type, List.of())); }
    public int count(String type) { return messages.getOrDefault(type, List.of()).size(); }
    public void add(String type, String text) { if (isValidType(type) && !text.isBlank()) messages.computeIfAbsent(type, k -> new ArrayList<>()).add(text); }
    public boolean remove(String type, int oneBasedIndex) { List<String> list = messages.get(type); if (list == null || oneBasedIndex < 1 || oneBasedIndex > list.size()) return false; list.remove(oneBasedIndex - 1); return true; }
    public void clear(String type) { messages.remove(type); }
    public Optional<String> randomOptional(String type, RandomSource random) { List<String> list = messages.get(type); if (list == null || list.isEmpty()) return Optional.empty(); return Optional.of(list.get(random.nextInt(list.size()))); }
    public String random(String type, RandomSource random) { return randomOptional(type, random).orElse(DEFAULTS.getOrDefault(type, "Gracias.")); }
    public CompoundTag save() { CompoundTag tag = new CompoundTag(); for (String type : TYPES) { ListTag list = new ListTag(); for (String msg : messages.getOrDefault(type, List.of())) list.add(StringTag.valueOf(msg)); if (!list.isEmpty()) tag.put(type, list); } return tag; }
    public static NpcGiftMessages load(CompoundTag tag) { NpcGiftMessages result = new NpcGiftMessages(); for (String type : TYPES) { ListTag list = tag.getList(type, Tag.TAG_STRING); for (Tag value : list) result.add(type, value.getAsString()); } return result; }
}
