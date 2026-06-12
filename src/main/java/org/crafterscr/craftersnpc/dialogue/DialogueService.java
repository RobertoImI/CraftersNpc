package org.crafterscr.craftersnpc.dialogue;

import org.crafterscr.craftersnpc.CnpcEntity;

import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Selects and starts the server-authoritative dialogue shown by an NPC. */
public final class DialogueService {
    private static final int TICKS_PER_SECOND = 20;
    private static final int SHORT_TEXT_MAX_WORDS = 10;
    private static final int TWO_LINES_MAX_WORDS = 20;
    private static final int MEDIUM_TEXT_MAX_WORDS = 39;
    private static final int PARAGRAPH_MAX_WORDS = 60;
    private static final int EXTRA_WORDS_PER_STEP = 20;
    private static final int MAX_DURATION_SECONDS = 60;

    private DialogueService() {
    }

    public static boolean startConversation(CnpcEntity npc, ServerPlayer player) {
        List<String> phrases = npc.getDialoguePhrases();
        if (phrases.isEmpty()) {
            return false;
        }

        String phrase = phrases.get(npc.getRandom().nextInt(phrases.size()));
        int duration = durationTicks(phrase);
        npc.startDialogue(phrase, duration, player);
        return true;
    }

    /** Gives players enough reading time based on the phrase's word count. */
    public static int durationTicks(String text) {
        int words = wordCount(text);
        int seconds;
        if (words <= SHORT_TEXT_MAX_WORDS) {
            seconds = 4;
        } else if (words <= TWO_LINES_MAX_WORDS) {
            seconds = 8;
        } else if (words <= MEDIUM_TEXT_MAX_WORDS) {
            seconds = 12;
        } else if (words <= PARAGRAPH_MAX_WORDS) {
            seconds = 18;
        } else {
            int extraSteps = (words - PARAGRAPH_MAX_WORDS + EXTRA_WORDS_PER_STEP - 1) / EXTRA_WORDS_PER_STEP;
            seconds = Math.min(MAX_DURATION_SECONDS, 18 + extraSteps * 4);
        }
        return seconds * TICKS_PER_SECOND;
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }
}
