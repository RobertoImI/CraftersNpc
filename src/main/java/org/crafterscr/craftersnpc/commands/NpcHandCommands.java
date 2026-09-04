package org.crafterscr.craftersnpc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcRegistry;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NpcHandCommands {

    private NpcHandCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {

        return Commands.literal("hand")

                .then(
                        Commands.argument(
                                        "npcId",
                                        StringArgumentType.word()
                                )
                                .suggests(
                                        CnpcCommandSuggestions::suggestNpcIds
                                )

                                /*
                                 * MAIN HAND
                                 */
                                .then(
                                        Commands.literal("main")

                                                .then(
                                                        Commands.literal("clear")
                                                                .executes(
                                                                        context ->
                                                                                clearHand(
                                                                                        context,
                                                                                        InteractionHand.MAIN_HAND
                                                                                )
                                                                )
                                                )

                                                .then(
                                                        Commands.argument(
                                                                        "item",
                                                                        StringArgumentType.greedyString()
                                                                )
                                                                .suggests(
                                                                        NpcHandCommands::suggestItems
                                                                )
                                                                .executes(
                                                                        context ->
                                                                                setHand(
                                                                                        context,
                                                                                        InteractionHand.MAIN_HAND
                                                                                )
                                                                )
                                                )
                                )

                                /*
                                 * OFF HAND
                                 */
                                .then(
                                        Commands.literal("off")

                                                .then(
                                                        Commands.literal("clear")
                                                                .executes(
                                                                        context ->
                                                                                clearHand(
                                                                                        context,
                                                                                        InteractionHand.OFF_HAND
                                                                                )
                                                                )
                                                )

                                                .then(
                                                        Commands.argument(
                                                                        "item",
                                                                        StringArgumentType.greedyString()
                                                                )
                                                                .suggests(
                                                                        NpcHandCommands::suggestItems
                                                                )
                                                                .executes(
                                                                        context ->
                                                                                setHand(
                                                                                        context,
                                                                                        InteractionHand.OFF_HAND
                                                                                )
                                                                )
                                                )
                                )
                );
    }

    private static int setHand(
            CommandContext<CommandSourceStack> context,
            InteractionHand hand
    ) throws CommandSyntaxException {

        ServerPlayer player =
                context.getSource()
                        .getPlayerOrException();

        String npcId =
                StringArgumentType.getString(
                        context,
                        "npcId"
                );

        String itemId =
                StringArgumentType.getString(
                        context,
                        "item"
                );

        Optional<CnpcEntity> npcOptional =
                NpcRegistry.findById(
                        player.getServer(),
                        npcId
                );

        if (npcOptional.isEmpty()) {

            context.getSource()
                    .sendFailure(
                            Component.literal(
                                    "NPC no encontrado: "
                                            + npcId
                            )
                    );

            return 0;
        }

        ResourceLocation resourceLocation =
                ResourceLocation.tryParse(
                        itemId
                );

        if (
                resourceLocation == null
                        || !BuiltInRegistries.ITEM.containsKey(
                        resourceLocation
                )
        ) {

            context.getSource()
                    .sendFailure(
                            Component.literal(
                                    "Item no válido: "
                                            + itemId
                            )
                    );

            return 0;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        resourceLocation
                );

        CnpcEntity npc =
                npcOptional.get();

        npc.setItemInHand(
                hand,
                new ItemStack(
                        item
                )
        );

        String handName =
                hand == InteractionHand.MAIN_HAND
                        ? "mano principal"
                        : "mano secundaria";

        context.getSource()
                .sendSuccess(
                        () -> Component.literal(
                                npcId
                                        + " ahora sostiene "
                                        + itemId
                                        + " en "
                                        + handName
                        ),
                        true
                );

        return 1;
    }

    private static int clearHand(
            CommandContext<CommandSourceStack> context,
            InteractionHand hand
    ) throws CommandSyntaxException {

        ServerPlayer player =
                context.getSource()
                        .getPlayerOrException();

        String npcId =
                StringArgumentType.getString(
                        context,
                        "npcId"
                );

        Optional<CnpcEntity> npcOptional =
                NpcRegistry.findById(
                        player.getServer(),
                        npcId
                );

        if (npcOptional.isEmpty()) {

            context.getSource()
                    .sendFailure(
                            Component.literal(
                                    "NPC no encontrado: "
                                            + npcId
                            )
                    );

            return 0;
        }

        CnpcEntity npc =
                npcOptional.get();

        npc.setItemInHand(
                hand,
                ItemStack.EMPTY
        );

        String handName =
                hand == InteractionHand.MAIN_HAND
                        ? "mano principal"
                        : "mano secundaria";

        context.getSource()
                .sendSuccess(
                        () -> Component.literal(
                                "Objeto eliminado de la "
                                        + handName
                                        + " de "
                                        + npcId
                        ),
                        true
                );

        return 1;
    }

    private static CompletableFuture<
            com.mojang.brigadier.suggestion.Suggestions
            > suggestItems(
            CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {

        return SharedSuggestionProvider.suggest(
                BuiltInRegistries.ITEM
                        .keySet()
                        .stream()
                        .map(
                                ResourceLocation::toString
                        ),
                builder
        );
    }
}