package org.crafterscr.craftersnpc.behavior.action;

import org.crafterscr.craftersnpc.behavior.action.impl.HoldItemAction;
import org.crafterscr.craftersnpc.behavior.action.impl.LookAction;
import org.crafterscr.craftersnpc.behavior.action.impl.PlaySoundAction;
import org.crafterscr.craftersnpc.behavior.action.impl.SayAction;
import org.crafterscr.craftersnpc.behavior.action.impl.SitAction;
import org.crafterscr.craftersnpc.behavior.action.impl.WorkAction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NpcActionRegistry {
    private static final Map<String, NpcAction> ACTIONS = new LinkedHashMap<>();

    static {
        register("look", new LookAction());
        register("say", new SayAction());
        register("hold_item", new HoldItemAction());
        register("sit", new SitAction());
        register("work", new WorkAction());
        register("play_sound", new PlaySoundAction());
    }

    private NpcActionRegistry() {
    }

    public static void register(String id, NpcAction action) {
        ACTIONS.put(normalize(id), action);
    }

    public static Optional<NpcAction> find(String id) {
        return Optional.ofNullable(ACTIONS.get(normalize(id)));
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(ACTIONS.keySet());
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
