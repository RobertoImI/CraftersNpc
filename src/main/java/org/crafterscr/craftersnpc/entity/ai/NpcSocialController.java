package org.crafterscr.craftersnpc.entity.ai;

import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcPlayerMemory;
import org.crafterscr.craftersnpc.network.ReputationIndicatorPayload;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcSocialController {
    private final CnpcEntity npc;
    private final Map<UUID, NpcPlayerMemory> playerMemories = new HashMap<>();

    public NpcSocialController(CnpcEntity npc) {
        this.npc = npc;
    }

    public NpcPlayerMemory getOrCreatePlayerMemory(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        return playerMemories.computeIfAbsent(
                playerUuid,
                uuid -> new NpcPlayerMemory(uuid, player.getGameProfile().getName(), npc.level().getGameTime())
        );
    }

    public JsonObject presetConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("exportsPlayerMemories", false);
        return config;
    }

    public int getReputation(ServerPlayer player) {
        return getOrCreatePlayerMemory(player).reputation();
    }

    public int adjustReputation(ServerPlayer player, int delta, org.crafterscr.craftersnpc.reputation.ReputationReason reason) {
        NpcPlayerMemory memory = getOrCreatePlayerMemory(player);
        int previousReputation = memory.reputation();
        int newReputation = memory.adjustReputation(delta);
        int appliedDelta = newReputation - previousReputation;
        if (appliedDelta != 0) {
            sendReputationIndicator(player, appliedDelta, newReputation);
        }
        return newReputation;
    }

    private void sendReputationIndicator(ServerPlayer player, int appliedDelta, int currentReputation) {
        PacketDistributor.sendToPlayer(
                player,
                new ReputationIndicatorPayload(npc.getId(), appliedDelta, currentReputation, CnpcEntity.REPUTATION_INDICATOR_DURATION_TICKS)
        );
    }
}
