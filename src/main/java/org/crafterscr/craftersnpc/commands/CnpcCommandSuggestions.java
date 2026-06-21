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

final class CnpcCommandSuggestions {
    private CnpcCommandSuggestions() {}
    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestActions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        NpcActionRegistry.ids().forEach(id -> NpcActionRegistry.find(id).ifPresent(action -> builder.suggest(id, Component.literal(action.description()))));
        return builder.buildFuture();
    }

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestActionParameters(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String actionId = StringArgumentType.getString(context, "actionId");
        return NpcActionRegistry.find(actionId)
                .map(NpcAction::parameterSuggestions)
                .map(suggestions -> SharedSuggestionProvider.suggest(suggestions, builder))
                .orElseGet(builder::buildFuture);
    }

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRoutePoints(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
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

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDialoguePhrases(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            String npcId = StringArgumentType.getString(context, "npcId");
            NpcRegistry.findById(context.getSource().getPlayerOrException().getServer(), npcId).ifPresent(npc -> {
                List<String> phrases = npc.getDialoguePhrases();
                for (int index = 0; index < phrases.size(); index++) {
                    builder.suggest(Integer.toString(index + 1), Component.literal(phrases.get(index)));
                }
            });
        } catch (CommandSyntaxException ignored) {
        }
        return builder.buildFuture();
    }

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestScheduleEntries(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String npcId = StringArgumentType.getString(context, "npcId");
            NpcRegistry.findById(player.getServer(), npcId).ifPresent(npc -> {
                List<NpcScheduleEntry> schedule = npc.getSchedule();
                for (int index = 0; index < schedule.size(); index++) {
                    builder.suggest(Integer.toString(index + 1), Component.literal(CnpcCommandUtils.formatScheduleEntry(schedule.get(index))));
                }
            });
            return builder.buildFuture();
        } catch (CommandSyntaxException ignored) {
            return builder.buildFuture();
        }
    }

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSkins(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SkinDirectory.listSkins(), builder);
    }

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRoutes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
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

    static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNpcIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(NpcRegistry.listNpcIds(player.getServer()), builder);
        } catch (CommandSyntaxException e) {
            return CompletableFuture.completedFuture(builder.build());
        }
    }

}
