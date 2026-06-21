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

final class CnpcCommandUtils {
    private static final SimpleCommandExceptionType MUST_LOOK_CNPC = new SimpleCommandExceptionType(Component.literal("Debes mirar un CNPC a menos de 8 bloques."));
    private static final SimpleCommandExceptionType WRONG_ENTITY = new SimpleCommandExceptionType(Component.literal("La entidad observada no es un CNPC."));

    private CnpcCommandUtils() {}

    static Optional<CnpcEntity> findNpc(CommandContext<CommandSourceStack> context, String npcId) throws CommandSyntaxException {
        return NpcRegistry.findById(context.getSource().getPlayerOrException().getServer(), npcId);
    }

    static String formatScheduleEntry(NpcScheduleEntry entry) {
        return entry.startTime() + "-" + entry.endTime() + " -> " + entry.routeId();
    }
    static CnpcEntity requireLookedNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntityHitResult hitResult = raycastEntity(player, 8.0D)
                .orElseThrow(MUST_LOOK_CNPC::create);

        Entity entity = hitResult.getEntity();
        if (!(entity instanceof CnpcEntity npc)) {
            throw WRONG_ENTITY.create();
        }
        return npc;
    }

    static Optional<EntityHitResult> raycastEntity(ServerPlayer player, double maxDistance) {
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
