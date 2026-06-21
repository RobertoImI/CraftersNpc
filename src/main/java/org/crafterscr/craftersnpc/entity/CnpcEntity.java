package org.crafterscr.craftersnpc.entity;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.route.RouteStorage;
import org.crafterscr.craftersnpc.network.ReputationIndicatorPayload;
import org.crafterscr.craftersnpc.reputation.NpcReputation;
import org.crafterscr.craftersnpc.reputation.ReputationReason;
import org.crafterscr.craftersnpc.storage.NpcSettingsStorage;

import org.crafterscr.craftersnpc.behavior.action.NpcAction;
import org.crafterscr.craftersnpc.behavior.action.NpcActionRegistry;
import org.crafterscr.craftersnpc.dialogue.DialogueContext;
import org.crafterscr.craftersnpc.dialogue.DialogueEntry;
import org.crafterscr.craftersnpc.dialogue.DialogueService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CnpcEntity extends PathfinderMob {
    public static final double DEFAULT_WALK_SPEED = 0.25D;
    public static final int MAX_DIALOGUE_PHRASES = 256;
    public static final int MAX_DIALOGUE_PHRASE_LENGTH = 1024;
    private static final double MIN_WALK_SPEED = 0.05D;
    private static final double MAX_WALK_SPEED = 1.00D;
    private static final EntityDataAccessor<String> SKIN_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NPC_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ROUTE_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM_MODEL = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TEMPERAMENT = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DIALOGUE_TEXT = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DIALOGUE_TICKS = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REPUTATION_DELTA = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REPUTATION_INDICATOR_TICKS = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.INT);
    private static final int REPUTATION_INDICATOR_DURATION_TICKS = 40;

    private final List<RoutePoint> route = new ArrayList<>();
    private final List<RoutePoint> nightRefugePoints = new ArrayList<>();
    private final List<NpcScheduleEntry> schedule = new ArrayList<>();
    private final List<DialogueEntry> dialogueEntries = new ArrayList<>();
    private final Map<UUID, NpcPlayerMemory> playerMemories = new HashMap<>();

    private int routeIndex;
    private boolean movingForward = true;
    private int waitTicks;
    private boolean routeEnabled;
    private boolean nightModeOnly;
    private int repathTicks;
    private int stuckTicks;
    private Vec3 lastProgressPos = Vec3.ZERO;
    private int noProgressTicks;
    private NpcAction activeRouteAction;
    private Map<String, String> activeRouteActionParameters = Map.of();
    private int activeRouteActionTicks;
    private String activeScheduleRouteId = "";

    private NightModeState nightModeState = NightModeState.NONE;
    private int nightRefugeIndex = -1;
    private int nightReturnRouteIndex;
    private BlockPos interactingDoorPos;
    private int doorCloseDelayTicks;

    private List<RoutePoint> cachedRoute = List.of();
    private List<RouteStorage.RoutePoint> cachedStoredRouteSource = List.of();

    private ReactionState reactionState = ReactionState.NONE;
    private UUID reactivePlayerUuid;
    private int reactiveTicks;
    private int reactiveAttackCooldown;
    private double walkSpeed = DEFAULT_WALK_SPEED;
    private UUID dialoguePlayerUuid;
    private int lastDialoguePhraseIndex = -1;

    protected CnpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, DEFAULT_WALK_SPEED)
            .add(Attributes.ATTACK_DAMAGE, 3.0D)
            .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_ID, "steve");
        builder.define(NPC_ID, "");
        builder.define(ROUTE_ID, "");
        builder.define(SLIM_MODEL, false);
        builder.define(TEMPERAMENT, Temperament.PACIFICO.id);
        builder.define(DIALOGUE_TEXT, "");
        builder.define(DIALOGUE_TICKS, 0);
        builder.define(REPUTATION_DELTA, 0);
        builder.define(REPUTATION_INDICATOR_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickReputationIndicator();
            if (tickDialogue()) {
                return;
            }
            if (tickReaction()) {
                return;
            }
            tickRoute();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Interaction rule: an empty hand talks; non-empty hands remain available to the future gift system.
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (!canStartDialogue()) {
            return InteractionResult.CONSUME;
        }

        NpcPlayerMemory memory = getOrCreatePlayerMemory(serverPlayer);
        DialogueContext context = createDialogueContext(memory);
        if (DialogueService.startConversation(this, serverPlayer, context, memory)) {
            maybeRewardDialogueReputation(serverPlayer, memory);
            memory.recordInteraction(serverPlayer.getGameProfile().getName(), level().getGameTime());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private NpcPlayerMemory getOrCreatePlayerMemory(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        return playerMemories.computeIfAbsent(
            playerUuid,
            uuid -> new NpcPlayerMemory(uuid, player.getGameProfile().getName(), level().getGameTime())
        );
    }

    private DialogueContext createDialogueContext(NpcPlayerMemory memory) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        if (!memory.greeted()) {
            categories.add("saludo");
        }

        int reputation = memory.reputation();
        String category;
        if (NpcReputation.isFriendly(reputation)) {
            category = NpcReputation.CATEGORY_FRIENDLY;
        } else if (NpcReputation.isHostile(reputation)) {
            category = NpcReputation.CATEGORY_HOSTILE;
            categories.add(NpcReputation.CATEGORY_FORGIVENESS);
        } else if (reputation <= NpcReputation.ANNOYED_THRESHOLD) {
            category = NpcReputation.CATEGORY_ANNOYED;
        } else {
            category = NpcReputation.CATEGORY_NEUTRAL;
        }
        categories.add(category);
        categories.add(DialogueEntry.GENERIC_CATEGORY);

        return new DialogueContext(
            category,
            memory.playerUuid(),
            reputation,
            memory.greeted(),
            memory.interactionCount(),
            categories
        );
    }

    private void maybeRewardDialogueReputation(ServerPlayer player, NpcPlayerMemory memory) {
        long gameTime = level().getGameTime();
        if (gameTime - memory.lastDialogueReputationGameTime() < NpcReputation.DIALOGUE_REWARD_COOLDOWN_TICKS) {
            return;
        }
        adjustReputation(player, NpcReputation.DIALOGUE_REWARD, ReputationReason.DIALOGUE);
        memory.markDialogueReputationRewarded(gameTime);
    }

    public int getReputation(ServerPlayer player) {
        return getOrCreatePlayerMemory(player).reputation();
    }

    public int adjustReputation(ServerPlayer player, int delta, ReputationReason reason) {
        NpcPlayerMemory memory = getOrCreatePlayerMemory(player);
        int previousReputation = memory.reputation();
        int newReputation = memory.adjustReputation(delta);
        int appliedDelta = newReputation - previousReputation;
        if (appliedDelta != 0) {
            sendReputationIndicator(player, appliedDelta, newReputation);
        }
        return newReputation;
    }

    private void sendReputationIndicator(ServerPlayer player, int appliedDelta, int currentReputation) {
        PacketDistributor.sendToPlayer(
            player,
            new ReputationIndicatorPayload(getId(), appliedDelta, currentReputation, REPUTATION_INDICATOR_DURATION_TICKS)
        );
    }

    /**
     * Fallback for non-private reputation indicators. Using this makes every tracking client see the change.
     */
    public void showSharedReputationIndicator(int appliedDelta) {
        if (appliedDelta == 0) {
            return;
        }
        entityData.set(REPUTATION_DELTA, appliedDelta);
        entityData.set(REPUTATION_INDICATOR_TICKS, REPUTATION_INDICATOR_DURATION_TICKS);
    }

    public boolean isFriendlyWith(ServerPlayer player) {
        return NpcReputation.isFriendly(getReputation(player));
    }

    public boolean isHostileTo(ServerPlayer player) {
        return NpcReputation.isHostile(getReputation(player));
    }

    private boolean canStartDialogue() {
        return getDialogueTicks() <= 0;
    }

    private boolean tickDialogue() {
        int remainingTicks = getDialogueTicks();
        if (remainingTicks <= 0) {
            return false;
        }

        Player player = dialoguePlayerUuid == null ? null : level().getPlayerByUUID(dialoguePlayerUuid);
        if (player != null && player.isAlive()) {
            getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
        getNavigation().stop();
        setDialogueTicks(remainingTicks - 1);
        if (remainingTicks == 1) {
            clearDialogue();
            reengageRouteNavigation();
            return false;
        }
        return true;
    }

    public void startDialogue(String text, int durationTicks, ServerPlayer player) {
        entityData.set(DIALOGUE_TEXT, text);
        setDialogueTicks(Math.max(1, durationTicks));
        dialoguePlayerUuid = player.getUUID();
        getNavigation().stop();
    }

    private void clearDialogue() {
        entityData.set(DIALOGUE_TEXT, "");
        setDialogueTicks(0);
        dialoguePlayerUuid = null;
    }

    private void setDialogueTicks(int ticks) {
        entityData.set(DIALOGUE_TICKS, Math.max(0, ticks));
    }

    public String getDialogueText() {
        return entityData.get(DIALOGUE_TEXT);
    }

    public int getDialogueTicks() {
        return entityData.get(DIALOGUE_TICKS);
    }

    private void tickReputationIndicator() {
        int remainingTicks = getReputationIndicatorTicks();
        if (remainingTicks <= 0) {
            return;
        }
        entityData.set(REPUTATION_INDICATOR_TICKS, remainingTicks - 1);
        if (remainingTicks == 1) {
            entityData.set(REPUTATION_DELTA, 0);
        }
    }

    public int getReputationDeltaIndicator() {
        return entityData.get(REPUTATION_DELTA);
    }

    public int getReputationIndicatorTicks() {
        return entityData.get(REPUTATION_INDICATOR_TICKS);
    }

    public List<String> getDialoguePhrases() {
        return dialogueEntries.stream().map(DialogueEntry::text).toList();
    }

    public List<DialogueEntry> getDialogueEntries() {
        return List.copyOf(dialogueEntries);
    }

    public int getLastDialoguePhraseIndex() {
        return lastDialoguePhraseIndex;
    }

    public void setLastDialoguePhraseIndex(int index) {
        lastDialoguePhraseIndex = index >= 0 && index < dialogueEntries.size() ? index : -1;
    }

    public DialogueContext withDialogueEntryState(DialogueContext context, int index) {
        if (context == null || context.playerUuid() == null) {
            return context;
        }
        NpcPlayerMemory memory = playerMemories.get(context.playerUuid());
        if (memory == null) {
            return context;
        }
        long gameTime = level().getGameTime();
        return context.withDialogueEntryState(memory.hasUsedDialogueEntry(index), memory.ticksSinceDialogueEntryUsed(index, gameTime));
    }

    public void markDialogueEntryUsed(int index, int cooldownTicks, ServerPlayer player) {
        setLastDialoguePhraseIndex(index);
        if (player != null) {
            getOrCreatePlayerMemory(player).markDialogueEntryUsed(index, cooldownTicks, level().getGameTime());
        }
    }

    public boolean addDialoguePhrase(String phrase) {
        return addDialogueEntry(DialogueEntry.generic(phrase));
    }

    public boolean addDialogueEntry(String phrase, String category) {
        return addDialogueEntry(new DialogueEntry(phrase, category, DialogueEntry.DEFAULT_WEIGHT));
    }

    public boolean addDialogueEntry(DialogueEntry entry) {
        if (entry.text().isEmpty() || entry.text().length() > MAX_DIALOGUE_PHRASE_LENGTH || dialogueEntries.size() >= MAX_DIALOGUE_PHRASES) {
            return false;
        }
        dialogueEntries.add(entry);
        return true;
    }

    public boolean editDialoguePhrase(int index, String phrase) {
        String normalized = normalizeDialoguePhrase(phrase);
        if (index < 0 || index >= dialogueEntries.size() || normalized.isEmpty()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(normalized, current.category(), current.weight(), current.minReputation(),
            current.maxReputation(), current.oncePerPlayer(), current.cooldownTicks(), current.priority()));
        return true;
    }

    public boolean editDialogueWeight(int index, int weight) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), current.category(), weight, current.minReputation(),
            current.maxReputation(), current.oncePerPlayer(), current.cooldownTicks(), current.priority()));
        return true;
    }

    public boolean editDialogueCategory(int index, String category) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), DialogueEntry.normalizeCategory(category), current.weight(), current.minReputation(),
            current.maxReputation(), current.oncePerPlayer(), current.cooldownTicks(), current.priority()));
        return true;
    }

    public boolean editDialogueReputationRange(int index, int minReputation, int maxReputation) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), current.category(), current.weight(), minReputation,
            maxReputation, current.oncePerPlayer(), current.cooldownTicks(), current.priority()));
        return true;
    }

    public boolean editDialogueOncePerPlayer(int index, boolean oncePerPlayer) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), current.category(), current.weight(), current.minReputation(),
            current.maxReputation(), oncePerPlayer, current.cooldownTicks(), current.priority()));
        return true;
    }

    public boolean editDialogueCooldown(int index, int cooldownTicks) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), current.category(), current.weight(), current.minReputation(),
            current.maxReputation(), current.oncePerPlayer(), cooldownTicks, current.priority()));
        return true;
    }

    public boolean editDialoguePriority(int index, int priority) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        DialogueEntry current = dialogueEntries.get(index);
        dialogueEntries.set(index, new DialogueEntry(current.text(), current.category(), current.weight(), current.minReputation(),
            current.maxReputation(), current.oncePerPlayer(), current.cooldownTicks(), priority));
        return true;
    }

    public boolean removeDialoguePhrase(int index) {
        if (index < 0 || index >= dialogueEntries.size()) {
            return false;
        }
        dialogueEntries.remove(index);
        if (lastDialoguePhraseIndex == index) {
            lastDialoguePhraseIndex = -1;
        } else if (lastDialoguePhraseIndex > index) {
            lastDialoguePhraseIndex--;
        }
        for (NpcPlayerMemory memory : playerMemories.values()) {
            memory.removeDialogueEntryIndex(index);
        }
        return true;
    }

    private static String normalizeDialoguePhrase(String phrase) {
        String normalized = phrase == null ? "" : phrase.strip();
        return normalized.length() <= MAX_DIALOGUE_PHRASE_LENGTH ? normalized : "";
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel && !NpcSettingsStorage.get(serverLevel).isNpcDamageEnabled()) {
            return false;
        }

        boolean damaged = super.hurt(source, amount);
        if (!damaged || level().isClientSide) {
            return damaged;
        }

        if (source.getEntity() instanceof Player player && player.isAlive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                adjustReputation(serverPlayer, NpcReputation.PLAYER_ATTACK_PENALTY, ReputationReason.PLAYER_ATTACK);
            }
            startReaction(player);
        }
        return true;
    }

    private boolean tickReaction() {
        if (reactionState == ReactionState.NONE || reactiveTicks <= 0 || reactivePlayerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            stopReaction();
            return false;
        }

        Player player = serverLevel.getPlayerByUUID(reactivePlayerUuid);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            stopReaction();
            return false;
        }

        reactiveTicks--;
        if (reactionState == ReactionState.ATTACKING) {
            getLookControl().setLookAt(player, 30.0F, 30.0F);
            getNavigation().moveTo(player, 1.15D);
            if (reactiveAttackCooldown > 0) {
                reactiveAttackCooldown--;
            }
            if (distanceToSqr(player) <= 4.0D && reactiveAttackCooldown <= 0) {
                swing(InteractionHand.MAIN_HAND);
                player.hurt(damageSources().playerAttack(player), 1.0F);
                reactiveAttackCooldown = 15;
            }
        } else if (reactionState == ReactionState.FLEEING) {
            Vec3 away = position().subtract(player.position());
            Vec3 horizontalAway = new Vec3(away.x, 0.0D, away.z);
            if (horizontalAway.lengthSqr() <= 1.0E-4D) {
                horizontalAway = new Vec3((random.nextDouble() - 0.5D) * 2.0D, 0.0D, (random.nextDouble() - 0.5D) * 2.0D);
            }
            Vec3 fleeTarget = position().add(horizontalAway.normalize().scale(6.0D));
            getNavigation().moveTo(fleeTarget.x, position().y, fleeTarget.z, 1.2D);
        }

        if (reactiveTicks <= 0) {
            stopReaction();
            return false;
        }
        return true;
    }

    private void startReaction(Player player) {
        Temperament temperament = getTemperament();
        reactionState = switch (temperament) {
            case AGRESIVO -> ReactionState.ATTACKING;
            case ALEATORIO -> random.nextBoolean() ? ReactionState.ATTACKING : ReactionState.FLEEING;
            case PACIFICO -> ReactionState.FLEEING;
        };

        reactivePlayerUuid = player.getUUID();
        reactiveTicks = 20 * 8;
        reactiveAttackCooldown = 0;
        finishCurrentAction();
        waitTicks = 0;
        resetMovementTracking();
        closeInteractingDoorIfAny();
        getNavigation().stop();
    }

    private void stopReaction() {
        if (reactionState == ReactionState.NONE && reactivePlayerUuid == null && reactiveTicks == 0) {
            return;
        }
        reactionState = ReactionState.NONE;
        reactivePlayerUuid = null;
        reactiveTicks = 0;
        reactiveAttackCooldown = 0;
        reengageRouteNavigation();
    }

    private void tickRoute() {
        tickDoorInteraction();
        updateScheduledRoute();

        if (!routeEnabled) {
            resetMovementTracking();
            return;
        }

        List<RoutePoint> points = currentRoute();
        if (points.isEmpty()) {
            finishCurrentAction();
            resetMovementTracking();
            waitTicks = 0;
            resetNightState();
            closeInteractingDoorIfAny();
            getNavigation().stop();

            if (schedule.isEmpty() && !getAssignedRouteId().isBlank()) {
                routeEnabled = false;
                CraftersNpc.LOGGER.debug("Desactivando ruta vacía para NPC {} (RouteId={})", getNpcId(), getAssignedRouteId());
            }
            return;
        }

        routeIndex = Mth.clamp(routeIndex, 0, points.size() - 1);
        nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, points.size() - 1);

        updateNightModeState(points);


        if (waitTicks > 0) {
            beginCurrentAction(points);
            tickCurrentAction();
            waitTicks--;
            getNavigation().stop();
            resetMovementTracking();

            if (waitTicks == 0) {
                advanceAfterReached(points);
                if (waitTicks > 0) {
                    return;
                }
                Vec3 nextCenter = currentTargetPos(points);
                getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
            }
            return;
        }

        Vec3 center = currentTargetPos(points);
        boolean reachedPoint = distanceToSqr(center) <= 1.8D || (getNavigation().isDone() && distanceToSqr(center) <= 2.25D);
        if (reachedPoint) {
            getNavigation().stop();
            waitTicks = currentWaitTicks(points);
            beginCurrentAction(points);
            resetMovementTracking();
            lastProgressPos = position();
            if (waitTicks <= 0) {
                advanceAfterReached(points);
                Vec3 nextCenter = currentTargetPos(points);
                getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
            }
            return;
        }

        trackMovementStall(points);

        repathTicks++;
        if (repathTicks >= 10 || getNavigation().isDone()) {
            repathTicks = 0;
            boolean hasPath = getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
            if (!hasPath) {
                stuckTicks += 10;
                if (stuckTicks >= 40) {
                    stuckTicks = 0;
                    advanceAfterReached(points);
                }
            } else {
                stuckTicks = 0;
            }
        }
    }

    private void trackMovementStall(List<RoutePoint> points) {
        Vec3 currentPos = position();
        if (currentPos.distanceToSqr(lastProgressPos) <= 0.04D) {
            noProgressTicks++;
        } else {
            lastProgressPos = currentPos;
            noProgressTicks = 0;
        }

        if (noProgressTicks < 100) {
            return;
        }

        noProgressTicks = 0;
        recoverFromStall(points);
        Vec3 nextCenter = currentTargetPos(points);
        getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
    }

    private void recoverFromStall(List<RoutePoint> baseRoute) {
        finishCurrentAction();
        waitTicks = 0;
        resetMovementTracking();

        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
            nightModeState = NightModeState.RETURNING_TO_ROUTE;
            nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, baseRoute.size() - 1);
            return;
        }

        advanceIndex(baseRoute);
    }

    private void tickDoorInteraction() {
        if (interactingDoorPos != null) {
            if (!isDoor(interactingDoorPos)) {
                interactingDoorPos = null;
                doorCloseDelayTicks = 0;
            } else {
                double distanceToDoor = distanceToSqr(Vec3.atCenterOf(interactingDoorPos));
                if (distanceToDoor <= 6.25D) {
                    doorCloseDelayTicks = 20;
                } else if (doorCloseDelayTicks > 0) {
                    doorCloseDelayTicks--;
                } else {
                    setDoorOpen(interactingDoorPos, false);
                    interactingDoorPos = null;
                }
            }
        }

        BlockPos frontDoorPos = findDoorInFront();
        if (frontDoorPos == null) {
            return;
        }

        if (interactingDoorPos != null && !interactingDoorPos.equals(frontDoorPos)) {
            setDoorOpen(interactingDoorPos, false);
        }

        interactingDoorPos = frontDoorPos;
        setDoorOpen(interactingDoorPos, true);
        doorCloseDelayTicks = 20;
        swing(InteractionHand.MAIN_HAND);
    }

    private BlockPos findDoorInFront() {
        Vec3 movement = getDeltaMovement();
        Vec3 horizontalMovement = new Vec3(movement.x, 0.0D, movement.z);
        Vec3 facing = horizontalMovement.lengthSqr() > 1.0E-4D ? horizontalMovement.normalize() : new Vec3(getLookAngle().x, 0.0D, getLookAngle().z).normalize();
        if (facing.lengthSqr() <= 1.0E-4D) {
            return null;
        }

        Direction direction = Direction.getNearest(facing.x, 0.0D, facing.z);
        BlockPos origin = blockPosition();
        BlockPos[] candidates = new BlockPos[] {
            origin.relative(direction),
            origin.above().relative(direction),
            origin,
            origin.above()
        };

        for (BlockPos candidate : candidates) {
            BlockPos doorBase = getDoorBasePos(candidate);
            if (doorBase != null) {
                return doorBase;
            }
        }
        return null;
    }

    private boolean isDoor(BlockPos pos) {
        return getDoorBasePos(pos) != null;
    }

    private BlockPos getDoorBasePos(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return null;
        }
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            return pos;
        }
        return pos.below();
    }

    private void setDoorOpen(BlockPos doorBasePos, boolean open) {
        BlockState doorState = level().getBlockState(doorBasePos);
        if (!(doorState.getBlock() instanceof DoorBlock doorBlock)) {
            return;
        }
        if (doorState.getValue(DoorBlock.OPEN) == open) {
            return;
        }
        doorBlock.setOpen(this, level(), doorState, doorBasePos, open);
    }

    private void updateNightModeState(List<RoutePoint> baseRoute) {
        if (!nightModeOnly || nightRefugePoints.isEmpty()) {
            if (nightModeState == NightModeState.RETURNING_TO_ROUTE) {
                resetNightState();
            }
            return;
        }

        if (level().isNight()) {
            if (nightModeState == NightModeState.NONE) {
                finishCurrentAction();
                startNightRefugeDetour();
            }
            return;
        }

        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
            finishCurrentAction();
            nightModeState = NightModeState.RETURNING_TO_ROUTE;
            nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, baseRoute.size() - 1);
            waitTicks = 0;
            resetMovementTracking();
        }
    }

    private void startNightRefugeDetour() {
        if (nightRefugePoints.isEmpty()) {
            return;
        }
        int closest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < nightRefugePoints.size(); i++) {
            double dist = distanceToSqr(nightRefugePoints.get(i).pos());
            if (dist < best) {
                best = dist;
                closest = i;
            }
        }
        nightRefugeIndex = closest;
        nightReturnRouteIndex = routeIndex;
        nightModeState = NightModeState.GOING_TO_REFUGE;
        waitTicks = 0;
        resetMovementTracking();
    }

    private Vec3 currentTargetPos(List<RoutePoint> baseRoute) {
        if ((nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) && isValidNightTarget()) {
            return nightRefugePoints.get(nightRefugeIndex).pos();
        }
        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
            CraftersNpc.LOGGER.debug("Night target inválido para NPC {} (state={}, index={})", getNpcId(), nightModeState, nightRefugeIndex);
            resetNightState();
        }
        if (nightModeState == NightModeState.RETURNING_TO_ROUTE) {
            return baseRoute.get(nightReturnRouteIndex).pos();
        }

        return baseRoute.get(routeIndex).pos();
    }

    private int currentWaitTicks(List<RoutePoint> baseRoute) {
        if ((nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) && isValidNightTarget()) {
            return nightRefugePoints.get(nightRefugeIndex).waitTicks();
        }
        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
            resetNightState();
            return baseRoute.get(routeIndex).waitTicks();
        }
        if (nightModeState == NightModeState.RETURNING_TO_ROUTE) {
            return 0;
        }

        return baseRoute.get(routeIndex).waitTicks();
    }

    private void advanceAfterReached(List<RoutePoint> baseRoute) {
        finishCurrentAction();
        if (nightModeState == NightModeState.GOING_TO_REFUGE) {
            nightModeState = NightModeState.AT_REFUGE;
            return;
        }
        if (nightModeState == NightModeState.AT_REFUGE) {
            if (nightModeOnly && level().isNight()) {
                waitTicks = 20;
                return;
            }
            nightModeState = NightModeState.RETURNING_TO_ROUTE;
            return;
        }
        if (nightModeState == NightModeState.RETURNING_TO_ROUTE) {
            resetNightState();
            return;
        }

        advanceIndex(baseRoute);
    }

    private void beginCurrentAction(List<RoutePoint> baseRoute) {
        if (activeRouteAction != null || nightModeState != NightModeState.NONE || routeIndex < 0 || routeIndex >= baseRoute.size()) {
            return;
        }
        RoutePoint point = baseRoute.get(routeIndex);
        NpcActionRegistry.find(point.actionId()).ifPresent(action -> {
            activeRouteAction = action;
            activeRouteActionParameters = point.actionParameters();
            activeRouteActionTicks = 0;
            action.start(this, activeRouteActionParameters);
        });
    }

    private void tickCurrentAction() {
        if (activeRouteAction != null) {
            activeRouteAction.tick(this, activeRouteActionParameters, activeRouteActionTicks++);
        }
    }

    private void finishCurrentAction() {
        if (activeRouteAction == null) {
            return;
        }
        activeRouteAction.finish(this, activeRouteActionParameters);
        activeRouteAction = null;
        activeRouteActionParameters = Map.of();
        activeRouteActionTicks = 0;
    }

    private void updateScheduledRoute() {
        if (schedule.isEmpty()) {
            if (!activeScheduleRouteId.isEmpty()) {
                switchEffectiveRoute("");
            }
            return;
        }

        int dayTime = (int) Math.floorMod(level().getDayTime(), NpcScheduleEntry.DAY_TICKS);
        String effectiveRouteId = schedule.stream()
            .filter(entry -> entry.contains(dayTime))
            .map(NpcScheduleEntry::routeId)
            .findFirst()
            .orElseGet(() -> fallbackRouteId(dayTime));
        if (!effectiveRouteId.equals(activeScheduleRouteId)) {
            switchEffectiveRoute(effectiveRouteId);
        }
    }

    private String fallbackRouteId(int dayTime) {
        String assignedRouteId = getAssignedRouteId();
        if (!assignedRouteId.isBlank()) {
            return assignedRouteId;
        }

        return schedule.stream()
            .min(Comparator.comparingInt(entry -> Math.floorMod(dayTime - entry.endTime(), NpcScheduleEntry.DAY_TICKS)))
            .map(NpcScheduleEntry::routeId)
            .orElse("");
    }

    private void switchEffectiveRoute(String routeId) {
        finishCurrentAction();
        activeScheduleRouteId = routeId;
        cachedRoute = List.of();
        cachedStoredRouteSource = List.of();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        resetMovementTracking();
        resetNightState();
        closeInteractingDoorIfAny();
        getNavigation().stop();
    }

    private String effectiveRouteId() {
        return schedule.isEmpty() ? getAssignedRouteId() : activeScheduleRouteId;
    }

    private List<RoutePoint> currentRoute() {
        String routeId = effectiveRouteId();
        if (level() instanceof ServerLevel serverLevel && !routeId.isBlank()) {
            List<RouteStorage.RoutePoint> stored = RouteStorage.get(serverLevel).getRoute(routeId);
            if (!stored.isEmpty()) {
                if (stored != cachedStoredRouteSource) {
                    cachedStoredRouteSource = stored;
                    cachedRoute = stored.stream()
                        .map(p -> new RoutePoint(new Vec3(p.x(), p.y(), p.z()), normalizeWaitTicks(p.waitTicks()), p.actionId(), p.actionParameters()))
                        .toList();
                }
                return cachedRoute;
            }
        }
        cachedStoredRouteSource = List.of();
        cachedRoute = List.of();
        return schedule.isEmpty() ? route : List.of();
    }

    private static int normalizeWaitTicks(int rawWait) {
        return Mth.clamp(rawWait, 0, 3600 * 20);
    }

    private void advanceIndex(List<RoutePoint> points) {
        int size = points.size();
        if (size <= 1) {
            return;
        }

        if (isLoopRoute(points)) {
            routeIndex = (routeIndex + 1) % size;
            return;
        }

        if (movingForward) {
            if (routeIndex >= size - 1) {
                movingForward = false;
                routeIndex--;
            } else {
                routeIndex++;
            }
        } else {
            if (routeIndex <= 0) {
                movingForward = true;
                routeIndex++;
            } else {
                routeIndex--;
            }
        }
    }

    private static boolean isLoopRoute(List<RoutePoint> points) {
        if (points.size() < 3) {
            return false;
        }

        BlockPos first = BlockPos.containing(points.get(0).pos());
        BlockPos last = BlockPos.containing(points.get(points.size() - 1).pos());
        return first.distManhattan(last) <= 1;
    }

    public void addNightRefugePoint(Vec3 pos, int waitSeconds) {
        nightRefugePoints.add(new RoutePoint(pos, Mth.clamp(waitSeconds, 0, 3600) * 20));
    }

    public void clearNightRefugePoints() {
        nightRefugePoints.clear();
        resetNightState();
        waitTicks = 0;
        resetMovementTracking();
        lastProgressPos = position();
        reengageRouteNavigation();
    }

    public List<String> nightRefugeSummary() {
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < nightRefugePoints.size(); i++) {
            RoutePoint point = nightRefugePoints.get(i);
            summary.add("#" + i + " (" + Mth.floor(point.pos().x) + "," + Mth.floor(point.pos().y) + "," + Mth.floor(point.pos().z) + ") wait=" + (point.waitTicks() / 20) + "s");
        }
        return summary;
    }

    public List<NpcScheduleEntry> getSchedule() {
        return List.copyOf(schedule);
    }

    public void setScheduleEntry(NpcScheduleEntry entry) {
        for (NpcScheduleEntry existing : schedule) {
            boolean sameInterval = existing.startTime() == entry.startTime() && existing.endTime() == entry.endTime();
            if (!sameInterval && existing.overlaps(entry)) {
                throw new IllegalArgumentException("La franja se superpone con " + existing.startTime() + "-" + existing.endTime() + ".");
            }
        }
        schedule.removeIf(existing -> existing.startTime() == entry.startTime() && existing.endTime() == entry.endTime());
        schedule.add(entry);
        schedule.sort(Comparator.comparingInt(NpcScheduleEntry::startTime));
        routeEnabled = true;
        switchEffectiveRoute("");
    }

    public boolean removeScheduleEntry(int index) {
        if (index < 0 || index >= schedule.size()) {
            return false;
        }
        schedule.remove(index);
        switchEffectiveRoute("");
        return true;
    }

    public int removeScheduleEntriesForRoute(String routeId) {
        int previousSize = schedule.size();
        schedule.removeIf(entry -> entry.routeId().equalsIgnoreCase(routeId));
        int removed = previousSize - schedule.size();
        if (removed > 0) {
            switchEffectiveRoute("");
        }
        return removed;
    }

    public void clearRoute() {
        finishCurrentAction();
        route.clear();
        cachedRoute = List.of();
        cachedStoredRouteSource = List.of();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        routeEnabled = false;
        resetMovementTracking();
        resetNightState();
        closeInteractingDoorIfAny();
        getNavigation().stop();
    }

    public void setRouteEnabled(boolean routeEnabled) {
        this.routeEnabled = routeEnabled;
        if (!routeEnabled) {
            finishCurrentAction();
            resetMovementTracking();
            waitTicks = 0;
            closeInteractingDoorIfAny();
            getNavigation().stop();
            return;
        }
        reengageRouteNavigation();
    }

    public String getSkinId() {
        return entityData.get(SKIN_ID);
    }

    public void setSkinId(String skinId) {
        entityData.set(SKIN_ID, normalizeId(skinId));
    }

    public void setNpcId(String npcId) {
        String previousNpcId = getNpcId();
        String normalizedNpcId = normalizeId(npcId);
        entityData.set(NPC_ID, normalizedNpcId);
        if (!level().isClientSide) {
            NpcRegistry.updateNpcId(this, previousNpcId, normalizedNpcId);
        }
    }

    public String getNpcId() {
        return entityData.get(NPC_ID);
    }

    public void setAssignedRouteId(String routeId) {
        finishCurrentAction();
        entityData.set(ROUTE_ID, normalizeId(routeId));
        cachedRoute = List.of();
        cachedStoredRouteSource = List.of();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        resetMovementTracking();
        resetNightState();
        closeInteractingDoorIfAny();
        reengageRouteNavigation();
    }

    public String getAssignedRouteId() {
        return entityData.get(ROUTE_ID);
    }

    public boolean isSlimModel() {
        return entityData.get(SLIM_MODEL);
    }

    public Temperament getTemperament() {
        return Temperament.fromId(entityData.get(TEMPERAMENT));
    }

    public void setTemperament(Temperament temperament) {
        entityData.set(TEMPERAMENT, temperament.id);
    }

    public void setSlimModel(boolean slimModel) {
        entityData.set(SLIM_MODEL, slimModel);
    }

    public boolean isNightModeOnly() {
        return nightModeOnly;
    }

    public double getWalkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(double speed) {
        walkSpeed = Mth.clamp(speed, MIN_WALK_SPEED, MAX_WALK_SPEED);
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(walkSpeed);
        }
    }

    public void setNightModeOnly(boolean nightModeOnly) {
        this.nightModeOnly = nightModeOnly;
        if (!nightModeOnly) {
            resetNightState();
            waitTicks = 0;
            resetMovementTracking();
            lastProgressPos = position();
            reengageRouteNavigation();
        }
    }

    public void forceRecoverFromStall() {
        if (!routeEnabled) {
            return;
        }
        List<RoutePoint> points = currentRoute();
        if (points.isEmpty()) {
            return;
        }
        routeIndex = Mth.clamp(routeIndex, 0, points.size() - 1);
        nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, points.size() - 1);
        recoverFromStall(points);
        lastProgressPos = position();
        Vec3 nextCenter = currentTargetPos(points);
        getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
    }

    public String debugRouteState() {
        List<RoutePoint> points = currentRoute();
        String target = points.isEmpty() ? "none" : formatVec3(currentTargetPos(points));
        return "routeEnabled=" + routeEnabled
            + ", routeId=" + getAssignedRouteId()
            + ", effectiveRouteId=" + effectiveRouteId()
            + ", scheduleEntries=" + schedule.size()
            + ", routePoints=" + points.size()
            + ", routeIndex=" + routeIndex
            + ", movingForward=" + movingForward
            + ", waitTicks=" + waitTicks
            + ", repathTicks=" + repathTicks
            + ", stuckTicks=" + stuckTicks
            + ", noProgressTicks=" + noProgressTicks
            + ", nightModeOnly=" + nightModeOnly
            + ", walkSpeed=" + String.format(Locale.ROOT, "%.2f", walkSpeed)
            + ", temperament=" + getTemperament().id
            + ", reaction=" + reactionState
            + ", reactiveTicks=" + reactiveTicks
            + ", nightState=" + nightModeState
            + ", nightRefugeIndex=" + nightRefugeIndex
            + ", nightRefugeCount=" + nightRefugePoints.size()
            + ", nightReturnRouteIndex=" + nightReturnRouteIndex
            + ", pos=" + formatVec3(position())
            + ", target=" + target;
    }

    private String formatVec3(Vec3 vec) {
        return "(" + Mth.floor(vec.x) + "," + Mth.floor(vec.y) + "," + Mth.floor(vec.z) + ")";
    }

    private void resetMovementTracking() {
        repathTicks = 0;
        stuckTicks = 0;
        noProgressTicks = 0;
    }

    private void resetNightState() {
        nightModeState = NightModeState.NONE;
        nightRefugeIndex = -1;
    }

    private boolean isValidNightTarget() {
        return nightRefugeIndex >= 0 && nightRefugeIndex < nightRefugePoints.size();
    }

    private void reengageRouteNavigation() {
        getNavigation().stop();
        if (!routeEnabled) {
            return;
        }
        List<RoutePoint> points = currentRoute();
        if (points.isEmpty()) {
            return;
        }
        routeIndex = Mth.clamp(routeIndex, 0, points.size() - 1);
        nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, points.size() - 1);
        Vec3 nextCenter = currentTargetPos(points);
        getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
    }


    private static boolean isFinite(Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }

    private static int decodeWaitTicks(CompoundTag pointTag) {
        int rawWait = pointTag.getInt("Wait");
        if (pointTag.contains("WaitIsTicks", Tag.TAG_BYTE) && pointTag.getBoolean("WaitIsTicks")) {
            return normalizeWaitTicks(rawWait);
        }
        return normalizeWaitTicks(rawWait * 20);
    }

    private void closeInteractingDoorIfAny() {
        if (interactingDoorPos != null) {
            setDoorOpen(interactingDoorPos, false);
        }
        interactingDoorPos = null;
        doorCloseDelayTicks = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", getSkinId());
        tag.putString("NpcId", getNpcId());
        tag.putString("RouteId", getAssignedRouteId());
        tag.putBoolean("SlimModel", isSlimModel());
        tag.putBoolean("RouteEnabled", routeEnabled);
        tag.putBoolean("NightModeOnly", nightModeOnly);
        tag.putDouble("WalkSpeed", walkSpeed);
        tag.putString("Temperament", getTemperament().id);
        tag.putInt("RouteIndex", routeIndex);
        tag.putBoolean("MovingForward", movingForward);
        tag.putInt("WaitTicks", waitTicks);
        tag.putInt("RepathTicks", repathTicks);
        tag.putInt("StuckTicks", stuckTicks);
        tag.putInt("NightModeState", nightModeState.ordinal());
        tag.putInt("NightRefugeIndex", nightRefugeIndex);
        tag.putInt("NightReturnRouteIndex", nightReturnRouteIndex);

        ListTag dialogueTag = new ListTag();
        for (DialogueEntry entry : dialogueEntries) {
            dialogueTag.add(entry.save());
        }
        tag.put("DialoguePhrases", dialogueTag);

        ListTag playerMemoriesTag = new ListTag();
        for (NpcPlayerMemory memory : playerMemories.values()) {
            playerMemoriesTag.add(memory.save());
        }
        tag.put("PlayerMemories", playerMemoriesTag);

        ListTag scheduleTag = new ListTag();
        for (NpcScheduleEntry entry : schedule) {
            scheduleTag.add(entry.save());
        }
        tag.put("Schedule", scheduleTag);

        ListTag points = new ListTag();
        for (RoutePoint point : route) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", point.pos().x);
            p.putDouble("Y", point.pos().y);
            p.putDouble("Z", point.pos().z);
            p.putInt("Wait", point.waitTicks());
            p.putBoolean("WaitIsTicks", true);
            RouteStorage.writeAction(p, point.actionId(), point.actionParameters());
            points.add(p);
        }
        tag.put("Route", points);

        ListTag refugesTag = new ListTag();
        for (RoutePoint point : nightRefugePoints) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", point.pos().x);
            p.putDouble("Y", point.pos().y);
            p.putDouble("Z", point.pos().z);
            p.putInt("Wait", point.waitTicks());
            p.putBoolean("WaitIsTicks", true);
            refugesTag.add(p);
        }
        tag.put("NightRefuges", refugesTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinId(tag.contains("Skin", Tag.TAG_STRING) ? tag.getString("Skin") : "steve");
        setNpcId(tag.contains("NpcId", Tag.TAG_STRING) ? tag.getString("NpcId") : "");
        setAssignedRouteId(tag.contains("RouteId", Tag.TAG_STRING) ? tag.getString("RouteId") : "");
        setSlimModel(tag.getBoolean("SlimModel"));
        routeEnabled = tag.getBoolean("RouteEnabled");
        nightModeOnly = tag.getBoolean("NightModeOnly");
        setWalkSpeed(tag.contains("WalkSpeed", Tag.TAG_DOUBLE) ? tag.getDouble("WalkSpeed") : DEFAULT_WALK_SPEED);
        setTemperament(Temperament.fromId(tag.getString("Temperament")));
        routeIndex = tag.getInt("RouteIndex");
        movingForward = tag.getBoolean("MovingForward");
        waitTicks = normalizeWaitTicks(tag.getInt("WaitTicks"));
        repathTicks = Mth.clamp(tag.getInt("RepathTicks"), 0, 200);
        stuckTicks = Mth.clamp(tag.getInt("StuckTicks"), 0, 200);

        int nightStateIndex = tag.getInt("NightModeState");
        nightModeState = nightStateIndex >= 0 && nightStateIndex < NightModeState.values().length ? NightModeState.values()[nightStateIndex] : NightModeState.NONE;
        nightRefugeIndex = tag.getInt("NightRefugeIndex");
        nightReturnRouteIndex = tag.getInt("NightReturnRouteIndex");

        dialogueEntries.clear();
        ListTag dialogueTag = tag.getList("DialoguePhrases", Tag.TAG_COMPOUND);
        for (Tag value : dialogueTag) {
            addDialogueEntry(DialogueEntry.load((CompoundTag) value));
        }
        setLastDialoguePhraseIndex(-1);
        clearDialogue();

        playerMemories.clear();
        ListTag playerMemoriesTag = tag.getList("PlayerMemories", Tag.TAG_COMPOUND);
        for (Tag value : playerMemoriesTag) {
            NpcPlayerMemory.load((CompoundTag) value).ifPresent(memory -> playerMemories.put(memory.playerUuid(), memory));
        }

        schedule.clear();
        activeScheduleRouteId = "";
        ListTag scheduleTag = tag.getList("Schedule", Tag.TAG_COMPOUND);
        for (Tag value : scheduleTag) {
            NpcScheduleEntry.load((CompoundTag) value).ifPresent(entry -> {
                if (schedule.stream().noneMatch(existing -> existing.overlaps(entry))) {
                    schedule.add(entry);
                }
            });
        }
        schedule.sort(Comparator.comparingInt(NpcScheduleEntry::startTime));
        if (!schedule.isEmpty()) {
            routeEnabled = true;
        }

        route.clear();
        ListTag points = tag.getList("Route", Tag.TAG_COMPOUND);
        for (Tag t : points) {
            CompoundTag p = (CompoundTag) t;
            Vec3 pos = new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"));
            if (isFinite(pos)) {
                route.add(new RoutePoint(pos, decodeWaitTicks(p), p.getString("Action"), RouteStorage.readActionParameters(p)));
            }
        }
        if (!route.isEmpty()) {
            routeIndex = Mth.clamp(routeIndex, 0, route.size() - 1);
        } else {
            routeIndex = 0;
        }

        nightRefugePoints.clear();
        ListTag refuges = tag.getList("NightRefuges", Tag.TAG_COMPOUND);
        for (Tag t : refuges) {
            CompoundTag p = (CompoundTag) t;
            Vec3 pos = new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"));
            if (isFinite(pos)) {
                nightRefugePoints.add(new RoutePoint(pos, decodeWaitTicks(p)));
            }
        }
        if (nightRefugePoints.isEmpty() || nightRefugeIndex < 0 || nightRefugeIndex >= nightRefugePoints.size()) {
            nightModeState = NightModeState.NONE;
            nightRefugeIndex = -1;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            NpcRegistry.track(this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        finishCurrentAction();
        if (!level().isClientSide) {
            NpcRegistry.untrack(this);
        }
        super.remove(reason);
    }

    public static CnpcEntity spawn(ServerLevel level, Vec3 pos, String npcId, boolean slimModel) {
        CnpcEntity entity = ModEntities.CNPC.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("No se pudo crear la entidad CNPC");
        }
        entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        entity.setNpcId(npcId);
        entity.setSlimModel(slimModel);
        level.addFreshEntity(entity);
        return entity;
    }

    private enum NightModeState {
        NONE,
        GOING_TO_REFUGE,
        AT_REFUGE,
        RETURNING_TO_ROUTE
    }

    public enum Temperament {
        PACIFICO("pacifico"),
        AGRESIVO("agresivo"),
        ALEATORIO("aleatorio");

        private final String id;

        Temperament(String id) {
            this.id = id;
        }

        public static Temperament fromId(String value) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            for (Temperament temperament : values()) {
                if (temperament.id.equals(normalized)) {
                    return temperament;
                }
            }
            return PACIFICO;
        }
    }

    private enum ReactionState {
        NONE,
        ATTACKING,
        FLEEING
    }

    private record RoutePoint(Vec3 pos, int waitTicks, String actionId, Map<String, String> actionParameters) {
        private RoutePoint(Vec3 pos, int waitTicks) {
            this(pos, waitTicks, "", Map.of());
        }
    }
}
