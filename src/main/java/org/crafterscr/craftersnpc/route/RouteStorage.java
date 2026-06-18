package org.crafterscr.craftersnpc.route;

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
import java.util.LinkedHashMap;
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
        String normalizedRouteId = normalizeRouteId(routeId);
        List<RoutePoint> sanitizedPoints = points.stream()
            .filter(RouteStorage::isFinite)
            .map(point -> new RoutePoint(point.x(), point.y(), point.z(), Mth.clamp(point.waitTicks(), 0, 3600 * 20), point.actionId(), point.actionParameters()))
            .toList();
        routes.put(normalizedRouteId, sanitizedPoints);
        setDirty();
    }

    public List<RoutePoint> getRoute(String routeId) {
        return routes.getOrDefault(normalizeRouteId(routeId), List.of());
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
        return routes.containsKey(normalizeRouteId(routeId));
    }

    public boolean setPointWait(String routeId, int pointIndex, int waitTicks) {
        String normalizedRouteId = normalizeRouteId(routeId);
        List<RoutePoint> points = routes.get(normalizedRouteId);
        if (points == null || pointIndex < 0 || pointIndex >= points.size()) {
            return false;
        }
        List<RoutePoint> updated = new ArrayList<>(points);
        RoutePoint point = updated.get(pointIndex);
        updated.set(pointIndex, new RoutePoint(point.x(), point.y(), point.z(), Mth.clamp(waitTicks, 0, 3600 * 20), point.actionId(), point.actionParameters()));
        routes.put(normalizedRouteId, List.copyOf(updated));
        setDirty();
        return true;
    }

    public boolean setPointAction(String routeId, int pointIndex, String actionId, Map<String, String> parameters) {
        String normalizedRouteId = normalizeRouteId(routeId);
        List<RoutePoint> points = routes.get(normalizedRouteId);
        if (points == null || pointIndex < 0 || pointIndex >= points.size()) {
            return false;
        }
        List<RoutePoint> updated = new ArrayList<>(points);
        RoutePoint point = updated.get(pointIndex);
        updated.set(pointIndex, new RoutePoint(point.x(), point.y(), point.z(), point.waitTicks(), actionId, parameters));
        routes.put(normalizedRouteId, List.copyOf(updated));
        setDirty();
        return true;
    }

    public boolean removeRoute(String routeId) {
        String normalized = normalizeRouteId(routeId);
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
                routePoints.add(new RoutePoint(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"), decodeWaitTicks(point), point.getString("Action"), readActionParameters(point)));
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
                writeAction(pointTag, point.actionId(), point.actionParameters());
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

    public static void writeAction(CompoundTag tag, String actionId, Map<String, String> parameters) {
        if (actionId != null && !actionId.isBlank()) {
            tag.putString("Action", actionId);
        }
        if (parameters != null && !parameters.isEmpty()) {
            CompoundTag parametersTag = new CompoundTag();
            parameters.forEach(parametersTag::putString);
            tag.put("ActionParameters", parametersTag);
        }
    }

    public static Map<String, String> readActionParameters(CompoundTag point) {
        if (!point.contains("ActionParameters", Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        CompoundTag parametersTag = point.getCompound("ActionParameters");
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String key : parametersTag.getAllKeys()) {
            if (parametersTag.contains(key, Tag.TAG_STRING)) {
                parameters.put(key, parametersTag.getString(key));
            }
        }
        return Map.copyOf(parameters);
    }

    private static Map<String, String> sanitizeActionParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        parameters.forEach((key, value) -> {
            if (key != null && value != null && !key.isBlank()) {
                sanitized.put(key.trim().toLowerCase(Locale.ROOT), value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static boolean isFinite(RoutePoint point) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }

    private static String normalizeRouteId(String routeId) {
        return routeId == null ? "" : routeId.toLowerCase(Locale.ROOT);
    }

    public record RoutePoint(double x, double y, double z, int waitTicks, String actionId, Map<String, String> actionParameters) {
        public RoutePoint(double x, double y, double z, int waitTicks) {
            this(x, y, z, waitTicks, "", Map.of());
        }

        public RoutePoint {
            actionId = actionId == null ? "" : actionId.trim().toLowerCase(Locale.ROOT);
            actionParameters = sanitizeActionParameters(actionParameters);
        }
    }
}
