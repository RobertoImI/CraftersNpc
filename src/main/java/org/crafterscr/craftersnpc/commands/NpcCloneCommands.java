package org.crafterscr.craftersnpc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.FixedPositionNpc;
import org.crafterscr.craftersnpc.entity.NpcRegistry;
import org.crafterscr.craftersnpc.preset.NpcPresetStorage;

import java.util.Locale;
import java.util.Optional;

/**
 * Crea una copia completa de un NPC existente en la posición del administrador.
 *
 * <p>La copia reutiliza el NBT propio del NPC, por lo que conserva skin,
 * nombre, diálogos, regalos, objetos equipados, rutas, horarios, estado de
 * animación y demás configuración persistente. Únicamente se genera un nuevo
 * npcId y, si el NPC original era fixed, el nuevo ancla se crea en la posición
 * donde se pega la copia.</p>
 */
final class NpcCloneCommands {
    private static final String FIXED_TAG = "FixedPosition";
    private static final String FIXED_X_TAG = "FixedPositionX";
    private static final String FIXED_Y_TAG = "FixedPositionY";
    private static final String FIXED_Z_TAG = "FixedPositionZ";

    private NpcCloneCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("copy")
                .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests(CnpcCommandSuggestions::suggestNpcIds)
                        .executes(context -> copyNpc(
                                context,
                                StringArgumentType.getString(context, "npcId")
                        )));
    }

    private static int copyNpc(
            CommandContext<CommandSourceStack> context,
            String sourceNpcId
    ) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<CnpcEntity> sourceOptional = NpcRegistry.findById(player.getServer(), sourceNpcId);

        if (sourceOptional.isEmpty()) {
            context.getSource().sendFailure(Component.literal("NPC no encontrado: " + sourceNpcId));
            return 0;
        }

        CnpcEntity source = sourceOptional.get();
        String newNpcId;

        try {
            newNpcId = nextCopyId(player.getServer(), source.getNpcId());
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }

        boolean sourceWasFixed = source instanceof FixedPositionNpc fixedNpc
                && fixedNpc.isFixedPosition();

        CompoundTag snapshot = new CompoundTag();
        source.addAdditionalSaveData(snapshot);

        /*
         * Nunca permitimos que readAdditionalSaveData registre temporalmente
         * el mismo ID del original. La copia nace directamente con su ID único.
         */
        snapshot.putString("NpcId", newNpcId);

        /*
         * Un NPC fixed debe quedar anclado al lugar donde se pega la copia,
         * no a las coordenadas antiguas del NPC original.
         */
        snapshot.putBoolean(FIXED_TAG, false);
        snapshot.remove(FIXED_X_TAG);
        snapshot.remove(FIXED_Y_TAG);
        snapshot.remove(FIXED_Z_TAG);

        CnpcEntity copy = CnpcEntity.spawn(
                player.serverLevel(),
                player.position(),
                newNpcId,
                source.isSlimModel()
        );

        try {
            copy.readAdditionalSaveData(snapshot);

            /*
             * addAdditionalSaveData no representa la posición base de Entity,
             * pero la reafirmamos después de cargar el snapshot para que el
             * comportamiento de "pegar aquí" sea inequívoco.
             */
            copy.moveTo(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    source.getYRot(),
                    source.getXRot()
            );

            if (sourceWasFixed && copy instanceof FixedPositionNpc fixedCopy) {
                fixedCopy.setFixedPosition(true);
            }

            copy.setPersistenceRequired();
        } catch (RuntimeException exception) {
            copy.discard();
            context.getSource().sendFailure(Component.literal(
                    "No se pudo copiar el NPC " + sourceNpcId + ": " + exception.getMessage()
            ));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "NPC copiado: " + source.getNpcId() + " -> " + newNpcId
                                + ". La copia fue pegada en tu posición."
                ),
                true
        );
        return 1;
    }

    private static String nextCopyId(MinecraftServer server, String sourceNpcId) {
        String base = sourceNpcId == null
                ? "npc"
                : sourceNpcId.strip().toLowerCase(Locale.ROOT);

        if (base.isBlank()) {
            base = "npc";
        }

        for (int copyNumber = 1; copyNumber <= 9999; copyNumber++) {
            String suffix = "_copy" + copyNumber;
            int availableBaseLength = NpcPresetStorage.MAX_ID_LENGTH - suffix.length();
            if (availableBaseLength <= 0) {
                break;
            }

            String shortenedBase = base.length() > availableBaseLength
                    ? base.substring(0, availableBaseLength)
                    : base;
            String candidate = shortenedBase + suffix;

            if (NpcRegistry.findById(server, candidate).isEmpty()) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "No se pudo generar un ID libre para la copia de " + sourceNpcId + "."
        );
    }
}
