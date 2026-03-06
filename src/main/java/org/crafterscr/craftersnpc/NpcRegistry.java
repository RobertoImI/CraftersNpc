package org.crafterscr.craftersnpc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcRegistry {
    private static final Map<String, UUID> NPC_BY_ID = new HashMap<>();
    private static final Map<UUID, String> ID_BY_NPC = new HashMap<>();

    private NpcRegistry() {
    }

    public static void track(CnpcEntity npc) {
        if (npc.getNpcId().isBlank()) {
            return;
        }
        UUID uuid = npc.getUUID();
        String normalizedId = normalize(npc.getNpcId());

        String previousId = ID_BY_NPC.put(uuid, normalizedId);
        if (previousId != null && !previousId.equals(normalizedId)) {
            NPC_BY_ID.remove(previousId, uuid);
        }

        UUID previousUuid = NPC_BY_ID.put(normalizedId, uuid);
        if (previousUuid != null && !previousUuid.equals(uuid)) {
            ID_BY_NPC.remove(previousUuid, normalizedId);
        }
    }

    public static void updateNpcId(CnpcEntity npc, String previousNpcId, String newNpcId) {
        UUID uuid = npc.getUUID();

        if (previousNpcId != null && !previousNpcId.isBlank()) {
            String normalizedPrevious = normalize(previousNpcId);
            NPC_BY_ID.remove(normalizedPrevious, uuid);
            ID_BY_NPC.remove(uuid, normalizedPrevious);
        }

        if (newNpcId != null && !newNpcId.isBlank()) {
            String normalizedNew = normalize(newNpcId);
            ID_BY_NPC.put(uuid, normalizedNew);
            UUID previousUuid = NPC_BY_ID.put(normalizedNew, uuid);
            if (previousUuid != null && !previousUuid.equals(uuid)) {
                ID_BY_NPC.remove(previousUuid, normalizedNew);
            }
        }
    }

    public static void untrack(CnpcEntity npc) {
        UUID uuid = npc.getUUID();
        String npcId = ID_BY_NPC.remove(uuid);
        if (npcId != null) {
            NPC_BY_ID.remove(npcId, uuid);
        }
    }

    public static Optional<CnpcEntity> findById(MinecraftServer server, String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return Optional.empty();
        }

        String normalizedId = normalize(npcId);
        UUID uuid = NPC_BY_ID.get(normalizedId);
        if (uuid == null) {
            return Optional.empty();
        }

        Entity entity = server.getEntity(uuid);
        if (entity instanceof CnpcEntity npc) {
            return Optional.of(npc);
        }

        NPC_BY_ID.remove(normalizedId, uuid);
        ID_BY_NPC.remove(uuid, normalizedId);
        return Optional.empty();
    }

    public static List<String> listNpcIds(MinecraftServer server) {
        List<String> ids = new ArrayList<>(NPC_BY_ID.size());
        for (Map.Entry<String, UUID> entry : NPC_BY_ID.entrySet()) {
            Entity entity = server.getEntity(entry.getValue());
            if (entity instanceof CnpcEntity) {
                ids.add(entry.getKey());
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static String normalize(String npcId) {
        return npcId.toLowerCase(Locale.ROOT);
    }
}
