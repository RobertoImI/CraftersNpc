package org.crafterscr.craftersnpc.preset;

import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcScheduleEntry;
import org.crafterscr.craftersnpc.route.RouteStorage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

public final class NpcPresetStorage {
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_PRESET_NAME_LENGTH = 64;
    public static final int MAX_ROUTE_POINTS = 512;
    public static final int MAX_NIGHT_REFUGES = 128;
    public static final int MAX_SCHEDULE_ENTRIES = 48;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_\\-.]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcPresetStorage() {}

    public static String normalizeId(String raw, String label) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " no puede estar vacío.");
        }
        if (value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(label + " excede " + MAX_ID_LENGTH + " caracteres.");
        }
        if (!SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " solo puede contener letras, números, '_', '-' y '.'.");
        }
        return value;
    }

    public static String normalizePresetName(String raw) {
        String value = normalizeId(raw, "El presetName");
        if (value.length() > MAX_PRESET_NAME_LENGTH) {
            throw new IllegalArgumentException("El presetName excede " + MAX_PRESET_NAME_LENGTH + " caracteres.");
        }
        return value;
    }

    public static Path presetPath(MinecraftServer server, String presetName) {
        return server.getFile("craftersnpc/presets").resolve(normalizePresetName(presetName) + ".json").normalize();
    }

    public static Path save(MinecraftServer server, CnpcEntity npc, String presetName) throws IOException {
        Path path = presetPath(server, presetName);
        Files.createDirectories(path.getParent());
        JsonObject root = toJson(npc);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        }
        return path;
    }

    public static JsonObject load(MinecraftServer server, String presetName) throws IOException {
        Path path = presetPath(server, presetName);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("No existe el preset: " + normalizePresetName(presetName));
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("El preset debe ser un objeto JSON.");
            }
            JsonObject root = element.getAsJsonObject();
            validate(root);
            return root;
        }
    }

    public static JsonObject toJson(CnpcEntity npc) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("npcId", npc.getNpcId());
        root.addProperty("npcName", npc.getNpcName());
        root.addProperty("skinId", npc.getSkinId());
        root.addProperty("slimModel", npc.isSlimModel());
        root.addProperty("temperament", npc.getTemperament().id());
        root.addProperty("walkSpeed", npc.getWalkSpeed());
        root.addProperty("assignedRouteId", npc.getAssignedRouteId());
        root.addProperty("routeEnabled", npc.isRouteEnabledForPreset());
        root.addProperty("nightModeOnly", npc.isNightModeOnly());
        root.add("dialogues", dialogues(npc));
        root.add("route", route(npc.getPresetRoutePoints()));
        root.add("assignedRoutes", assignedRoutes(npc));
        root.add("schedule", schedule(npc.getSchedule()));
        root.add("nightRefuges", route(npc.getPresetNightRefugePoints()));
        root.add("social", npc.socialConfigForPreset());
        return root;
    }

    public static void apply(ServerLevel level, CnpcEntity npc, JsonObject root) {
        validate(root);
        importAssignedRoutes(level, root);
        npc.applyPresetData(
                string(root, "npcId"), optionalString(root, "npcName"), string(root, "skinId"), bool(root, "slimModel"),
                CnpcEntity.Temperament.fromId(string(root, "temperament")), number(root, "walkSpeed"),
                optionalString(root, "assignedRouteId"), optionalBool(root, "routeEnabled"), optionalBool(root, "nightModeOnly"),
                readDialogues(root.getAsJsonArray("dialogues")), readRoute(root.getAsJsonArray("route"), MAX_ROUTE_POINTS),
                readSchedule(root.getAsJsonArray("schedule")), readRoute(root.getAsJsonArray("nightRefuges"), MAX_NIGHT_REFUGES),
                root.has("social") && root.get("social").isJsonObject() ? root.getAsJsonObject("social") : new JsonObject());
    }

    public static void validate(JsonObject root) {
        normalizeId(string(root, "npcId"), "El npcId");
        normalizeId(string(root, "skinId"), "El skinId");
        string(root, "temperament");
        bool(root, "slimModel");
        readDialogues(array(root, "dialogues"));
        readRoute(array(root, "route"), MAX_ROUTE_POINTS);
        if (root.has("assignedRoutes") && !root.get("assignedRoutes").isJsonObject()) {
            throw new IllegalArgumentException("assignedRoutes debe ser un objeto JSON.");
        }
        if (root.has("assignedRoutes") && root.get("assignedRoutes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("assignedRoutes").entrySet()) {
                normalizeId(entry.getKey(), "El routeId");
                if (!entry.getValue().isJsonArray()) {
                    throw new IllegalArgumentException("Cada ruta asignada debe ser un array.");
                }
                readRoute(entry.getValue().getAsJsonArray(), MAX_ROUTE_POINTS);
            }
        }
        readRoute(array(root, "nightRefuges"), MAX_NIGHT_REFUGES);
        readSchedule(array(root, "schedule"));
        if (!root.has("social") || !root.get("social").isJsonObject()) {
            throw new IllegalArgumentException("Falta objeto social.");
        }
    }

    private static JsonArray dialogues(CnpcEntity npc) {
        JsonArray array = new JsonArray();
        for (DialogueEntry entry : npc.getDialogueEntries()) {
            JsonObject object = new JsonObject();
            object.addProperty("text", entry.text());
            array.add(object);
        }
        return array;
    }

    private static JsonArray route(List<CnpcEntity.RoutePoint> points) {
        JsonArray array = new JsonArray();
        for (CnpcEntity.RoutePoint point : points) {
            JsonObject object = new JsonObject();
            object.addProperty("x", point.pos().x);
            object.addProperty("y", point.pos().y);
            object.addProperty("z", point.pos().z);
            object.addProperty("waitTicks", point.waitTicks());
            object.addProperty("actionId", point.actionId());
            JsonObject parameters = new JsonObject();
            point.actionParameters().forEach(parameters::addProperty);
            object.add("actionParameters", parameters);
            array.add(object);
        }
        return array;
    }

    private static JsonObject assignedRoutes(CnpcEntity npc) {
        JsonObject routes = new JsonObject();
        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return routes;
        }
        RouteStorage storage = RouteStorage.get(serverLevel);
        addStoredRoute(routes, storage, npc.getAssignedRouteId());
        for (NpcScheduleEntry entry : npc.getSchedule()) {
            addStoredRoute(routes, storage, entry.routeId());
        }
        return routes;
    }

    private static void addStoredRoute(JsonObject routes, RouteStorage storage, String routeId) {
        String normalizedRouteId = routeId == null ? "" : routeId.toLowerCase(Locale.ROOT);
        if (normalizedRouteId.isBlank() || routes.has(normalizedRouteId)) {
            return;
        }
        List<RouteStorage.RoutePoint> points = storage.getRoute(normalizedRouteId);
        if (points.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (RouteStorage.RoutePoint point : points) {
            JsonObject object = new JsonObject();
            object.addProperty("x", point.x());
            object.addProperty("y", point.y());
            object.addProperty("z", point.z());
            object.addProperty("waitTicks", point.waitTicks());
            object.addProperty("actionId", point.actionId());
            JsonObject parameters = new JsonObject();
            point.actionParameters().forEach(parameters::addProperty);
            object.add("actionParameters", parameters);
            array.add(object);
        }
        routes.add(normalizedRouteId, array);
    }

    private static void importAssignedRoutes(ServerLevel level, JsonObject root) {
        if (!root.has("assignedRoutes") || !root.get("assignedRoutes").isJsonObject()) {
            return;
        }
        RouteStorage storage = RouteStorage.get(level);
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("assignedRoutes").entrySet()) {
            String routeId = normalizeId(entry.getKey(), "El routeId");
            if (!entry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("La ruta asignada " + routeId + " debe ser un array.");
            }
            List<CnpcEntity.RoutePoint> points = readRoute(entry.getValue().getAsJsonArray(), MAX_ROUTE_POINTS);
            storage.saveRoute(routeId, points.stream()
                    .map(point -> new RouteStorage.RoutePoint(point.pos().x, point.pos().y, point.pos().z, point.waitTicks(), point.actionId(), point.actionParameters()))
                    .toList());
        }
    }

    private static JsonArray schedule(List<NpcScheduleEntry> entries) {
        JsonArray array = new JsonArray();
        for (NpcScheduleEntry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("startTime", entry.startTime());
            object.addProperty("endTime", entry.endTime());
            object.addProperty("routeId", entry.routeId());
            array.add(object);
        }
        return array;
    }

    private static List<DialogueEntry> readDialogues(JsonArray array) {
        if (array.size() > CnpcEntity.MAX_DIALOGUE_PHRASES) throw new IllegalArgumentException("Demasiados diálogos.");
        List<DialogueEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Cada diálogo debe ser un objeto.");
            JsonObject object = element.getAsJsonObject();
            String text = string(object, "text");
            if (text.isBlank() || text.length() > CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH) throw new IllegalArgumentException("Diálogo inválido o demasiado largo.");
            entries.add(new DialogueEntry(text));
        }
        return entries;
    }

    private static List<CnpcEntity.RoutePoint> readRoute(JsonArray array, int max) {
        if (array.size() > max) throw new IllegalArgumentException("Demasiados puntos de ruta/refugio.");
        List<CnpcEntity.RoutePoint> points = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Cada punto debe ser un objeto.");
            JsonObject o = element.getAsJsonObject();
            Vec3 pos = new Vec3(number(o, "x"), number(o, "y"), number(o, "z"));
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) throw new IllegalArgumentException("Punto con coordenadas inválidas.");
            points.add(new CnpcEntity.RoutePoint(pos, Mth.clamp(optionalInt(o, "waitTicks", 0), 0, 3600 * 20), optionalString(o, "actionId"), readParameters(o)));
        }
        return points;
    }

    private static List<NpcScheduleEntry> readSchedule(JsonArray array) {
        if (array.size() > MAX_SCHEDULE_ENTRIES) throw new IllegalArgumentException("Demasiadas entradas de schedule.");
        List<NpcScheduleEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Cada schedule debe ser un objeto.");
            JsonObject o = element.getAsJsonObject();
            NpcScheduleEntry entry = new NpcScheduleEntry(integer(o, "startTime"), integer(o, "endTime"), string(o, "routeId"));
            if (entries.stream().anyMatch(existing -> existing.overlaps(entry))) throw new IllegalArgumentException("El schedule contiene franjas superpuestas.");
            entries.add(entry);
        }
        return entries;
    }

    private static Map<String, String> readParameters(JsonObject o) {
        if (!o.has("actionParameters") || !o.get("actionParameters").isJsonObject()) {
            return Map.of();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : o.getAsJsonObject("actionParameters").entrySet()) {
            if (entry.getValue().isJsonPrimitive() && !entry.getKey().isBlank()) {
                parameters.put(entry.getKey().strip().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
        }
        return Map.copyOf(parameters);
    }
    private static JsonArray array(JsonObject o, String k) { if (!o.has(k) || !o.get(k).isJsonArray()) throw new IllegalArgumentException("Falta array: " + k); return o.getAsJsonArray(k); }
    private static String string(JsonObject o, String k) { if (!o.has(k) || !o.get(k).isJsonPrimitive()) throw new IllegalArgumentException("Falta dato: " + k); return o.get(k).getAsString(); }
    private static String optionalString(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }
    private static boolean bool(JsonObject o, String k) { if (!o.has(k) || !o.get(k).isJsonPrimitive()) throw new IllegalArgumentException("Falta boolean: " + k); return o.get(k).getAsBoolean(); }
    private static boolean optionalBool(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean(); }
    private static double number(JsonObject o, String k) { if (!o.has(k) || !o.get(k).isJsonPrimitive()) throw new IllegalArgumentException("Falta número: " + k); return o.get(k).getAsDouble(); }
    private static int integer(JsonObject o, String k) { return Mth.floor(number(o, k)); }
    private static int optionalInt(JsonObject o, String k, int fallback) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : fallback; }
}
