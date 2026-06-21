package org.crafterscr.craftersnpc.client;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public final class ClientReputationIndicators {
    private static final Map<Integer, Indicator> INDICATORS = new HashMap<>();

    private ClientReputationIndicators() {
    }

    public static void show(int entityId, int reputationDelta, int currentReputation, int durationTicks) {
        if (reputationDelta == 0 || durationTicks <= 0) {
            INDICATORS.remove(entityId);
            return;
        }
        INDICATORS.put(entityId, new Indicator(reputationDelta, currentReputation, durationTicks, currentGameTime()));
    }

    public static Optional<Indicator> get(int entityId) {
        Indicator indicator = INDICATORS.get(entityId);
        if (indicator == null) {
            return Optional.empty();
        }
        if (indicator.remainingTicks() <= 0) {
            INDICATORS.remove(entityId);
            return Optional.empty();
        }
        return Optional.of(indicator);
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, Indicator>> iterator = INDICATORS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().remainingTicks() <= 0) {
                iterator.remove();
            }
        }
    }

    private static long currentGameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    public record Indicator(int reputationDelta, int currentReputation, int durationTicks, long startedGameTime) {
        public int remainingTicks() {
            long elapsedTicks = Math.max(0L, currentGameTime() - startedGameTime);
            return Math.max(0, durationTicks - (int) elapsedTicks);
        }
    }
}
