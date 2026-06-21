package org.crafterscr.craftersnpc.commands;

import org.crafterscr.craftersnpc.entity.*;
import org.crafterscr.craftersnpc.dialogue.*;
import org.crafterscr.craftersnpc.route.*;
import org.crafterscr.craftersnpc.skin.*;
import org.crafterscr.craftersnpc.storage.*;
import org.crafterscr.craftersnpc.behavior.action.*;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.suggestion.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

final class NpcRouteCommands {
    private NpcRouteCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> registerNpcRouteAssignment() {
        return Commands.literal("route")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.argument("routeId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestRoutes)
                                .executes(ctx -> assignRoute(ctx, StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "routeId")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerRouteCommands() {
        return Commands.literal("route")
                .then(Commands.literal("edit")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestRoutes)
                                .executes(ctx -> editRoute(ctx, StringArgumentType.getString(ctx, "routeId")))))
                .then(Commands.literal("save").executes(NpcRouteCommands::saveRoute))
                .then(Commands.literal("cancel").executes(NpcRouteCommands::cancelRoute))
                .then(Commands.literal("remove")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestRoutes)
                                .executes(ctx -> removeRoute(ctx, StringArgumentType.getString(ctx, "routeId")))))
                .then(Commands.literal("action")
                        .then(Commands.literal("set")
                                .then(Commands.argument("routeId", StringArgumentType.word())
                                        .suggests(CnpcCommandSuggestions::suggestRoutes)
                                        .then(Commands.argument("point", IntegerArgumentType.integer(1))
                                                .suggests(CnpcCommandSuggestions::suggestRoutePoints)
                                                .then(Commands.argument("actionId", StringArgumentType.word())
                                                        .suggests(CnpcCommandSuggestions::suggestActions)
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                                .executes(ctx -> setRoutePointAction(ctx, ""))
                                                                .then(Commands.argument("parameters", StringArgumentType.greedyString())
                                                                        .suggests(CnpcCommandSuggestions::suggestActionParameters)
                                                                        .executes(ctx -> setRoutePointAction(ctx, StringArgumentType.getString(ctx, "parameters"))))))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("routeId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestRoutes)
                                .then(Commands.argument("point", IntegerArgumentType.integer(1))
                                        .suggests(CnpcCommandSuggestions::suggestRoutePoints)
                                        .executes(NpcRouteCommands::clearRoutePointAction))))
            .then(Commands.literal("wait")
                .then(Commands.argument("routeId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestRoutes)
                        .then(Commands.argument("point", IntegerArgumentType.integer(1))
                                .suggests(CnpcCommandSuggestions::suggestRoutePoints)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(NpcRouteCommands::setRoutePointWait)))))
                .then(Commands.literal("list").executes(NpcRouteCommands::listRoutes))
                .then(Commands.literal("preview").executes(NpcRouteCommands::toggleRoutesPreview));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerWandCommands() {
        return Commands.literal("wand")
                .then(Commands.literal("set").executes(NpcRouteCommands::setWand))
                .then(Commands.literal("clear").executes(NpcRouteCommands::clearWand));
    }
    static int editRoute(CommandContext<CommandSourceStack> context, String routeId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        String normalizedRouteId = routeId.toLowerCase(Locale.ROOT);
        RouteWandManager.startSession(player, normalizedRouteId, storage.getRoute(normalizedRouteId));
        context.getSource().sendSuccess(() -> Component.literal("Edición de ruta " + normalizedRouteId + " iniciada. Shift+Flecha Arriba/Abajo para cambiar espera por punto, click derecho para agregar, shift+click para borrar último."), false);
        return 1;
    }

    static int saveRoute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    static int cancelRoute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteWandManager.clearSession(player);
        context.getSource().sendSuccess(() -> Component.literal("Edición de ruta cancelada."), false);
        return 1;
    }

    static int listRoutes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        String ids = storage.routeIds().stream().sorted()
                .map(routeId -> routeId + " (" + storage.getRoute(routeId).size() + " puntos)")
                .reduce((a, b) -> a + ", " + b).orElse("(sin rutas)");
        context.getSource().sendSuccess(() -> Component.literal("Rutas: " + ids), false);
        return 1;
    }

    static int toggleRoutesPreview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = RouteWandManager.toggleAllRoutesPreview(player);
        context.getSource().sendSuccess(() -> Component.literal(enabled
                ? "Visualización de todas las rutas activada (solo visible con wand en mano)."
                : "Visualización de todas las rutas desactivada."), false);
        return 1;
    }

    static int setRoutePointAction(CommandContext<CommandSourceStack> context, String rawParameters) throws CommandSyntaxException {
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

    static int setRoutePointWait(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    static int clearRoutePointAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    static int removeRoute(CommandContext<CommandSourceStack> context, String routeId) throws CommandSyntaxException {
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

    static int assignRoute(CommandContext<CommandSourceStack> context, String npcId, String routeId) throws CommandSyntaxException {
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

    static int setWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    static int clearWand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RouteWandManager.clearWand(player);
        context.getSource().sendSuccess(() -> Component.literal("Wand removida."), false);
        return 1;
    }

}
