package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class SayAction implements NpcAction {
    @Override
    public String description() {
        return "Envía un mensaje al llegar al punto";
    }

    @Override
    public java.util.List<String> parameterSuggestions() {
        return java.util.List.of("message=Hola", "message=Bienvenido");
    }

    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        String message = parameters.getOrDefault("message", parameters.getOrDefault("text", "Hola"));
        if (!message.isBlank() && npc.level() instanceof ServerLevel level) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<" + npc.getNpcId() + "> " + message), false);
        }
    }
}
