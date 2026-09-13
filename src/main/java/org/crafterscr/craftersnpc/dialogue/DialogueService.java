package org.crafterscr.craftersnpc.dialogue;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
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
    private static final String PLAYER_PLACEHOLDER = "@player";

    private DialogueService() {
    }

    public static boolean startConversation(CnpcEntity npc, ServerPlayer player) {
        List<DialogueEntry> entries = availableEntries(npc);
        if (entries.isEmpty()) {
            return false;
        }

        int phraseIndex = selectPhraseIndex(npc, entries);
        DialogueEntry entry = entries.get(phraseIndex);

        /*
         * Conservamos el texto original para el historial/anti-repetición.
         * La sustitución se hace únicamente sobre la copia que verá el
         * jugador, por lo que un mismo diálogo funciona para todos.
         */
        String displayText = resolvePlaceholders(entry.text(), player);

        npc.markDialogueUsed(phraseIndex, entry.text());
        npc.startDialogue(displayText, durationTicks(displayText), player);
        return true;
    }

    /**
     * Sustituye placeholders dinámicos del diálogo sin modificar el texto
     * almacenado en el NPC o en su banco de diálogos.
     *
     * Ejemplo:
     * "Hola @player, bienvenido" -> "Hola Roberto, bienvenido"
     */
    public static String resolvePlaceholders(String text, ServerPlayer player) {
        if (text == null || text.isEmpty() || player == null) {
            return text == null ? "" : text;
        }

        return text.replace(PLAYER_PLACEHOLDER, player.getGameProfile().getName());
    }

    private static List<DialogueEntry> availableEntries(CnpcEntity npc) {
        List<DialogueEntry> entries = new ArrayList<>(npc.getDialogueEntries());
        if (!npc.getDialogueBankId().isBlank() && npc.level() instanceof ServerLevel serverLevel) {
            entries.addAll(DialogueBankStorage.get(serverLevel).getBank(npc.getDialogueBankId()));
        }
        return entries;
    }

    private static int selectPhraseIndex(CnpcEntity npc, List<DialogueEntry> entries) {
        if (entries.size() == 1) {
            return 0;
        }

        int selected = npc.getRandom().nextInt(entries.size());
        if (selected == npc.getLastDialoguePhraseIndex() || entries.get(selected).text().equals(npc.getLastDialogueText())) {
            selected = (selected + 1 + npc.getRandom().nextInt(entries.size() - 1)) % entries.size();
        }
        return selected;
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
