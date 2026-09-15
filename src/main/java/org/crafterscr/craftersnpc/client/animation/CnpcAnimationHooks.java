package org.crafterscr.craftersnpc.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

public final class CnpcAnimationHooks {

    private CnpcAnimationHooks() {
    }

    @FunctionalInterface
    public interface ModelAnimationHook {

        void apply(
                CnpcEntity entity,
                PlayerModel<CnpcEntity> model,
                float ageInTicks
        );
    }

    @FunctionalInterface
    public interface RootAnimationHook {

        void apply(
                CnpcEntity entity,
                float partialTicks,
                PoseStack poseStack
        );
    }

    /**
     * Hook utilizado por la capa de objetos en mano.
     *
     * <p>Se mantiene en CraftersNpc como una interfaz neutral para que el mod
     * base no dependa de PlayerAnimator. CraftersNpcAnimations registra la
     * implementación real cuando está instalado.</p>
     */
    @FunctionalInterface
    public interface HeldItemHook {

        void apply(
                CnpcEntity entity,
                HumanoidArm arm,
                PoseStack poseStack
        );
    }

    /*
     * Por defecto no hacen absolutamente nada.
     *
     * Así CraftersNpc puede funcionar sin
     * CraftersNpcAnimations instalado.
     */
    private static ModelAnimationHook modelHook =
            (entity, model, ageInTicks) -> {
            };

    private static RootAnimationHook rootHook =
            (entity, partialTicks, poseStack) -> {
            };

    private static HeldItemHook heldItemBendHook =
            (entity, arm, poseStack) -> {
            };

    private static HeldItemHook heldItemTransformHook =
            (entity, arm, poseStack) -> {
            };

    public static void registerModelHook(
            ModelAnimationHook hook
    ) {

        modelHook =
                hook != null
                        ? hook
                        : (entity, model, ageInTicks) -> {
                };
    }

    public static void registerRootHook(
            RootAnimationHook hook
    ) {

        rootHook =
                hook != null
                        ? hook
                        : (entity, partialTicks, poseStack) -> {
                };
    }

    public static void registerHeldItemBendHook(
            HeldItemHook hook
    ) {

        heldItemBendHook =
                hook != null
                        ? hook
                        : (entity, arm, poseStack) -> {
                };
    }

    public static void registerHeldItemTransformHook(
            HeldItemHook hook
    ) {

        heldItemTransformHook =
                hook != null
                        ? hook
                        : (entity, arm, poseStack) -> {
                };
    }

    public static void applyModelAnimation(
            CnpcEntity entity,
            PlayerModel<CnpcEntity> model,
            float ageInTicks
    ) {

        modelHook.apply(
                entity,
                model,
                ageInTicks
        );
    }

    public static void applyRootAnimation(
            CnpcEntity entity,
            float partialTicks,
            PoseStack poseStack
    ) {

        rootHook.apply(
                entity,
                partialTicks,
                poseStack
        );
    }

    public static void applyHeldItemBend(
            CnpcEntity entity,
            HumanoidArm arm,
            PoseStack poseStack
    ) {

        heldItemBendHook.apply(
                entity,
                arm,
                poseStack
        );
    }

    public static void applyHeldItemTransform(
            CnpcEntity entity,
            HumanoidArm arm,
            PoseStack poseStack
    ) {

        heldItemTransformHook.apply(
                entity,
                arm,
                poseStack
        );
    }
}
