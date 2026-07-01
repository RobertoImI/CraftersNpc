package org.crafterscr.craftersnpc;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CraftersNpc.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue MAX_DIALOGUE_DISTANCE = BUILDER
        .comment("Maximum distance, in blocks, from which a player can start or continue dialogue with an NPC.")
        .defineInRange("maxDialogueDistance", 8.0D, 1.0D, 64.0D);

    private static final ModConfigSpec.IntValue BASE_DIALOGUE_DURATION_TICKS = BUILDER
        .comment("Base duration, in ticks, used for dialogue lines without a custom duration.")
        .defineInRange("baseDialogueDurationTicks", 100, 20, 20 * 60 * 10);

    private static final ModConfigSpec.BooleanValue DEFAULT_DAMAGE_ALLOWED = BUILDER
        .comment("Whether NPCs can receive damage by default before per-NPC settings are applied.")
        .define("defaultDamageAllowed", false);

    private static final ModConfigSpec.IntValue MAX_NPCS_PER_WORLD = BUILDER
        .comment("Maximum number of Crafters NPCs allowed in a single world.")
        .defineInRange("maxNpcsPerWorld", 256, 1, 10_000);

    private static final ModConfigSpec.IntValue MAX_NPCS_PER_CHUNK = BUILDER
        .comment("Maximum number of Crafters NPCs allowed in a single chunk.")
        .defineInRange("maxNpcsPerChunk", 16, 1, 256);

    private static final ModConfigSpec.IntValue MAX_ROUTES_PER_WORLD = BUILDER
        .comment("Maximum number of named NPC routes stored per world.")
        .defineInRange("maxRoutesPerWorld", 128, 1, 10_000);

    private static final ModConfigSpec.IntValue MAX_ROUTE_POINTS = BUILDER
        .comment("Maximum number of points allowed in a single NPC route.")
        .defineInRange("maxRoutePoints", 64, 1, 1_024);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double maxDialogueDistance;
    public static int baseDialogueDurationTicks;
    public static boolean defaultDamageAllowed;
    public static int maxNpcsPerWorld;
    public static int maxNpcsPerChunk;
    public static int maxRoutesPerWorld;
    public static int maxRoutePoints;
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxDialogueDistance = MAX_DIALOGUE_DISTANCE.get();
        baseDialogueDurationTicks = BASE_DIALOGUE_DURATION_TICKS.get();
        defaultDamageAllowed = DEFAULT_DAMAGE_ALLOWED.get();
        maxNpcsPerWorld = MAX_NPCS_PER_WORLD.get();
        maxNpcsPerChunk = MAX_NPCS_PER_CHUNK.get();
        maxRoutesPerWorld = MAX_ROUTES_PER_WORLD.get();
        maxRoutePoints = MAX_ROUTE_POINTS.get();    }
}
