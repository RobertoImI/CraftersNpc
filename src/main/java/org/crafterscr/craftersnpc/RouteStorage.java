package org.crafterscr.craftersnpc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RouteStorage extends SavedData {
    private static final String DATA_NAME = "craftersnpc_routes";
    private final Map<String, List<RoutePoint>> routes = new HashMap<>();

    public static RouteStorage get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RouteStorage::new, RouteStorage::load), DATA_NAME);
    }

    public void saveRoute(String routeId, List<RoutePoint> points) {
        routes.put(routeId.toLowerCase(Locale.ROOT), List.copyOf(points));
        setDirty();
    }

    public List<RoutePoint> getRoute(String routeId) {
        return routes.getOrDefault(routeId.toLowerCase(Locale.ROOT), List.of());
    }

    public Set<String> routeIds() {
        return Collections.unmodifiableSet(new HashSet<>(routes.keySet()));
    }

    public Map<String, List<RoutePoint>> allRoutes() {
        Map<String, List<RoutePoint>> snapshot = new HashMap<>();
        routes.forEach((routeId, points) -> snapshot.put(routeId, List.copyOf(points)));
        return Collections.unmodifiableMap(snapshot);
    }

    public boolean hasRoute(String routeId) {
        return routes.containsKey(routeId.toLowerCase(Locale.ROOT));
    }

    public boolean removeRoute(String routeId) {
        String normalized = routeId.toLowerCase(Locale.ROOT);
        if (routes.remove(normalized) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public static RouteStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteStorage storage = new RouteStorage();
        CompoundTag routesTag = tag.getCompound("Routes");
        for (String key : routesTag.getAllKeys()) {
            ListTag points = routesTag.getList(key, Tag.TAG_COMPOUND);
            List<RoutePoint> routePoints = new ArrayList<>();
            for (Tag pointTag : points) {
                CompoundTag point = (CompoundTag) pointTag;
                routePoints.add(new RoutePoint(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"), decodeWaitTicks(point)));
            }
            storage.routes.put(key.toLowerCase(Locale.ROOT), routePoints);
        }
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag routesTag = new CompoundTag();
        routes.forEach((key, value) -> {
            ListTag points = new ListTag();
            for (RoutePoint point : value) {
                CompoundTag pointTag = new CompoundTag();
                pointTag.putDouble("X", point.x());
                pointTag.putDouble("Y", point.y());
                pointTag.putDouble("Z", point.z());
                pointTag.putInt("Wait", point.waitTicks());
                pointTag.putBoolean("WaitIsTicks", true);
                points.add(pointTag);
            }
            routesTag.put(key, points);
        });
        tag.put("Routes", routesTag);
        return tag;
    }

    private static int decodeWaitTicks(CompoundTag point) {
        int rawWait = point.getInt("Wait");
        if (point.contains("WaitIsTicks", Tag.TAG_BYTE) && point.getBoolean("WaitIsTicks")) {
            return Mth.clamp(rawWait, 0, 3600 * 20);
        }
        return Mth.clamp(rawWait, 0, 3600) * 20;
    }

    public record RoutePoint(double x, double y, double z, int waitTicks) {
    }
}
