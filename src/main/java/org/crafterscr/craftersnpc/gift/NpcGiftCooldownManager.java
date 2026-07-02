package org.crafterscr.craftersnpc.gift;

import java.util.*;

public final class NpcGiftCooldownManager {
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private NpcGiftCooldownManager() {}
    public static boolean isCoolingDown(UUID npcUuid) { return COOLDOWNS.getOrDefault(npcUuid, 0L) > System.currentTimeMillis(); }
    public static void start(UUID npcUuid, int seconds) { if (seconds > 0) COOLDOWNS.put(npcUuid, System.currentTimeMillis() + seconds * 1000L); }
}
