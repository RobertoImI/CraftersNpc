package org.crafterscr.craftersnpc.entity.ai;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class NpcReactionController {
    private final CnpcEntity npc;
    private ReactionState reactionState = ReactionState.NONE;
    private UUID reactivePlayerUuid;
    private int reactiveTicks;
    private int reactiveAttackCooldown;

    public NpcReactionController(CnpcEntity npc) {
        this.npc = npc;
    }

    public boolean tick() {
        if (reactionState == ReactionState.NONE || reactiveTicks <= 0 || reactivePlayerUuid == null || !(npc.level() instanceof ServerLevel serverLevel)) {
            stop();
            return false;
        }

        Player player = serverLevel.getPlayerByUUID(reactivePlayerUuid);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            stop();
            return false;
        }

        reactiveTicks--;
        if (reactionState == ReactionState.ATTACKING) {
            npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
            npc.getNavigation().moveTo(player, 1.15D);
            if (reactiveAttackCooldown > 0) {
                reactiveAttackCooldown--;
            }
            if (npc.distanceToSqr(player) <= 4.0D && reactiveAttackCooldown <= 0) {
                npc.swing(InteractionHand.MAIN_HAND);
                float attackDamage = (float) npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
                player.hurt(npc.damageSources().mobAttack(npc), attackDamage);
                reactiveAttackCooldown = 15;
            }
        } else if (reactionState == ReactionState.FLEEING) {
            Vec3 away = npc.position().subtract(player.position());
            Vec3 horizontalAway = new Vec3(away.x, 0.0D, away.z);
            if (horizontalAway.lengthSqr() <= 1.0E-4D) {
                horizontalAway = new Vec3((npc.getRandom().nextDouble() - 0.5D) * 2.0D, 0.0D, (npc.getRandom().nextDouble() - 0.5D) * 2.0D);
            }
            Vec3 fleeTarget = npc.position().add(horizontalAway.normalize().scale(6.0D));
            npc.getNavigation().moveTo(fleeTarget.x, npc.position().y, fleeTarget.z, 1.2D);
        }

        if (reactiveTicks <= 0) {
            stop();
            return false;
        }
        return true;
    }

    public void start(Player player) {
        CnpcEntity.Temperament temperament = npc.getTemperament();
        reactionState = switch (temperament) {
            case AGRESIVO -> ReactionState.ATTACKING;
            case ALEATORIO -> npc.getRandom().nextBoolean() ? ReactionState.ATTACKING : ReactionState.FLEEING;
            case PACIFICO -> ReactionState.FLEEING;
        };

        reactivePlayerUuid = player.getUUID();
        reactiveTicks = 20 * 8;
        reactiveAttackCooldown = 0;
        npc.stopRouteForReaction();
    }

    public void stop() {
        if (reactionState == ReactionState.NONE && reactivePlayerUuid == null && reactiveTicks == 0) {
            return;
        }
        reactionState = ReactionState.NONE;
        reactivePlayerUuid = null;
        reactiveTicks = 0;
        reactiveAttackCooldown = 0;
        npc.reengageRouteNavigation();
    }

    public String debugState() {
        return reactionState.toString();
    }

    public int reactiveTicks() {
        return reactiveTicks;
    }

    private enum ReactionState {
        NONE,
        ATTACKING,
        FLEEING
    }
}
