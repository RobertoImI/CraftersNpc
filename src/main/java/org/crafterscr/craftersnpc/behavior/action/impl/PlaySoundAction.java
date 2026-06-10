package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class PlaySoundAction implements NpcAction {
    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        ResourceLocation id = ResourceLocation.tryParse(parameters.getOrDefault("sound", ""));
        if (id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            float volume = (float) ActionParameters.decimal(parameters, "volume", 1.0D);
            float pitch = (float) ActionParameters.decimal(parameters, "pitch", 1.0D);
            npc.playSound(BuiltInRegistries.SOUND_EVENT.get(id), volume, pitch);
        }
    }
}
