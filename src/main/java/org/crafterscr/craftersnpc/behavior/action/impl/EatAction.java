package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersnpc.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.ActionParameters;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.List;
import java.util.Map;

public final class EatAction implements NpcAction {
    @Override
    public String description() {
        return "Come o bebe durante la espera; keep=true conserva el objeto después";
    }

    @Override
    public List<String> parameterSuggestions() {
        return List.of(
            "item=minecraft:apple",
            "item=minecraft:bread",
            "item=minecraft:baked_potato",
            "item=minecraft:carrot",
            "item=minecraft:golden_carrot",
            "item=minecraft:cooked_beef",
            "item=minecraft:cooked_chicken",
            "item=minecraft:cooked_porkchop",
            "item=minecraft:cooked_salmon",
            "item=minecraft:pumpkin_pie",
            "item=minecraft:cookie",
            "item=minecraft:melon_slice",
            "item=minecraft:sweet_berries",
            "item=minecraft:honey_bottle",
            "item=minecraft:potion",
            "item=minecraft:milk_bucket",
            "item=minecraft:apple,keep=true"
        );
    }

    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        equipFood(npc, parameters);
        npc.startUsingItem(InteractionHand.MAIN_HAND);
    }

    @Override
    public void tick(CnpcEntity npc, Map<String, String> parameters, int elapsedTicks) {
        if (!npc.isUsingItem()) {
            equipFood(npc, parameters);
            npc.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void finish(CnpcEntity npc, Map<String, String> parameters) {
        npc.stopUsingItem();
        if (!ActionParameters.bool(parameters, "keep", false)) {
            npc.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void equipFood(CnpcEntity npc, Map<String, String> parameters) {
        ResourceLocation id = ResourceLocation.tryParse(parameters.getOrDefault("item", "minecraft:apple"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            id = ResourceLocation.withDefaultNamespace("apple");
        }
        npc.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }
}
