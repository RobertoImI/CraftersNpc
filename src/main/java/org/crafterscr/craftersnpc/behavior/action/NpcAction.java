package org.crafterscr.craftersnpc.behavior.action;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

import java.util.List;
import java.util.Map;

/** A route-point behavior with an explicit start/tick/finish lifecycle. */
public interface NpcAction {
    default void start(CnpcEntity npc, Map<String, String> parameters) {
    }

    default void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
    }

    default void finish(CnpcEntity npc, Map<String, String> parameters) {
    }

    /** Short explanation shown in command completion. */
    default String description() {
        return "Acción de punto de ruta";
    }

    /** Ready-to-use parameter examples shown in command completion. */
    default List<String> parameterSuggestions() {
        return List.of();
    }
}
