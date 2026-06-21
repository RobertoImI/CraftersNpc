package org.crafterscr.craftersnpc.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

public final class NpcDataMigrations {
    public static final String DATA_VERSION_TAG = "DataVersionCraftersNpc";

    // Version 1: adds DataVersionCraftersNpc and migrates legacy route Wait values from seconds to ticks.
    public static final int CURRENT_DATA_VERSION = 1;

    private NpcDataMigrations() {
    }

    public static int readVersion(CompoundTag tag) {
        return tag.contains(DATA_VERSION_TAG, Tag.TAG_INT) ? tag.getInt(DATA_VERSION_TAG) : 0;
    }

    public static boolean needsMigration(CompoundTag tag) {
        return readVersion(tag) < CURRENT_DATA_VERSION;
    }

    public static void writeCurrentVersion(CompoundTag tag) {
        tag.putInt(DATA_VERSION_TAG, CURRENT_DATA_VERSION);
    }

    public static CompoundTag migrateEntityData(CompoundTag tag) {
        int version = readVersion(tag);
        if (version < 1) {
            migrateLegacyRouteWaits(tag, "Route");
            migrateLegacyRouteWaits(tag, "NightRefuges");
            version = 1;
        }
        tag.putInt(DATA_VERSION_TAG, Math.max(version, CURRENT_DATA_VERSION));
        return tag;
    }

    public static CompoundTag migrateRouteStorageData(CompoundTag tag) {
        int version = readVersion(tag);
        if (version < 1) {
            CompoundTag routesTag = tag.getCompound("Routes");
            for (String key : routesTag.getAllKeys()) {
                migrateLegacyRouteWaits(routesTag, key);
            }
            version = 1;
        }
        tag.putInt(DATA_VERSION_TAG, Math.max(version, CURRENT_DATA_VERSION));
        return tag;
    }

    public static CompoundTag migrateNpcSettingsData(CompoundTag tag) {
        int version = readVersion(tag);
        if (version < 1) {
            version = 1;
        }
        tag.putInt(DATA_VERSION_TAG, Math.max(version, CURRENT_DATA_VERSION));
        return tag;
    }

    private static void migrateLegacyRouteWaits(CompoundTag ownerTag, String listKey) {
        ListTag points = ownerTag.getList(listKey, Tag.TAG_COMPOUND);
        for (Tag pointValue : points) {
            CompoundTag point = (CompoundTag) pointValue;
            if (!point.contains("WaitIsTicks", Tag.TAG_BYTE)) {
                point.putInt("Wait", Mth.clamp(point.getInt("Wait"), 0, 3600) * 20);
                point.putBoolean("WaitIsTicks", true);
            }
        }
    }
}
