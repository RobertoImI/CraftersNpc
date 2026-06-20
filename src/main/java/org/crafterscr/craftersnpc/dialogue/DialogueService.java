package org.crafterscr.craftersnpc.dialogue;

import org.crafterscr.craftersnpc.entity.CnpcEntity;

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

    private DialogueService() {
    }

    public static boolean startConversation(CnpcEntity npc, ServerPlayer player) {
        return startConversation(npc, player, DialogueContext.generic());
    }

    public static boolean startConversation(CnpcEntity npc, ServerPlayer player, DialogueContext context) {
        List<DialogueEntry> entries = npc.getDialogueEntries();
        if (entries.isEmpty()) {
            return false;
        }

        int phraseIndex = selectPhraseIndex(npc, entries, context);
        if (phraseIndex < 0) {
            return false;
        }

        DialogueEntry entry = entries.get(phraseIndex);
        int duration = durationTicks(entry.text());
        npc.markDialogueEntryUsed(phraseIndex);
        npc.startDialogue(entry.text(), duration, player);
        return true;
    }

    private static int selectPhraseIndex(CnpcEntity npc, List<DialogueEntry> entries, DialogueContext context) {
        CandidatePool exactCandidates = collectCandidates(npc, entries, context, false);
        CandidatePool candidates = exactCandidates.isEmpty()
            ? collectCandidates(npc, entries, context, true)
            : exactCandidates;
        if (candidates.isEmpty()) {
            return -1;
        }
        return selectWeightedCandidate(npc, entries, candidates);
    }

    private static CandidatePool collectCandidates(CnpcEntity npc, List<DialogueEntry> entries, DialogueContext context, boolean genericOnly) {
        List<Integer> candidates = new ArrayList<>();
        int totalWeight = 0;
        int lastPhraseIndex = npc.getLastDialoguePhraseIndex();
        for (int index = 0; index < entries.size(); index++) {
            DialogueEntry entry = entries.get(index);
            if (index == lastPhraseIndex && entries.size() > 1) {
                continue;
            }
            if (!matchesSelectionPhase(entry, context, genericOnly)) {
                continue;
            }
            candidates.add(index);
            totalWeight += entry.weight();
        }
        return new CandidatePool(candidates, totalWeight);
    }

    private static boolean matchesSelectionPhase(DialogueEntry entry, DialogueContext context, boolean genericOnly) {
        if (genericOnly) {
            return context == null || context.matchesGenericCategory(entry.category());
        }
        return context == null || context.matchesCategory(entry.category());
    }

    private static int selectWeightedCandidate(CnpcEntity npc, List<DialogueEntry> entries, CandidatePool candidates) {
        int selectedWeight = npc.getRandom().nextInt(candidates.totalWeight());
        for (int index : candidates.indices()) {
            selectedWeight -= entries.get(index).weight();
            if (selectedWeight < 0) {
                return index;
            }
        }
        return candidates.indices().get(candidates.indices().size() - 1);
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

    private record CandidatePool(List<Integer> indices, int totalWeight) {
        private boolean isEmpty() {
            return indices.isEmpty();
        }
    }
}
