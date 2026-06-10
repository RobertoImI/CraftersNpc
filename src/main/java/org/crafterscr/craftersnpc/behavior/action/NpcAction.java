package org.crafterscr.craftersnpc.behavior.action;

import org.crafterscr.craftersnpc.CnpcEntity;

import java.util.Map;

/** A route-point behavior with an explicit start/tick/finish lifecycle. */
public interface NpcAction {
    default void start(CnpcEntity npc, Map<String, String> parameters) {
    }

    default void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
    }

    default void finish(CnpcEntity npc, Map<String, String> parameters) {
    }
}
