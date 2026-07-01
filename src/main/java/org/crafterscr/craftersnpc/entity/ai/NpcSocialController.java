package org.crafterscr.craftersnpc.entity.ai;

import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcPlayerMemory;

import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerPlayer;

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
}
