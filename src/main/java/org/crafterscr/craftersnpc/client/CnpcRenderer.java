package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class CnpcRenderer extends HumanoidMobRenderer<CnpcEntity, PlayerModel<CnpcEntity>> {
    private static final double MAX_DIALOGUE_DISTANCE_SQR = 32.0D * 32.0D;
    private static final int MAX_DIALOGUE_LINE_WIDTH = 200;
    private static final int DIALOGUE_LINE_SPACING = 2;
    private static final float DIALOGUE_SCALE = 0.025F;
    private static final int REPUTATION_INDICATOR_DURATION_TICKS = 40;
    private static final double REPUTATION_INDICATOR_RISE = 0.45D;
    private final PlayerModel<CnpcEntity> wideModel;
    private final PlayerModel<CnpcEntity> slimModel;

    public CnpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(CnpcEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = entity.isSlimModel() ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        renderDialogue(entity, poseStack, buffer, packedLight);
        renderReputationIndicator(entity, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderDialogue(CnpcEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String text = entity.getDialogueText();
        Minecraft minecraft = Minecraft.getInstance();
        if (text.isEmpty() || entity.getDialogueTicks() <= 0 || minecraft.player == null
            || entity.distanceToSqr(minecraft.player) > MAX_DIALOGUE_DISTANCE_SQR || entity.isInvisibleTo(minecraft.player)) {
            return;
        }

        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(Component.literal(text), MAX_DIALOGUE_LINE_WIDTH);
        int lineStep = font.lineHeight + DIALOGUE_LINE_SPACING;
        double paragraphLift = Math.max(0, lines.size() - 1) * lineStep * DIALOGUE_SCALE;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + (entity.hasCustomName() ? 0.75D : 0.5D) + paragraphLift, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(DIALOGUE_SCALE, -DIALOGUE_SCALE, DIALOGUE_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            float x = -font.width(line) / 2.0F;
            float y = index * lineStep;
            // SEE_THROUGH prevents the NPC model from depth-occluding parts of its own dialogue.
            font.drawInBatch(line, x, y, 0xFFFFFFFF, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0x60000000, packedLight);
        }
        poseStack.popPose();
    }

    private void renderReputationIndicator(CnpcEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        int delta = entity.getReputationDeltaIndicator();
        int ticks = entity.getReputationIndicatorTicks();
        if (delta == 0 || ticks <= 0 || minecraft.player == null
            || entity.distanceToSqr(minecraft.player) > MAX_DIALOGUE_DISTANCE_SQR || entity.isInvisibleTo(minecraft.player)) {
            return;
        }

        Font font = getFont();
        String arrow = delta > 0 ? "↑" : "↓";
        int color = delta > 0 ? 0x55FF55 : 0xFF5555;
        float remainingPercent = Math.min(1.0F, Math.max(0.0F, (ticks - partialTicks) / REPUTATION_INDICATOR_DURATION_TICKS));
        double upwardOffset = (1.0F - remainingPercent) * REPUTATION_INDICATOR_RISE;

        String dialogueText = entity.getDialogueText();
        int dialogueTicks = entity.getDialogueTicks();
        int dialogueLineCount = dialogueText.isEmpty() || dialogueTicks <= 0
            ? 0
            : font.split(Component.literal(dialogueText), MAX_DIALOGUE_LINE_WIDTH).size();
        int lineStep = font.lineHeight + DIALOGUE_LINE_SPACING;
        double paragraphLift = Math.max(0, dialogueLineCount - 1) * lineStep * DIALOGUE_SCALE;
        double nameLift = entity.hasCustomName() ? 0.75D : 0.5D;
        double dialogueLift = dialogueLineCount > 0 ? (font.lineHeight + DIALOGUE_LINE_SPACING) * DIALOGUE_SCALE : 0.0D;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + nameLift + paragraphLift + dialogueLift + upwardOffset, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(DIALOGUE_SCALE, -DIALOGUE_SCALE, DIALOGUE_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(arrow, -font.width(arrow) / 2.0F, 0.0F, color, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0x60000000, packedLight);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CnpcEntity entity) {
        return SkinTextureManager.resolveTexture(entity.getSkinId());
    }
}
