package org.crafterscr.craftersnpc.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
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
}