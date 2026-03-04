package org.crafterscr.craftersnpc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

public final class CnpcCommands {
    private static final SimpleCommandExceptionType MUST_LOOK_CNPC = new SimpleCommandExceptionType(Component.literal("Debes mirar un CNPC a menos de 8 bloques."));
    private static final SimpleCommandExceptionType WRONG_ENTITY = new SimpleCommandExceptionType(Component.literal("La entidad observada no es un CNPC."));

    private CnpcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cnpc")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("create")
                .executes(CnpcCommands::createNpc))
            .then(Commands.literal("skin")
                .then(Commands.argument("skin", StringArgumentType.word())
                    .executes(ctx -> setSkin(ctx, StringArgumentType.getString(ctx, "skin")))))
            .then(Commands.literal("route")
                .then(Commands.literal("add")
                    .then(Commands.argument("waitSeconds", IntegerArgumentType.integer(0, 3600))
                        .executes(ctx -> addWaypoint(ctx, IntegerArgumentType.getInteger(ctx, "waitSeconds")))))
                .then(Commands.literal("clear")
                    .executes(CnpcCommands::clearRoute))
                .then(Commands.literal("start")
                    .executes(ctx -> setRoute(ctx, true)))
                .then(Commands.literal("stop")
                    .executes(ctx -> setRoute(ctx, false)))));
    }

    private static int createNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 spawnPos = player.position();
        CnpcEntity.spawn(player.serverLevel(), spawnPos);
        context.getSource().sendSuccess(() -> Component.literal("CNPC creado."), true);
        return 1;
    }

    private static int setSkin(CommandContext<CommandSourceStack> context, String skin) throws CommandSyntaxException {
        CnpcEntity npc = requireLookedNpc(context);
        npc.setSkinId(skin);
        context.getSource().sendSuccess(() -> Component.literal("Skin del CNPC cambiada a: " + skin), true);
        return 1;
    }

    private static int addWaypoint(CommandContext<CommandSourceStack> context, int waitSeconds) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CnpcEntity npc = requireLookedNpc(context);
        npc.addRoutePoint(player.position(), waitSeconds);
        context.getSource().sendSuccess(() -> Component.literal("Punto agregado. Espera: " + waitSeconds + "s"), true);
        return 1;
    }

    private static int clearRoute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CnpcEntity npc = requireLookedNpc(context);
        npc.clearRoute();
        context.getSource().sendSuccess(() -> Component.literal("Ruta del CNPC limpiada."), true);
        return 1;
    }

    private static int setRoute(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        CnpcEntity npc = requireLookedNpc(context);
        npc.setRouteEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.literal(enabled ? "Ruta iniciada." : "Ruta detenida."), true);
        return 1;
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
