package org.crafterscr.craftersnpc;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CnpcRenderer extends HumanoidMobRenderer<CnpcEntity, PlayerModel<CnpcEntity>> {
    public CnpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CnpcEntity entity) {
        return SkinTextureManager.resolveTexture(entity.getSkinId());
    }
}
