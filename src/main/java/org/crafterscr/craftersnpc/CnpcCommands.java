package org.crafterscr.craftersnpc;

import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;
import org.crafterscr.craftersnpc.behavior.action.NpcActionRegistry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CnpcCommands {
    private static final SimpleCommandExceptionType MUST_LOOK_CNPC = new SimpleCommandExceptionType(Component.literal("Debes mirar un CNPC a menos de 8 bloques."));
    private static final SimpleCommandExceptionType WRONG_ENTITY = new SimpleCommandExceptionType(Component.literal("La entidad observada no es un CNPC."));

    private CnpcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cnpc")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("create")
                .then(Commands.argument("npcId", StringArgumentType.word())
                    .then(Commands.literal("steve").executes(ctx -> createNpc(ctx, false)))
                    .then(Commands.literal("alex").executes(ctx -> createNpc(ctx, true)))))
            .then(Commands.literal("skin")
                .then(Commands.argument("skin", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestSkins(builder))
                    .executes(ctx -> setSkinLooked(ctx, StringArgumentType.getString(ctx, "skin")))))
            .then(Commands.literal("npc")
                .then(Commands.literal("skin")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .then(Commands.argument("skin", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestSkins(builder))
                            .executes(ctx -> setSkinById(ctx, StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "skin"))))))
                .then(Commands.literal("route")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestRoutes(ctx, builder))
                            .executes(ctx -> assignRoute(ctx, StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "routeId"))))))
                .then(Commands.literal("schedule")
                    .then(Commands.literal("assign")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .then(Commands.argument("startTime", IntegerArgumentType.integer(0, NpcScheduleEntry.DAY_TICKS - 1))
                                .then(Commands.argument("endTime", IntegerArgumentType.integer(0, NpcScheduleEntry.DAY_TICKS - 1))
                                    .then(Commands.argument("routeId", StringArgumentType.word())
                                        .suggests(CnpcCommands::suggestRoutes)
                                        .executes(CnpcCommands::assignScheduleEntry))))))
                    .then(Commands.literal("list")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .executes(CnpcCommands::listSchedule)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .then(Commands.argument("entry", IntegerArgumentType.integer(1))
                                .suggests(CnpcCommands::suggestScheduleEntries)
                                .executes(CnpcCommands::removeScheduleEntry)))))
                .then(Commands.literal("temperament")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .then(Commands.literal("pacifico").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.PACIFICO)))
                        .then(Commands.literal("agresivo").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.AGRESIVO)))
                        .then(Commands.literal("aleatorio").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.ALEATORIO)))))
                .then(Commands.literal("speed")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.05D, 1.0D))
                            .executes(ctx -> setWalkSpeed(ctx, StringArgumentType.getString(ctx, "npcId"), DoubleArgumentType.getDouble(ctx, "value"))))))
                .then(Commands.literal("debug")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .executes(ctx -> debugNpc(ctx, StringArgumentType.getString(ctx, "npcId")))))
                .then(Commands.literal("unstick")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .executes(ctx -> unstickNpc(ctx, StringArgumentType.getString(ctx, "npcId")))))
                .then(Commands.literal("nightmode")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .then(Commands.literal("on").executes(ctx -> setNightMode(ctx, StringArgumentType.getString(ctx, "npcId"), true)))
                        .then(Commands.literal("off").executes(ctx -> setNightMode(ctx, StringArgumentType.getString(ctx, "npcId"), false)))))
                .then(Commands.literal("nightrefuge")
                    .then(Commands.literal("add")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .executes(ctx -> addNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId"), 5))
                            .then(Commands.argument("waitSeconds", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> addNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId"), IntegerArgumentType.getInteger(ctx, "waitSeconds"))))))
                    .then(Commands.literal("clear")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .executes(ctx -> clearNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId")))))
                    .then(Commands.literal("list")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestNpcIds)
                            .executes(ctx -> listNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId"))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestNpcIds)
                        .executes(ctx -> removeNpc(ctx, StringArgumentType.getString(ctx, "npcId")))))
                .then(Commands.literal("list")
                    .executes(CnpcCommands::listNpcs))
                .then(Commands.literal("damage")
                    .then(Commands.literal("on").executes(ctx -> setNpcDamage(ctx, true)))
                    .then(Commands.literal("off").executes(ctx -> setNpcDamage(ctx, false)))
                    .then(Commands.literal("status").executes(CnpcCommands::npcDamageStatus))))
            .then(Commands.literal("route")
                .then(Commands.literal("edit")
                    .then(Commands.argument("routeId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRoutes(ctx, builder))
                        .executes(ctx -> editRoute(ctx, StringArgumentType.getString(ctx, "routeId")))))
                .then(Commands.literal("save")
                    .executes(CnpcCommands::saveRoute))
                .then(Commands.literal("cancel")
                    .executes(CnpcCommands::cancelRoute))
                .then(Commands.literal("remove")
                    .then(Commands.argument("routeId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestRoutes(ctx, builder))
                        .executes(ctx -> removeRoute(ctx, StringArgumentType.getString(ctx, "routeId")))))
                .then(Commands.literal("action")
                    .then(Commands.literal("set")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestRoutes(ctx, builder))
                            .then(Commands.argument("point", IntegerArgumentType.integer(1))
                                .suggests(CnpcCommands::suggestRoutePoints)
                                .then(Commands.argument("actionId", StringArgumentType.word())
                                    .suggests(CnpcCommands::suggestActions)
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> setRoutePointAction(ctx, ""))
                                        .then(Commands.argument("parameters", StringArgumentType.greedyString())
                                            .suggests(CnpcCommands::suggestActionParameters)
                                            .executes(ctx -> setRoutePointAction(ctx, StringArgumentType.getString(ctx, "parameters")))))))))
                    .then(Commands.literal("clear")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                            .suggests(CnpcCommands::suggestRoutes)
                            .then(Commands.argument("point", IntegerArgumentType.integer(1))
                                .suggests(CnpcCommands::suggestRoutePoints)
                                .executes(CnpcCommands::clearRoutePointAction)))))
                .then(Commands.literal("wait")
                    .then(Commands.argument("routeId", StringArgumentType.word())
                        .suggests(CnpcCommands::suggestRoutes)
                        .then(Commands.argument("point", IntegerArgumentType.integer(1))
                            .suggests(CnpcCommands::suggestRoutePoints)
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .executes(CnpcCommands::setRoutePointWait)))))
                .then(Commands.literal("list")
                    .executes(CnpcCommands::listRoutes))
                .then(Commands.literal("preview")
                    .executes(CnpcCommands::toggleRoutesPreview)))
            .then(Commands.literal("wand")
                .then(Commands.literal("set")
                    .executes(CnpcCommands::setWand))
                .then(Commands.literal("clear")
                    .executes(CnpcCommands::clearWand))));
    }

    private static int createNpc(CommandContext<CommandSourceStack> context, boolean slimModel) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId").toLowerCase(Locale.ROOT);
        if (NpcRegistry.findById(player.getServer(), npcId).isPresent()) {
            context.getSource().sendFailure(Component.literal("Ya existe un NPC con ID: " + npcId));
            return 0;
        }
        CnpcEntity.spawn(player.serverLevel(), player.position(), npcId, slimModel);
        context.getSource().sendSuccess(() -> Component.literal("CNPC creado con ID " + npcId + " y modelo " + (slimModel ? "alex" : "steve")), true);
        return 1;
    }

    private static int setSkinLooked(CommandContext<CommandSourceStack> context, String skin) throws CommandSyntaxException {
        CnpcEntity npc = requireLookedNpc(context);
        npc.setSkinId(skin);
        context.getSource().sendSuccess(() -> Component.literal("Skin del CNPC cambiada a: " + skin), true);
        return 1;
    }

    private static int setSkinById(CommandContext<CommandSourceStack> context, String npcId, String skin) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().setSkinId(skin);
        context.getSource().sendSuccess(() -> Component.literal("Skin de " + npcId + " actualizada a " + skin), true);
        return 1;
    }

    private static int editRoute(CommandContext<CommandSourceStack> context, String routeId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        String normalizedRouteId = routeId.toLowerCase(Locale.ROOT);
        RouteWandManager.startSession(player, normalizedRouteId, storage.getRoute(normalizedRouteId));
        context.getSource().sendSuccess(() -> Component.literal("Edición de ruta " + normalizedRouteId + " iniciada. Shift+Flecha Arriba/Abajo para cambiar espera por punto, click derecho para agregar, shift+click para borrar último."), false);
        return 1;
    }

    private static int saveRoute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<RouteWandManager.BuildSession> session = RouteWandManager.session(player);
        if (session.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No tienes una ruta en edición."));
            return 0;
        }
        RouteStorage.get(player.serverLevel()).saveRoute(session.get().routeId(), session.get().points());
        int size = session.get().points().size();
        RouteWandManager.clearSession(player);
        context.getSource().sendSuccess(() -> Component.literal("Ruta guardada con ID " + session.get().routeId() + " con " + size + " puntos."), true);
        return 1;
    }

    private static int cancelRoute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteWandManager.clearSession(player);
        context.getSource().sendSuccess(() -> Component.literal("Edición de ruta cancelada."), false);
        return 1;
    }

    private static int listRoutes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        String ids = storage.routeIds().stream().sorted()
            .map(routeId -> routeId + " (" + storage.getRoute(routeId).size() + " puntos)")
            .reduce((a, b) -> a + ", " + b).orElse("(sin rutas)");
        context.getSource().sendSuccess(() -> Component.literal("Rutas: " + ids), false);
        return 1;
    }

    private static int toggleRoutesPreview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = RouteWandManager.toggleAllRoutesPreview(player);
        context.getSource().sendSuccess(() -> Component.literal(enabled
            ? "Visualización de todas las rutas activada (solo visible con wand en mano)."
            : "Visualización de todas las rutas desactivada."), false);
        return 1;
    }

    private static int setRoutePointAction(CommandContext<CommandSourceStack> context, String rawParameters) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String routeId = StringArgumentType.getString(context, "routeId").toLowerCase(Locale.ROOT);
        int point = IntegerArgumentType.getInteger(context, "point");
        String actionId = StringArgumentType.getString(context, "actionId").toLowerCase(Locale.ROOT);
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        if (NpcActionRegistry.find(actionId).isEmpty()) {
            context.getSource().sendFailure(Component.literal("Acción desconocida: " + actionId + ". Disponibles: " + String.join(", ", NpcActionRegistry.ids())));
            return 0;
        }
        final Map<String, String> parameters;
        try {
            parameters = ActionParameters.parse(rawParameters);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        if (!storage.setPointAction(routeId, point - 1, actionId, parameters)) {
            context.getSource().sendFailure(Component.literal("No existe el punto #" + point + " en la ruta " + routeId));
            return 0;
        }
        storage.setPointWait(routeId, point - 1, seconds * 20);
        context.getSource().sendSuccess(() -> Component.literal("Acción " + actionId + " asignada al punto #" + point + " de " + routeId + " durante " + seconds + "s"), true);
        return 1;
    }

    private static int setRoutePointWait(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String routeId = StringArgumentType.getString(context, "routeId");
        int point = IntegerArgumentType.getInteger(context, "point");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        if (!RouteStorage.get(player.serverLevel()).setPointWait(routeId, point - 1, seconds * 20)) {
            context.getSource().sendFailure(Component.literal("No existe el punto #" + point + " en la ruta " + routeId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("El punto #" + point + " de " + routeId + " esperará " + seconds + "s"), true);
        return 1;
    }

    private static int clearRoutePointAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String routeId = StringArgumentType.getString(context, "routeId").toLowerCase(Locale.ROOT);
        int point = IntegerArgumentType.getInteger(context, "point");
        if (!RouteStorage.get(player.serverLevel()).setPointAction(routeId, point - 1, "", Map.of())) {
            context.getSource().sendFailure(Component.literal("No existe el punto #" + point + " en la ruta " + routeId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Acción eliminada del punto #" + point + " de " + routeId), true);
        return 1;
    }

    private static int removeRoute(CommandContext<CommandSourceStack> context, String routeId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String normalizedRouteId = routeId.toLowerCase(Locale.ROOT);
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        if (!storage.removeRoute(normalizedRouteId)) {
            context.getSource().sendFailure(Component.literal("No existe ruta: " + normalizedRouteId));
            return 0;
        }

        int affectedNpcs = 0;
        for (ServerLevel serverLevel : player.getServer().getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof CnpcEntity npc) {
                    boolean assigned = npc.getAssignedRouteId().equalsIgnoreCase(normalizedRouteId);
                    int removedScheduleEntries = npc.removeScheduleEntriesForRoute(normalizedRouteId);
                    if (assigned) {
                        npc.setAssignedRouteId("");
                        if (npc.getSchedule().isEmpty()) {
                            npc.setRouteEnabled(false);
                        }
                    }
                    if (assigned || removedScheduleEntries > 0) {
                        affectedNpcs++;
                    }
                }
            }
        }

        int finalAffectedNpcs = affectedNpcs;
        context.getSource().sendSuccess(() -> Component.literal("Ruta " + normalizedRouteId + " eliminada. NPCs afectados: " + finalAffectedNpcs), true);
        return 1;
    }

    private static int assignRoute(CommandContext<CommandSourceStack> context, String npcId, String routeId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String normalizedRouteId = routeId.toLowerCase(Locale.ROOT);
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        if (!storage.hasRoute(normalizedRouteId)) {
            context.getSource().sendFailure(Component.literal("No existe ruta: " + normalizedRouteId));
            return 0;
        }
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().setAssignedRouteId(normalizedRouteId);
        npc.get().setRouteEnabled(true);
        context.getSource().sendSuccess(() -> Component.literal("Ruta " + normalizedRouteId + " asignada a NPC " + npcId), true);
        return 1;
    }

    private static int assignScheduleEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId");
        String routeId = StringArgumentType.getString(context, "routeId").toLowerCase(Locale.ROOT);
        if (!RouteStorage.get(player.serverLevel()).hasRoute(routeId)) {
            context.getSource().sendFailure(Component.literal("No existe ruta: " + routeId));
            return 0;
        }
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }

        try {
            NpcScheduleEntry entry = new NpcScheduleEntry(
                IntegerArgumentType.getInteger(context, "startTime"),
                IntegerArgumentType.getInteger(context, "endTime"),
                routeId
            );
            npc.get().setScheduleEntry(entry);
            context.getSource().sendSuccess(() -> Component.literal("Horario de " + npcId + " asignado: " + formatScheduleEntry(entry)), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listSchedule(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId");
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        List<NpcScheduleEntry> schedule = npc.get().getSchedule();
        if (schedule.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Horario de " + npcId + ": (sin entradas; usa la ruta manual asignada)"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("Horario de " + npcId + ":"), false);
        for (int index = 0; index < schedule.size(); index++) {
            int entryNumber = index + 1;
            NpcScheduleEntry entry = schedule.get(index);
            context.getSource().sendSuccess(() -> Component.literal("#" + entryNumber + " " + formatScheduleEntry(entry)), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Fuera de estas franjas el NPC se detiene; la hora final es exclusiva."), false);
        return schedule.size();
    }

    private static int removeScheduleEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String npcId = StringArgumentType.getString(context, "npcId");
        int entry = IntegerArgumentType.getInteger(context, "entry");
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        if (!npc.get().removeScheduleEntry(entry - 1)) {
            context.getSource().sendFailure(Component.literal("No existe la entrada #" + entry + " en el horario de " + npcId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Entrada #" + entry + " eliminada del horario de " + npcId), true);
        return 1;
    }

    private static String formatScheduleEntry(NpcScheduleEntry entry) {
        return entry.startTime() + "-" + entry.endTime() + " -> " + entry.routeId();
    }

    private static int setNightMode(CommandContext<CommandSourceStack> context, String npcId, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().setNightModeOnly(enabled);
        context.getSource().sendSuccess(() -> Component.literal("NightMode de " + npcId + " = " + enabled), true);
        return 1;
    }

    private static int setTemperament(CommandContext<CommandSourceStack> context, String npcId, CnpcEntity.Temperament temperament) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().setTemperament(temperament);
        context.getSource().sendSuccess(() -> Component.literal("Temperamento de " + npcId + " = " + temperament.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }

    private static int setWalkSpeed(CommandContext<CommandSourceStack> context, String npcId, double speed) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().setWalkSpeed(speed);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Velocidad de caminata de %s = %.2f", npcId, npc.get().getWalkSpeed())), true);
        return 1;
    }

    private static int setNpcDamage(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NpcSettingsStorage.get(player.serverLevel()).setNpcDamageEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Daño a NPCs " + (enabled ? "activado" : "desactivado")), true);
        return 1;
    }

    private static int npcDamageStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = NpcSettingsStorage.get(player.serverLevel()).isNpcDamageEnabled();
        context.getSource().sendSuccess(() -> Component.literal("Daño a NPCs: " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int debugNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Estado de " + npcId + ": " + npc.get().debugRouteState()), false);
        return 1;
    }

    private static int unstickNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().forceRecoverFromStall();
        context.getSource().sendSuccess(() -> Component.literal("Se forzó recuperación de ruta para " + npcId), true);
        return 1;
    }

    private static int addNightRefuge(CommandContext<CommandSourceStack> context, String npcId, int waitSeconds) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().addNightRefugePoint(player.position(), waitSeconds);
        context.getSource().sendSuccess(() -> Component.literal("Refugio nocturno agregado a " + npcId + " en tu posición actual."), true);
        return 1;
    }

    private static int clearNightRefuge(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().clearNightRefugePoints();
        context.getSource().sendSuccess(() -> Component.literal("Refugios nocturnos removidos para " + npcId), true);
        return 1;
    }

    private static int listNightRefuge(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        List<String> summary = npc.get().nightRefugeSummary();
        context.getSource().sendSuccess(() -> Component.literal("Refugios nocturnos de " + npcId + ": " + (summary.isEmpty() ? "(sin refugios)" : String.join(" | ", summary))), false);
        return 1;
    }




    private static int removeNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        npc.get().discard();
        context.getSource().sendSuccess(() -> Component.literal("NPC eliminado: " + npcId), true);
        return 1;
    }

    private static int listNpcs(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> npcIds = NpcRegistry.listNpcIds(player.getServer());
        String ids = npcIds.isEmpty() ? "(sin NPCs)" : String.join(", ", npcIds);
        context.getSource().sendSuccess(() -> Component.literal("NPCs: " + ids), false);
        return 1;
    }

    private static int setWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Debes sostener un item con la mano principal."));
            return 0;
        }
        RouteWandManager.setWand(player, stack);
        context.getSource().sendSuccess(() -> Component.literal("Wand configurada: " + RouteWandManager.getWandItemId(player)), false);
        return 1;
    }

    private static int clearWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteWandManager.clearWand(player);
        context.getSource().sendSuccess(() -> Component.literal("Wand removida."), false);
        return 1;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestActions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        NpcActionRegistry.ids().forEach(id -> NpcActionRegistry.find(id).ifPresent(action -> builder.suggest(id, Component.literal(action.description()))));
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestActionParameters(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String actionId = StringArgumentType.getString(context, "actionId");
        return NpcActionRegistry.find(actionId)
            .map(NpcAction::parameterSuggestions)
            .map(suggestions -> SharedSuggestionProvider.suggest(suggestions, builder))
            .orElseGet(builder::buildFuture);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRoutePoints(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String routeId = StringArgumentType.getString(context, "routeId");
            List<RouteStorage.RoutePoint> points = RouteStorage.get(player.serverLevel()).getRoute(routeId);
            for (int index = 0; index < points.size(); index++) {
                RouteStorage.RoutePoint point = points.get(index);
                String action = point.actionId().isBlank() ? "sin acción" : point.actionId();
                String details = (point.waitTicks() / 20) + "s, " + action + ", "
                    + (int) point.x() + " " + (int) point.y() + " " + (int) point.z();
                builder.suggest(Integer.toString(index + 1), Component.literal(details));
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException | IllegalArgumentException ignored) {
            return builder.buildFuture();
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestScheduleEntries(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String npcId = StringArgumentType.getString(context, "npcId");
            NpcRegistry.findById(player.getServer(), npcId).ifPresent(npc -> {
                List<NpcScheduleEntry> schedule = npc.getSchedule();
                for (int index = 0; index < schedule.size(); index++) {
                    builder.suggest(Integer.toString(index + 1), Component.literal(formatScheduleEntry(schedule.get(index))));
                }
            });
            return builder.buildFuture();
        } catch (CommandSyntaxException ignored) {
            return builder.buildFuture();
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSkins(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SkinDirectory.listSkins(), builder);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRoutes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            RouteStorage storage = RouteStorage.get(player.serverLevel());
            storage.routeIds().stream().sorted().forEach(routeId -> {
                int count = storage.getRoute(routeId).size();
                builder.suggest(routeId, Component.literal(count + (count == 1 ? " punto" : " puntos")));
            });
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            return CompletableFuture.completedFuture(builder.build());
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNpcIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(NpcRegistry.listNpcIds(player.getServer()), builder);
        } catch (CommandSyntaxException e) {
            return CompletableFuture.completedFuture(builder.build());
        }
    }

    private static CnpcEntity requireLookedNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntityHitResult hitResult = raycastEntity(player, 8.0D)
            .orElseThrow(MUST_LOOK_CNPC::create);

        Entity entity = hitResult.getEntity();
        if (!(entity instanceof CnpcEntity npc)) {
            throw WRONG_ENTITY.create();
        }
        return npc;
    }

    private static Optional<EntityHitResult> raycastEntity(ServerPlayer player, double maxDistance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(view.scale(maxDistance));

        AABB box = player.getBoundingBox().expandTowards(view.scale(maxDistance)).inflate(1.0D);

        return player.level().getEntities(player, box, e -> e instanceof CnpcEntity)
            .stream()
            .map(entity -> {
                AABB aabb = entity.getBoundingBox().inflate(entity.getPickRadius());
                return aabb.clip(eyePos, endPos).map(vec3 -> new EntityHitResult(entity, vec3));
            })
            .flatMap(Optional::stream)
            .min(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(eyePos)));
    }
}
