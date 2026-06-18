package org.crafterscr.craftersnpc.behavior.action.impl;

import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class LookAction implements NpcAction {
    @Override
    public String description() {
        return "Mira fijamente hacia una dirección durante la espera";
    }

    @Override
    public java.util.List<String> parameterSuggestions() {
        return java.util.List.of("yaw=0,pitch=0", "yaw=90,pitch=0", "yaw=180,pitch=0", "yaw=-90,pitch=0");
    }

    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        float yaw = (float) ActionParameters.decimal(parameters, "yaw", npc.getYRot());
        float pitch = (float) ActionParameters.decimal(parameters, "pitch", npc.getXRot());
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setXRot(pitch);
    }

    @Override
    public void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
        start(npc, parameters);
    }
}
