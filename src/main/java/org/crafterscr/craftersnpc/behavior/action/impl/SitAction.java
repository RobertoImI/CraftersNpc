package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.world.entity.Pose;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class SitAction implements NpcAction {
    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        npc.setPose(Pose.SITTING);
    }

    @Override
    public void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
        npc.setPose(Pose.SITTING);
    }

    @Override
    public void finish(CnpcEntity npc, Map<String, String> parameters) {
        npc.setPose(Pose.STANDING);
    }
}
