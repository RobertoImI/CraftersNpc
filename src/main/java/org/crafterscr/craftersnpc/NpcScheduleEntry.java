package org.crafterscr.craftersnpc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;
import java.util.Optional;

/** Assigns a route to a Minecraft day-time interval. The end time is exclusive. */
public record NpcScheduleEntry(int startTime, int endTime, String routeId) {
    public static final int DAY_TICKS = 24000;

    public NpcScheduleEntry {
        if (startTime < 0 || startTime >= DAY_TICKS || endTime < 0 || endTime >= DAY_TICKS) {
            throw new IllegalArgumentException("Las horas deben estar entre 0 y 23999 ticks.");
        }
        if (startTime == endTime) {
            throw new IllegalArgumentException("La hora inicial y final no pueden ser iguales.");
        }
        routeId = routeId == null ? "" : routeId.toLowerCase(Locale.ROOT);
        if (routeId.isBlank()) {
            throw new IllegalArgumentException("El route ID del horario no puede estar vacío.");
        }
    }

    public boolean contains(int dayTime) {
        int normalizedTime = Math.floorMod(dayTime, DAY_TICKS);
        if (startTime < endTime) {
            return normalizedTime >= startTime && normalizedTime < endTime;
        }
        return normalizedTime >= startTime || normalizedTime < endTime;
    }

    public boolean overlaps(NpcScheduleEntry other) {
        return contains(other.startTime) || other.contains(startTime);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StartTime", startTime);
        tag.putInt("EndTime", endTime);
        tag.putString("RouteId", routeId);
        return tag;
    }

    public static Optional<NpcScheduleEntry> load(CompoundTag tag) {
        if (!tag.contains("StartTime", Tag.TAG_INT) || !tag.contains("EndTime", Tag.TAG_INT) || !tag.contains("RouteId", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new NpcScheduleEntry(tag.getInt("StartTime"), tag.getInt("EndTime"), tag.getString("RouteId")));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

