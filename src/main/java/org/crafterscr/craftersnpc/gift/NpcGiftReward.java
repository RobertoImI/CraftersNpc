package org.crafterscr.craftersnpc.gift;

import net.minecraft.nbt.CompoundTag;

public record NpcGiftReward(String item, int min, int max, int weight) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", item);
        tag.putInt("Min", min);
        tag.putInt("Max", max);
        tag.putInt("Weight", weight);
        return tag;
    }

    public static NpcGiftReward load(CompoundTag tag) {
        int min = Math.max(1, tag.getInt("Min"));
        int max = Math.max(min, tag.getInt("Max"));
        int weight = Math.max(1, tag.getInt("Weight"));
        return new NpcGiftReward(tag.getString("Item"), min, max, weight);
    }
}
