package org.crafterscr.craftersnpc.entity.ai;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

/**
 * Dedicated route-behavior component for CNPCs.
 *
 * <p>The controller is intentionally thin while the route state is migrated out
 * incrementally: it owns the route tick entry point and keeps CnpcEntity focused
 * on entity lifecycle, synchronization, persistence, and high-level orchestration.</p>
 */
public class NpcRouteController {
    private final CnpcEntity npc;

    public NpcRouteController(CnpcEntity npc) {
        this.npc = npc;
    }

    public void tick() {
        npc.tickRouteInternal();
    }

    public void reengageNavigation() {
        npc.reengageRouteNavigation();
    }

    public void stopForReaction() {
        npc.stopRouteForReaction();
    }
}
