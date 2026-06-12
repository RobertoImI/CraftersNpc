package org.crafterscr.craftersnpc.dialogue;

import org.crafterscr.craftersnpc.CnpcEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.List;

/** Selects and starts the server-authoritative dialogue shown by an NPC. */
public final class DialogueService {
    public static final int MIN_DURATION_TICKS = 60;
    public static final int MAX_DURATION_TICKS = 200;

    private DialogueService() {
    }

    public static boolean startConversation(CnpcEntity npc, ServerPlayer player) {
        List<String> phrases = npc.getDialoguePhrases();
        if (phrases.isEmpty()) {
            return false;
        }

        String phrase = phrases.get(npc.getRandom().nextInt(phrases.size()));
        int duration = Mth.clamp(phrase.length() * 3, MIN_DURATION_TICKS, MAX_DURATION_TICKS);
        npc.startDialogue(phrase, duration, player);
        return true;
    }
}
