package org.crafterscr.craftersnpc.reputation;

import net.minecraft.util.Mth;

/** Shared reputation constants and helpers for NPC/player relationships. */
public final class NpcReputation {
    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final int NEUTRAL = 0;
    public static final int FRIENDLY_THRESHOLD = 25;
    public static final int HOSTILE_THRESHOLD = -25;
    public static final int ANNOYED_THRESHOLD = -10;
    public static final int PLAYER_ATTACK_PENALTY = -10;
    public static final int DIALOGUE_REWARD = 1;
    public static final int DIALOGUE_REWARD_COOLDOWN_TICKS = 20 * 60 * 5;

    public static final String CATEGORY_FRIENDLY = "amistad";
    public static final String CATEGORY_NEUTRAL = "neutral";
    public static final String CATEGORY_HOSTILE = "hostil";
    public static final String CATEGORY_ANNOYED = "molesto";
    public static final String CATEGORY_FORGIVENESS = "perdon";

    private NpcReputation() {
    }

    public static int clamp(int reputation) {
        return Mth.clamp(reputation, MIN, MAX);
    }

    public static boolean isFriendly(int reputation) {
        return reputation >= FRIENDLY_THRESHOLD;
    }

    public static boolean isHostile(int reputation) {
        return reputation <= HOSTILE_THRESHOLD;
    }
}
