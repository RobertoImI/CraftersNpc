package org.crafterscr.craftersnpc.gift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class NpcGiftData {
    public enum Category { FAVORITE, LIKED, UNKNOWN }
    private boolean enabled = false;
    private final Set<String> likedItems = new LinkedHashSet<>();
    private final Set<String> favoriteItems = new LinkedHashSet<>();
    private final List<NpcGiftReward> rewardPool = new ArrayList<>();
    private double rewardChance = 5.0D;
    private int cooldownSeconds = 600;
    private boolean consumeLiked = true, consumeFavorite = true;
    private NpcGiftMessages messages = new NpcGiftMessages();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> likedItems() { return likedItems; }
    public Set<String> favoriteItems() { return favoriteItems; }
    public List<NpcGiftReward> rewardPool() { return rewardPool; }
    public double rewardChance() { return rewardChance; }
    public void setRewardChance(double v) { rewardChance = Math.max(0, Math.min(100, v)); }
    public int cooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int v) { cooldownSeconds = Math.max(0, v); }
    public boolean consumeLiked() { return consumeLiked; }
    public void setConsumeLiked(boolean v) { consumeLiked = v; }
    public boolean consumeFavorite() { return consumeFavorite; }
    public void setConsumeFavorite(boolean v) { consumeFavorite = v; }
    public NpcGiftMessages messages() { return messages; }
    public Category categoryOf(String item) { if (favoriteItems.contains(item)) return Category.FAVORITE; if (likedItems.contains(item)) return Category.LIKED; return Category.UNKNOWN; }
    public boolean accepts(String item) { return categoryOf(item) != Category.UNKNOWN; }
    public boolean shouldConsume(Category c) { return c == Category.FAVORITE ? consumeFavorite : c == Category.LIKED && consumeLiked; }
    public boolean addToCategory(Category c, String item) { likedItems.remove(item); favoriteItems.remove(item); return switch (c) { case FAVORITE -> favoriteItems.add(item); case LIKED -> likedItems.add(item); default -> false; }; }
    public boolean removeFromCategory(Category c, String item) { return switch (c) { case FAVORITE -> favoriteItems.remove(item); case LIKED -> likedItems.remove(item); default -> false; }; }
    public boolean removeReward(String item) { return rewardPool.removeIf(r -> r.item().equals(item)); }

    public CompoundTag save() { CompoundTag tag = new CompoundTag(); tag.putBoolean("Enabled", enabled); putStrings(tag,"LikedItems",likedItems); putStrings(tag,"FavoriteItems",favoriteItems); tag.putDouble("RewardChance", rewardChance); tag.putInt("CooldownSeconds", cooldownSeconds); tag.putBoolean("ConsumeLiked", consumeLiked); tag.putBoolean("ConsumeFavorite", consumeFavorite); tag.put("Messages", messages.save()); ListTag rewards = new ListTag(); for (NpcGiftReward r: rewardPool) rewards.add(r.save()); tag.put("RewardPool", rewards); return tag; }
    public static NpcGiftData load(CompoundTag tag) { NpcGiftData d = new NpcGiftData(); d.enabled = tag.getBoolean("Enabled"); readStrings(tag,"LikedItems",d.likedItems); readStrings(tag,"FavoriteItems",d.favoriteItems); if (tag.contains("RewardChance")) d.setRewardChance(tag.getDouble("RewardChance")); if (tag.contains("CooldownSeconds")) d.setCooldownSeconds(tag.getInt("CooldownSeconds")); if (tag.contains("ConsumeLiked")) d.consumeLiked = tag.getBoolean("ConsumeLiked"); if (tag.contains("ConsumeFavorite")) d.consumeFavorite = tag.getBoolean("ConsumeFavorite"); if (tag.contains("Messages", Tag.TAG_COMPOUND)) d.messages = NpcGiftMessages.load(tag.getCompound("Messages")); ListTag rewards = tag.getList("RewardPool", Tag.TAG_COMPOUND); for (Tag value: rewards) d.rewardPool.add(NpcGiftReward.load((CompoundTag)value)); d.likedItems.removeAll(d.favoriteItems); return d; }
    private static void putStrings(CompoundTag tag, String name, Collection<String> values) { ListTag list = new ListTag(); for (String v: values) list.add(StringTag.valueOf(v)); tag.put(name, list); }
    private static void readStrings(CompoundTag tag, String name, Set<String> target) { for (Tag value: tag.getList(name, Tag.TAG_STRING)) target.add(value.getAsString()); }
}
