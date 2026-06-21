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

final class NpcSettingsCommands {
    private NpcSettingsCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> registerTemperament() {
        return Commands.literal("temperament")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.literal("pacifico").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.PACIFICO)))
                        .then(Commands.literal("agresivo").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.AGRESIVO)))
                        .then(Commands.literal("aleatorio").executes(ctx -> setTemperament(ctx, StringArgumentType.getString(ctx, "npcId"), CnpcEntity.Temperament.ALEATORIO))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerSpeed() {
        return Commands.literal("speed")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.05D, 1.0D))
                                .executes(ctx -> setWalkSpeed(ctx, StringArgumentType.getString(ctx, "npcId"), DoubleArgumentType.getDouble(ctx, "value")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerDamage() {
        return Commands.literal("damage")
                .then(Commands.literal("on").executes(ctx -> setNpcDamage(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setNpcDamage(ctx, false)))
                .then(Commands.literal("status").executes(NpcSettingsCommands::npcDamageStatus));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerNightMode() {
        return Commands.literal("nightmode")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.literal("on").executes(ctx -> setNightMode(ctx, StringArgumentType.getString(ctx, "npcId"), true)))
                        .then(Commands.literal("off").executes(ctx -> setNightMode(ctx, StringArgumentType.getString(ctx, "npcId"), false))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerNightRefuge() {
        return Commands.literal("nightrefuge")
                .then(Commands.literal("add")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(ctx -> addNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId"), 5))
                                .then(Commands.argument("waitSeconds", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> addNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId"), IntegerArgumentType.getInteger(ctx, "waitSeconds"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(ctx -> clearNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId")))))
                .then(Commands.literal("list")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(ctx -> listNightRefuge(ctx, StringArgumentType.getString(ctx, "npcId")))));
    }
    static int setNightMode(CommandContext<CommandSourceStack> context, String npcId, boolean enabled) throws CommandSyntaxException {
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

    static int setTemperament(CommandContext<CommandSourceStack> context, String npcId, CnpcEntity.Temperament temperament) throws CommandSyntaxException {
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

    static int setWalkSpeed(CommandContext<CommandSourceStack> context, String npcId, double speed) throws CommandSyntaxException {
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

    static int setNpcDamage(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NpcSettingsStorage.get(player.serverLevel()).setNpcDamageEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Daño a NPCs " + (enabled ? "activado" : "desactivado")), true);
        return 1;
    }

    static int npcDamageStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = NpcSettingsStorage.get(player.serverLevel()).isNpcDamageEnabled();
        context.getSource().sendSuccess(() -> Component.literal("Daño a NPCs: " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    static int debugNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Estado de " + npcId + ": " + npc.get().debugRouteState()), false);
        return 1;
    }

    static int unstickNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
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

    static int addNightRefuge(CommandContext<CommandSourceStack> context, String npcId, int waitSeconds) throws CommandSyntaxException {
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

    static int clearNightRefuge(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
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

    static int listNightRefuge(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
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




}
