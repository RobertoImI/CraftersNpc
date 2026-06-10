package org.crafterscr.craftersnpc.behavior.action.impl;

import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.List;
import java.util.Map;

public final class LookAroundAction implements NpcAction {
    @Override
    public String description() {
        return "Se queda en el punto mirando a su alrededor";
    }

    @Override
    public List<String> parameterSuggestions() {
        return List.of("interval=40,angle=60", "interval=20,angle=90", "interval=60,angle=45");
    }

    @Override
    public void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
        int interval = Math.max(5, ActionParameters.integer(parameters, "interval", 40));
        float angle = (float) Math.abs(ActionParameters.decimal(parameters, "angle", 60.0D));
        float direction = ((elapsedTicks / interval) & 1) == 0 ? -1.0F : 1.0F;
        float yaw = npc.getYRot() + direction * angle;
        npc.setYHeadRot(yaw);
        npc.setXRot(0.0F);
    }
}
