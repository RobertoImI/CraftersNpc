package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.world.InteractionHand;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class WorkAction implements NpcAction {
    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        npc.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
        int interval = Math.max(1, ActionParameters.integer(parameters, "interval", 20));
        if (elapsedTicks % interval == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }
}
