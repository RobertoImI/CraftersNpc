package org.crafterscr.craftersnpc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcSettingsStorage extends SavedData {
    private static final String DATA_NAME = "craftersnpc_settings";

    private boolean npcDamageEnabled = true;

    public static NpcSettingsStorage get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(NpcSettingsStorage::new, NpcSettingsStorage::load), DATA_NAME);
    }

    public static NpcSettingsStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcSettingsStorage storage = new NpcSettingsStorage();
        if (tag.contains("NpcDamageEnabled")) {
            storage.npcDamageEnabled = tag.getBoolean("NpcDamageEnabled");
        }
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("NpcDamageEnabled", npcDamageEnabled);
        return tag;
    }

    public boolean isNpcDamageEnabled() {
        return npcDamageEnabled;
    }

    public void setNpcDamageEnabled(boolean npcDamageEnabled) {
        this.npcDamageEnabled = npcDamageEnabled;
        setDirty();
    }
}
