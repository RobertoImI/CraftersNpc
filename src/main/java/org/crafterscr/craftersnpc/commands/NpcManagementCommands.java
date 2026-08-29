package org.crafterscr.craftersnpc.commands;

import org.crafterscr.craftersnpc.entity.*;
import org.crafterscr.craftersnpc.dialogue.*;
import org.crafterscr.craftersnpc.route.*;
import org.crafterscr.craftersnpc.skin.*;
import org.crafterscr.craftersnpc.storage.*;
import org.crafterscr.craftersnpc.network.OpenNpcEditorPayload;
import org.crafterscr.craftersnpc.preset.NpcPresetStorage;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class NpcManagementCommands {
    private NpcManagementCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("create")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .then(Commands.literal("steve").executes(ctx -> createNpc(ctx, false)))
                        .then(Commands.literal("alex").executes(ctx -> createNpc(ctx, true))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerSkinLooked() {
        return Commands.literal("skin")
                .then(Commands.literal("url")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes(ctx -> setSkinUrlLooked(ctx, StringArgumentType.getString(ctx, "url")))))
                .then(Commands.argument("skin", StringArgumentType.word())
                        .suggests((ctx, builder) -> CnpcCommandSuggestions.suggestSkins(builder))
                        .executes(ctx -> setSkinLooked(ctx, StringArgumentType.getString(ctx, "skin"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerNpcSkin() {
        return Commands.literal("skin")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.argument("skin", StringArgumentType.word())
                                .suggests((ctx, builder) -> CnpcCommandSuggestions.suggestSkins(builder))
                                .executes(ctx -> setSkinById(ctx, StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "skin")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerDebug() {
        return Commands.literal("debug")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .executes(ctx -> NpcSettingsCommands.debugNpc(ctx, StringArgumentType.getString(ctx, "npcId"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerUnstick() {
        return Commands.literal("unstick")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .executes(ctx -> NpcSettingsCommands.unstickNpc(ctx, StringArgumentType.getString(ctx, "npcId"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerRemove() {
        return Commands.literal("remove")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .executes(ctx -> removeNpc(ctx, StringArgumentType.getString(ctx, "npcId"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerList() {
        return Commands.literal("list").executes(NpcManagementCommands::listNpcs);
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerExport() {
        return Commands.literal("export")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .then(Commands.argument("presetName", StringArgumentType.word())
                                .executes(ctx -> exportNpc(ctx, StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "presetName")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerImport() {
        return Commands.literal("import")
                .then(Commands.argument("presetName", StringArgumentType.word())
                        .executes(ctx -> importNpc(ctx, StringArgumentType.getString(ctx, "presetName"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerEdit() {
        return Commands.literal("edit")
                .then(Commands.literal("npc").executes(NpcManagementCommands::openEditorForLookedNpc))
                .then(Commands.literal("dialogue").then(NpcDialogueCommands.registerDialogueScreenArgument()))
                .executes(NpcManagementCommands::openEditorForLookedNpc);
    }
    static int openEditorForLookedNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CnpcEntity npc = CnpcCommandUtils.requireLookedNpc(context);
        boolean damageEnabled = NpcSettingsStorage.get(player.serverLevel()).isNpcDamageEnabled();
        PacketDistributor.sendToPlayer(player, new OpenNpcEditorPayload(npc.getId(), npc.getNpcId(), npc.getNpcName(), npc.getSkinId(), !npc.getSkinUrl().isBlank(), npc.isSlimModel(),
                npc.getWalkSpeed(), npc.getTemperament().id(), npc.getAssignedRouteId(), npc.isNightModeOnly(), damageEnabled,
                SkinDirectory.listSkins(), RouteStorage.get(player.serverLevel()).routeIds().stream().sorted().toList()));
        return 1;
    }

    static int createNpc(CommandContext<CommandSourceStack> context, boolean slimModel) throws CommandSyntaxException {
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

    static int setSkinLooked(CommandContext<CommandSourceStack> context, String skin) throws CommandSyntaxException {
        CnpcEntity npc = CnpcCommandUtils.requireLookedNpc(context);
        npc.setSkinId(skin);
        context.getSource().sendSuccess(() -> Component.literal("Skin del CNPC cambiada a: " + skin), true);
        return 1;
    }

    static int setSkinUrlLooked(CommandContext<CommandSourceStack> context, String url) throws CommandSyntaxException {
        CnpcEntity npc = CnpcCommandUtils.requireLookedNpc(context);
        String validatedUrl;
        try {
            validatedUrl = validateSkinUrl(url);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        npc.setSkinUrl(validatedUrl);
        context.getSource().sendSuccess(() -> Component.literal("Skin URL del CNPC actualizada. Los clientes la descargarán automáticamente."), true);
        return 1;
    }

    private static String validateSkinUrl(String value) {
        if (value.length() > 2048) {
            throw new IllegalArgumentException("La URL de la skin es demasiado larga (máximo 2048 caracteres).");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("La URL de la skin no es válida.");
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("La skin debe usar una URL http o https absoluta.");
        }
        return uri.toASCIIString();
    }

    static int setSkinById(CommandContext<CommandSourceStack> context, String npcId, String skin) throws CommandSyntaxException {
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

    static int removeNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
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

    static int listNpcs(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> npcIds = NpcRegistry.listNpcIds(player.getServer());
        String ids = npcIds.isEmpty() ? "(sin NPCs)" : String.join(", ", npcIds);
        context.getSource().sendSuccess(() -> Component.literal("NPCs: " + ids), false);
        return 1;
    }

    static int exportNpc(CommandContext<CommandSourceStack> context, String npcId, String presetName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            String normalizedNpcId = NpcPresetStorage.normalizeId(npcId, "El npcId");
            String normalizedPresetName = NpcPresetStorage.normalizePresetName(presetName);
            Optional<CnpcEntity> npc = NpcRegistry.findById(player.getServer(), normalizedNpcId);
            if (npc.isEmpty()) {
                context.getSource().sendFailure(Component.literal("NPC no encontrado: " + normalizedNpcId));
                return 0;
            }
            Path path = NpcPresetStorage.save(player.getServer(), npc.get(), normalizedPresetName);
            context.getSource().sendSuccess(() -> Component.literal("Preset exportado: " + normalizedPresetName + " -> " + path), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            context.getSource().sendFailure(Component.literal(ex.getMessage()));
            return 0;
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("No se pudo exportar el preset: " + ex.getMessage()));
            return 0;
        }
    }

    static int importNpc(CommandContext<CommandSourceStack> context, String presetName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            String normalizedPresetName = NpcPresetStorage.normalizePresetName(presetName);
            com.google.gson.JsonObject preset = NpcPresetStorage.load(player.getServer(), normalizedPresetName);
            String npcId = NpcPresetStorage.normalizeId(preset.get("npcId").getAsString(), "El npcId");
            if (NpcRegistry.findById(player.getServer(), npcId).isPresent()) {
                context.getSource().sendFailure(Component.literal("Ya existe un NPC con ID: " + npcId));
                return 0;
            }
            CnpcEntity npc = CnpcEntity.spawn(player.serverLevel(), player.position(), npcId, preset.get("slimModel").getAsBoolean());
            NpcPresetStorage.apply(player.serverLevel(), npc, preset);
            context.getSource().sendSuccess(() -> Component.literal("Preset importado como NPC: " + npcId), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            context.getSource().sendFailure(Component.literal(ex.getMessage()));
            return 0;
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("No se pudo importar el preset: " + ex.getMessage()));
            return 0;
        }
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
