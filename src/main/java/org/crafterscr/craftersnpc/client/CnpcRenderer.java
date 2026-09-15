package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.client.animation.CnpcAnimationHooks;
import org.crafterscr.craftersnpc.client.animation.CnpcPlayerModel;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;

public class CnpcRenderer
        extends HumanoidMobRenderer<
        CnpcEntity,
        CnpcPlayerModel
        > {

    private static final double MAX_DIALOGUE_DISTANCE_SQR =
            32.0D * 32.0D;

    private static final int MAX_DIALOGUE_LINE_WIDTH =
            200;

    private static final int DIALOGUE_LINE_SPACING =
            2;

    private static final float DIALOGUE_SCALE =
            0.025F;

    private static final int DIALOGUE_BACKGROUND =
            0x60000000;

    private final CnpcPlayerModel wideModel;
    private final CnpcPlayerModel slimModel;

    public CnpcRenderer(
            EntityRendererProvider.Context context
    ) {

        super(
                context,
                new CnpcPlayerModel(
                        context.bakeLayer(
                                ModelLayers.PLAYER
                        ),
                        false
                ),
                0.5F
        );

        this.wideModel =
                this.getModel();

        this.slimModel =
                new CnpcPlayerModel(
                        context.bakeLayer(
                                ModelLayers.PLAYER_SLIM
                        ),
                        true
                );

        this.addLayer(
                new ItemInHandLayer<>(
                        this,
                        context.getItemInHandRenderer()
                )
        );
    }

    @Override
    public void render(
            CnpcEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {

        this.model =
                entity.isSlimModel()
                        ? slimModel
                        : wideModel;

        /*
         * IMPORTANTE:
         *
         * Ya NO aplicamos aquí el root del emote.
         *
         * Debe aplicarse dentro de setupRotations(),
         * exactamente igual que en AnimatedNpcRenderer.
         */
        super.render(
                entity,
                entityYaw,
                partialTicks,
                poseStack,
                buffer,
                packedLight
        );

        /*
         * El diálogo se renderiza aparte y no recibe
         * la transformación del emote.
         */
        renderDialogue(
                entity,
                poseStack,
                buffer
        );
    }

    /*
     * ==========================================
     * ROOT / BODY DE PLAYERANIMATOR
     * ==========================================
     *
     * Minecraft primero aplica sus rotaciones normales.
     * Después el addon puede aplicar la transformación
     * global del emote.
     *
     * Este orden coincide con AnimatedNpcRenderer.
     */
    @Override
    protected void setupRotations(
            CnpcEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale
    ) {

        super.setupRotations(
                entity,
                poseStack,
                bob,
                yBodyRot,
                partialTick,
                scale
        );

        CnpcAnimationHooks.applyRootAnimation(
                entity,
                partialTick,
                poseStack
        );
    }

    private void renderDialogue(
            CnpcEntity entity,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {

        String text =
                entity.getDialogueText();

        Minecraft minecraft =
                Minecraft.getInstance();

        if (text.isEmpty()
                || entity.getDialogueTicks() <= 0
                || minecraft.player == null
                || entity.distanceToSqr(
                minecraft.player
        ) > MAX_DIALOGUE_DISTANCE_SQR
                || entity.isInvisibleTo(
                minecraft.player
        )) {

            return;
        }

        Font font =
                getFont();

        List<FormattedCharSequence> lines =
                font.split(
                        Component.literal(
                                text
                        ),
                        MAX_DIALOGUE_LINE_WIDTH
                );

        int lineStep =
                font.lineHeight
                        + DIALOGUE_LINE_SPACING;

        double paragraphLift =
                Math.max(
                        0,
                        lines.size() - 1
                )
                        * lineStep
                        * DIALOGUE_SCALE;

        poseStack.pushPose();

        poseStack.translate(
                0.0D,
                entity.getBbHeight()
                        + (
                        entity.hasCustomName()
                                ? 0.75D
                                : 0.5D
                )
                        + paragraphLift,
                0.0D
        );

        poseStack.mulPose(
                entityRenderDispatcher
                        .cameraOrientation()
        );

        poseStack.scale(
                DIALOGUE_SCALE,
                -DIALOGUE_SCALE,
                DIALOGUE_SCALE
        );

        Matrix4f matrix =
                poseStack.last()
                        .pose();

        for (
                int index = 0;
                index < lines.size();
                index++
        ) {

            FormattedCharSequence line =
                    lines.get(
                            index
                    );

            float x =
                    -font.width(
                            line
                    ) / 2.0F;

            float y =
                    index * lineStep;

            /*
             * Primera pasada: SEE_THROUGH y FULL_BRIGHT.
             *
             * Esta es la que garantiza que la frase completa permanezca
             * impresa aunque la cámara pase por un ángulo donde el propio NPC,
             * su objeto o parte del escenario quede entre la cámara y el texto.
             * No dependemos del depth buffer para decidir qué letras sobreviven.
             */
            font.drawInBatch(
                    line,
                    x,
                    y,
                    0xFFFFFFFF,
                    false,
                    matrix,
                    buffer,
                    Font.DisplayMode.SEE_THROUGH,
                    DIALOGUE_BACKGROUND,
                    LightTexture.FULL_BRIGHT
            );

            /*
             * Segunda pasada: NORMAL y FULL_BRIGHT.
             *
             * Cuando el texto está realmente visible frente a la geometría,
             * esta pasada deja los glifos completamente sólidos. Combinada con
             * la pasada anterior reproduce la estrategia de los name-tags de
             * Minecraft y evita el efecto de letras que desaparecen al girar.
             */
            font.drawInBatch(
                    line,
                    x,
                    y,
                    0xFFFFFFFF,
                    false,
                    matrix,
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    LightTexture.FULL_BRIGHT
            );
        }

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(
            CnpcEntity entity
    ) {

        return entity.getSkinUrl()
                .isBlank()
                ? SkinTextureManager.resolveTexture(
                entity.getSkinId()
        )
                : SkinTextureManager.resolveUrlTexture(
                entity.getSkinUrl()
        );
    }
}
