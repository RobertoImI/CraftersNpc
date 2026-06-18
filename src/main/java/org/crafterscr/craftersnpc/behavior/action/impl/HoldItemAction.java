package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

public final class HoldItemAction implements NpcAction {
    @Override
    public String description() {
        return "Sostiene un objeto; keep=true permite llevarlo al siguiente punto";
    }

    @Override
    public java.util.List<String> parameterSuggestions() {
        return java.util.List.of(
            "item=minecraft:oak_log,keep=true",
            "item=minecraft:spruce_log,keep=true",
            "item=minecraft:diamond,keep=true",
            "item=minecraft:iron_ingot,keep=true",
            "item=minecraft:gold_ingot,keep=true",
            "item=minecraft:coal,keep=true",
            "item=minecraft:cobblestone,keep=true",
            "item=minecraft:bricks,keep=true",
            "item=minecraft:wheat,keep=true",
            "item=minecraft:hay_block,keep=true",
            "item=minecraft:chest,keep=true",
            "item=minecraft:barrel,keep=true",
            "item=minecraft:book,keep=true",
            "item=minecraft:map,keep=true",
            "item=minecraft:lantern,keep=true",
            "item=minecraft:iron_sword,keep=true",
            "item=minecraft:iron_pickaxe,keep=true",
            "item=minecraft:apple"
        );
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
        if (!ActionParameters.bool(parameters, "keep", false)) {
            npc.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }
}
