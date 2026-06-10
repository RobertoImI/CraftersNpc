package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class HoldItemAction implements NpcAction {
    @Override
    public String description() {
        return "Saca y sostiene un objeto durante la espera del punto";
    }

    @Override
    public java.util.List<String> parameterSuggestions() {
        return java.util.List.of("item=minecraft:apple", "item=minecraft:diamond", "item=minecraft:iron_sword");
    }

    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        ResourceLocation id = ResourceLocation.tryParse(parameters.getOrDefault("item", "minecraft:apple"));
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            npc.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        }
    }

    @Override
    public void finish(CnpcEntity npc, Map<String, String> parameters) {
        npc.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
}
