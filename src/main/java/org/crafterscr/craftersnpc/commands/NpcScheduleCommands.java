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

final class NpcScheduleCommands {
    private NpcScheduleCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("schedule")
                .then(Commands.literal("assign")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .then(Commands.argument("startTime", IntegerArgumentType.integer(0, NpcScheduleEntry.DAY_TICKS - 1))
                                        .then(Commands.argument("endTime", IntegerArgumentType.integer(0, NpcScheduleEntry.DAY_TICKS - 1))
                                                .then(Commands.argument("routeId", StringArgumentType.word())
                                                        .suggests(CnpcCommandSuggestions::suggestRoutes)
                                                        .executes(NpcScheduleCommands::assignScheduleEntry))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(NpcScheduleCommands::listSchedule)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .then(Commands.argument("entry", IntegerArgumentType.integer(1))
                                        .suggests(CnpcCommandSuggestions::suggestScheduleEntries)
                                        .executes(NpcScheduleCommands::removeScheduleEntry))));
    }
    static int assignScheduleEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
            context.getSource().sendSuccess(() -> Component.literal("Horario de " + npcId + " asignado: " + CnpcCommandUtils.formatScheduleEntry(entry)), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    static int listSchedule(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
            context.getSource().sendSuccess(() -> Component.literal("#" + entryNumber + " " + CnpcCommandUtils.formatScheduleEntry(entry)), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Fuera de estas franjas usa la ruta manual asignada o, si no hay una, continúa la última ruta programada; NightMode sigue teniendo prioridad; la hora final es exclusiva."), false);
        return schedule.size();
    }

    static int removeScheduleEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

}
