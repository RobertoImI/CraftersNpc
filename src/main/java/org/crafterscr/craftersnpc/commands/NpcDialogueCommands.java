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

final class NpcDialogueCommands {
    private NpcDialogueCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("dialogue")
                .then(Commands.literal("assign")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                                .executes(NpcDialogueCommands::assignCategorizedDialoguePhrase)))
                                .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                        .executes(NpcDialogueCommands::assignDialoguePhrase))))
                .then(Commands.literal("list")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(NpcDialogueCommands::listDialoguePhrases)))
                .then(Commands.literal("metadata")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .executes(NpcDialogueCommands::listDialogueMetadata)))
                .then(Commands.literal("edit")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .then(Commands.argument("phrase", IntegerArgumentType.integer(1))
                                        .suggests(CnpcCommandSuggestions::suggestDialoguePhrases)
                                        .then(Commands.argument("newPhrase", StringArgumentType.greedyString()).executes(NpcDialogueCommands::editDialoguePhrase))
                                        .then(Commands.literal("weight").then(Commands.argument("weight", IntegerArgumentType.integer(1, 10_000)).executes(NpcDialogueCommands::editDialogueWeight)))
                                        .then(Commands.literal("category").then(Commands.argument("category", StringArgumentType.word()).executes(NpcDialogueCommands::editDialogueCategory)))
                                        .then(Commands.literal("reputation").then(Commands.argument("min", IntegerArgumentType.integer(-100, 100)).then(Commands.argument("max", IntegerArgumentType.integer(-100, 100)).executes(NpcDialogueCommands::editDialogueReputationRange))))
                                        .then(Commands.literal("once").then(Commands.literal("on").executes(ctx -> editDialogueOncePerPlayer(ctx, true))).then(Commands.literal("off").executes(ctx -> editDialogueOncePerPlayer(ctx, false))))
                                        .then(Commands.literal("cooldown").then(Commands.argument("ticks", IntegerArgumentType.integer(0)).executes(NpcDialogueCommands::editDialogueCooldown)))
                                        .then(Commands.literal("priority").then(Commands.argument("priority", IntegerArgumentType.integer()).executes(NpcDialogueCommands::editDialoguePriority))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("npcId", StringArgumentType.word())
                                .suggests(CnpcCommandSuggestions::suggestNpcIds)
                                .then(Commands.argument("phrase", IntegerArgumentType.integer(1))
                                        .suggests(CnpcCommandSuggestions::suggestDialoguePhrases)
                                        .executes(NpcDialogueCommands::removeDialoguePhrase))));
    }
    static int assignDialoguePhrase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return assignDialoguePhrase(context, DialogueEntry.GENERIC_CATEGORY);
    }

    static int assignCategorizedDialoguePhrase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return assignDialoguePhrase(context, StringArgumentType.getString(context, "category"));
    }

    static int assignDialoguePhrase(CommandContext<CommandSourceStack> context, String category) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        String phrase = StringArgumentType.getString(context, "phrase").strip();
        String normalizedCategory = DialogueEntry.normalizeCategory(category);
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty() || phrase.isEmpty()) {
            context.getSource().sendFailure(Component.literal(phrase.isEmpty() ? "La frase no puede estar vacía." : "NPC no encontrado: " + npcId));
            return 0;
        }
        if (!npc.get().addDialogueEntry(phrase, normalizedCategory)) {
            context.getSource().sendFailure(Component.literal("La frase debe tener entre 1 y " + CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH
                    + " caracteres y el NPC admite hasta " + CnpcEntity.MAX_DIALOGUE_PHRASES + " frases."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Frase [" + normalizedCategory + "] asignada a " + npcId + "."), true);
        return 1;
    }

    static int listDialoguePhrases(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        List<DialogueEntry> entries = npc.get().getDialogueEntries();
        if (entries.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Frases de " + npcId + ": (sin frases)"), false);
            return 1;
        }
        for (int index = 0; index < entries.size(); index++) {
            DialogueEntry entry = entries.get(index);
            Component line = Component.literal("#" + (index + 1) + " [" + entry.category() + ", peso " + entry.weight()
                    + "]: " + entry.text());
            context.getSource().sendSuccess(() -> line, false);
        }
        return entries.size();
    }

    static int listDialogueMetadata(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        List<DialogueEntry> entries = npc.get().getDialogueEntries();
        if (entries.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Metadatos de diálogo de " + npcId + ": (sin frases)"), false);
            return 1;
        }
        for (int index = 0; index < entries.size(); index++) {
            DialogueEntry entry = entries.get(index);
            Component line = Component.literal("#" + (index + 1)
                    + " [categoria=" + entry.category()
                    + ", peso=" + entry.weight()
                    + ", reputacion=" + entry.minReputation() + ".." + entry.maxReputation()
                    + ", oncePerPlayer=" + entry.oncePerPlayer()
                    + ", cooldownTicks=" + entry.cooldownTicks()
                    + ", prioridad=" + entry.priority()
                    + "]: " + entry.text());
            context.getSource().sendSuccess(() -> line, false);
        }
        return entries.size();
    }

    static int editDialoguePhrase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        int phraseIndex = IntegerArgumentType.getInteger(context, "phrase") - 1;
        String newPhrase = StringArgumentType.getString(context, "newPhrase");
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        if (!npc.get().editDialoguePhrase(phraseIndex, newPhrase)) {
            context.getSource().sendFailure(Component.literal("Índice inválido o la frase debe tener entre 1 y "
                    + CnpcEntity.MAX_DIALOGUE_PHRASE_LENGTH + " caracteres."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Frase #" + (phraseIndex + 1) + " editada en " + npcId + "."), true);
        return 1;
    }

    static int editDialogueWeight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return editDialogueMetadata(context, "peso", npc -> npc.editDialogueWeight(
                IntegerArgumentType.getInteger(context, "phrase") - 1,
                IntegerArgumentType.getInteger(context, "weight")));
    }

    static int editDialogueCategory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        int phraseIndex = IntegerArgumentType.getInteger(context, "phrase") - 1;
        String category = DialogueEntry.normalizeCategory(StringArgumentType.getString(context, "category"));
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId + "; categoría no actualizada a " + category + "."));
            return 0;
        }
        if (!npc.get().editDialogueCategory(phraseIndex, category)) {
            context.getSource().sendFailure(Component.literal("Índice de frase inválido; categoría no actualizada a " + category + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Frase #" + (phraseIndex + 1) + ": categoría actualizada a "
                + category + " en " + npcId + "."), true);
        return 1;
    }

    static int editDialogueReputationRange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return editDialogueMetadata(context, "rango de reputación", npc -> npc.editDialogueReputationRange(
                IntegerArgumentType.getInteger(context, "phrase") - 1,
                IntegerArgumentType.getInteger(context, "min"),
                IntegerArgumentType.getInteger(context, "max")));
    }

    static int editDialogueOncePerPlayer(CommandContext<CommandSourceStack> context, boolean oncePerPlayer) throws CommandSyntaxException {
        return editDialogueMetadata(context, "oncePerPlayer", npc -> npc.editDialogueOncePerPlayer(
                IntegerArgumentType.getInteger(context, "phrase") - 1,
                oncePerPlayer));
    }

    static int editDialogueCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return editDialogueMetadata(context, "cooldown", npc -> npc.editDialogueCooldown(
                IntegerArgumentType.getInteger(context, "phrase") - 1,
                IntegerArgumentType.getInteger(context, "ticks")));
    }

    static int editDialoguePriority(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return editDialogueMetadata(context, "prioridad", npc -> npc.editDialoguePriority(
                IntegerArgumentType.getInteger(context, "phrase") - 1,
                IntegerArgumentType.getInteger(context, "priority")));
    }

    static int editDialogueMetadata(CommandContext<CommandSourceStack> context, String field, java.util.function.Predicate<CnpcEntity> update) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        int phraseIndex = IntegerArgumentType.getInteger(context, "phrase") - 1;
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        if (!update.test(npc.get())) {
            context.getSource().sendFailure(Component.literal("Índice de frase inválido."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Frase #" + (phraseIndex + 1) + ": " + field + " actualizado en " + npcId + "."), true);
        return 1;
    }

    static int removeDialoguePhrase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String npcId = StringArgumentType.getString(context, "npcId");
        int phraseIndex = IntegerArgumentType.getInteger(context, "phrase") - 1;
        Optional<CnpcEntity> npc = CnpcCommandUtils.findNpc(context, npcId);
        if (npc.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + npcId));
            return 0;
        }
        if (!npc.get().removeDialoguePhrase(phraseIndex)) {
            context.getSource().sendFailure(Component.literal("Índice de frase inválido."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Frase removida de " + npcId + "."), true);
        return 1;
    }

}
