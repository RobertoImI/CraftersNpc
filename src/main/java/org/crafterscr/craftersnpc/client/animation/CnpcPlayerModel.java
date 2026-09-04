package org.crafterscr.craftersnpc.client.animation;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.crafterscr.craftersnpc.entity.CnpcEntity;

public class CnpcPlayerModel
        extends PlayerModel<CnpcEntity> {

    public CnpcPlayerModel(
            ModelPart root,
            boolean slim
    ) {

        super(
                root,
                slim
        );
    }

    @Override
    public void setupAnim(
            CnpcEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {

        /*
         * Primero comportamiento normal vanilla
         * del NPC.
         */
        super.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch
        );

        /*
         * Después permitimos que un addon modifique
         * la pose.
         *
         * Sin addon este método no hace nada.
         */
        CnpcAnimationHooks.applyModelAnimation(
                entity,
                this,
                ageInTicks
        );
    }
}