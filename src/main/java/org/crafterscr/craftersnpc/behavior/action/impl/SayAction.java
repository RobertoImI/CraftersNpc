package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class SayAction implements NpcAction {
    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        String message = parameters.getOrDefault("message", parameters.getOrDefault("text", ""));
        if (!message.isBlank() && npc.level() instanceof ServerLevel level) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<" + npc.getNpcId() + "> " + message), false);
        }
    }
}
