package org.crafterscr.craftersnpc.behavior.action.impl;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.behavior.action.NpcAction;

import java.util.Map;

/** Clears the carried item, allowing routes to model depositing or storing cargo. */
public final class PutAwayAction implements NpcAction {
    @Override
    public String description() {
        return "Guarda o deposita el objeto que llevaba en la mano";
    }

    @Override
    public void start(CnpcEntity npc, Map<String, String> parameters) {
        npc.stopUsingItem();
        npc.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
}
