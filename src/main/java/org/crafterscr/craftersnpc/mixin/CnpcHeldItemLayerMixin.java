package org.crafterscr.craftersnpc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersnpc.client.animation.CnpcAnimationHooks;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extiende la corrección que PlayerAnimator hace normalmente para jugadores
 * al caso de CnpcEntity.
 *
 * <p>PlayerAnimator solo corrige el objeto en mano cuando la entidad implementa
 * su interfaz IAnimatedPlayer. Nuestros CNPC usan el mismo motor de animación,
 * pero no son Player, por lo que sin estos hooks el brazo sí se anima y el
 * objeto puede quedarse en la posición vanilla.</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class CnpcHeldItemLayerMixin {

    /**
     * Aplica el bend del antebrazo antes de la orientación vanilla del objeto.
     * Esto hace que el item siga físicamente la mano cuando el emote dobla el
     * brazo por el codo.
     */
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 0
            )
    )
    private void craftersnpc$applyAnimatedHandBend(
            LivingEntity livingEntity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (livingEntity instanceof CnpcEntity npc) {
            CnpcAnimationHooks.applyHeldItemBend(npc, arm, poseStack);
        }
    }

    /**
     * Aplica además los canales leftItem/rightItem del emote justo antes de
     * renderizar el objeto. Esto permite que animaciones pensadas para sostener
     * comida, herramientas u otros props controlen su posición y rotación.
     */
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void craftersnpc$applyAnimatedItemTransform(
            LivingEntity livingEntity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (livingEntity instanceof CnpcEntity npc) {
            CnpcAnimationHooks.applyHeldItemTransform(npc, arm, poseStack);
        }
    }
}
