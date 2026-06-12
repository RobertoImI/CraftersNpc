package org.crafterscr.craftersnpc;

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

public class CnpcRenderer extends HumanoidMobRenderer<CnpcEntity, PlayerModel<CnpcEntity>> {
    private static final double MAX_DIALOGUE_DISTANCE_SQR = 32.0D * 32.0D;
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
    }

    private void renderDialogue(CnpcEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String text = entity.getDialogueText();
        Minecraft minecraft = Minecraft.getInstance();
        if (text.isEmpty() || entity.getDialogueTicks() <= 0 || minecraft.player == null
            || entity.distanceToSqr(minecraft.player) > MAX_DIALOGUE_DISTANCE_SQR || entity.isInvisibleTo(minecraft.player)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + (entity.hasCustomName() ? 0.75D : 0.5D), 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        Font font = getFont();
        Component component = Component.literal(text);
        float x = -font.width(component) / 2.0F;
        font.drawInBatch(component, x, 0.0F, 0xFFFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, 0x60000000, packedLight);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CnpcEntity entity) {
        return SkinTextureManager.resolveTexture(entity.getSkinId());
    }
}
