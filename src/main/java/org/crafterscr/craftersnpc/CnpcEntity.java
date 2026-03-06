package org.crafterscr.craftersnpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CnpcEntity extends PathfinderMob {
    private static final int POI_CHECK_COOLDOWN_AFTER_STALL = 20 * 45;
    private static final int POI_CHECK_COOLDOWN_AFTER_RETURN = 20 * 30;
    private static final int POI_CHECK_COOLDOWN_AFTER_DETOUR_START = 20 * 90;

    private static final EntityDataAccessor<String> SKIN_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NPC_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ROUTE_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM_MODEL = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<RoutePoint> route = new ArrayList<>();
    private final List<RoutePoint> poiPoints = new ArrayList<>();
    private final List<RoutePoint> nightRefugePoints = new ArrayList<>();

    private int routeIndex;
    private boolean movingForward = true;
    private int waitTicks;
    private boolean routeEnabled;
    private boolean nightModeOnly;
    private int repathTicks;
    private int stuckTicks;
    private Vec3 lastProgressPos = Vec3.ZERO;
    private int noProgressTicks;

    private PoiState poiState = PoiState.NONE;
    private int poiIndex = -1;
    private int poiReturnRouteIndex;
    private int poiCheckCooldown;

    private NightModeState nightModeState = NightModeState.NONE;
    private int nightRefugeIndex = -1;
    private int nightReturnRouteIndex;
    private BlockPos interactingDoorPos;
    private int doorCloseDelayTicks;

    private List<RoutePoint> cachedRoute = List.of();
    private List<RouteStorage.RoutePoint> cachedStoredRouteSource = List.of();

    protected CnpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
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
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickRoute();
        }
    }

    private void tickRoute() {
        tickDoorInteraction();

        if (!routeEnabled) {
            resetMovementTracking();
            return;
        }

        List<RoutePoint> points = currentRoute();
        if (points.isEmpty()) {
            resetMovementTracking();
            waitTicks = 0;
            resetPoiState();
            resetNightState();
            closeInteractingDoorIfAny();
            getNavigation().stop();

            if (!getAssignedRouteId().isBlank()) {
                routeEnabled = false;
                CraftersNpc.LOGGER.debug("Desactivando ruta vacía para NPC {} (RouteId={})", getNpcId(), getAssignedRouteId());
            }
            return;
        }

        routeIndex = Mth.clamp(routeIndex, 0, points.size() - 1);
        nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, points.size() - 1);
        poiReturnRouteIndex = Mth.clamp(poiReturnRouteIndex, 0, points.size() - 1);

        updateNightModeState(points);

        if (nightModeState == NightModeState.NONE) {
            if (poiCheckCooldown > 0) {
                poiCheckCooldown--;
            }
            if (poiState == PoiState.NONE && shouldStartPoiDetour()) {
                startPoiDetour();
            }
        }

        if (waitTicks > 0) {
            waitTicks--;
            getNavigation().stop();
            resetMovementTracking();

            if (waitTicks == 0) {
                if (nightModeState == NightModeState.AT_REFUGE) {
                    if (nightModeOnly && level().isNight()) {
                        waitTicks = 20;
                        return;
                    }
                    nightModeState = NightModeState.RETURNING_TO_ROUTE;
                } else if (poiState == PoiState.AT_POI) {
                    poiState = PoiState.RETURNING;
                } else if (nightModeState == NightModeState.NONE && poiState == PoiState.NONE) {
                    advanceIndex(points);
                }
                Vec3 nextCenter = currentTargetPos(points);
                getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
            }
            return;
        }

        Vec3 center = currentTargetPos(points);
        boolean reachedPoint = distanceToSqr(center) <= 1.8D || (getNavigation().isDone() && distanceToSqr(center) <= 4.0D);
        if (reachedPoint) {
            getNavigation().stop();
            waitTicks = currentWaitTicks(points);
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
        waitTicks = 0;
        resetMovementTracking();

        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
            nightModeState = NightModeState.RETURNING_TO_ROUTE;
            nightReturnRouteIndex = Mth.clamp(nightReturnRouteIndex, 0, baseRoute.size() - 1);
            return;
        }

        if (poiState == PoiState.TO_POI || poiState == PoiState.AT_POI) {
            poiState = PoiState.RETURNING;
            poiReturnRouteIndex = Mth.clamp(poiReturnRouteIndex, 0, baseRoute.size() - 1);
            return;
        }

        if (poiState == PoiState.RETURNING) {
            poiState = PoiState.NONE;
            poiIndex = -1;
            poiCheckCooldown = POI_CHECK_COOLDOWN_AFTER_STALL;
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
                startNightRefugeDetour();
            }
            return;
        }

        if (nightModeState == NightModeState.GOING_TO_REFUGE || nightModeState == NightModeState.AT_REFUGE) {
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
        resetPoiState();
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

        return switch (poiState) {
            case TO_POI, AT_POI -> {
                if (isValidPoiTarget()) {
                    yield poiPoints.get(poiIndex).pos();
                }
                CraftersNpc.LOGGER.debug("POI target inválido para NPC {} (state={}, index={})", getNpcId(), poiState, poiIndex);
                resetPoiState();
                yield baseRoute.get(routeIndex).pos();
            }
            case RETURNING -> baseRoute.get(poiReturnRouteIndex).pos();
            case NONE -> baseRoute.get(routeIndex).pos();
        };
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

        return switch (poiState) {
            case TO_POI, AT_POI -> {
                if (isValidPoiTarget()) {
                    yield poiPoints.get(poiIndex).waitTicks();
                }
                resetPoiState();
                yield baseRoute.get(routeIndex).waitTicks();
            }
            case RETURNING -> 0;
            case NONE -> baseRoute.get(routeIndex).waitTicks();
        };
    }

    private void advanceAfterReached(List<RoutePoint> baseRoute) {
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

        if (poiState == PoiState.TO_POI) {
            poiState = PoiState.AT_POI;
            return;
        }
        if (poiState == PoiState.AT_POI) {
            poiState = PoiState.RETURNING;
            return;
        }
        if (poiState == PoiState.RETURNING) {
            resetPoiState();
            poiCheckCooldown = POI_CHECK_COOLDOWN_AFTER_RETURN;
            return;
        }

        advanceIndex(baseRoute);
    }

    private boolean shouldStartPoiDetour() {
        if (poiPoints.isEmpty() || poiCheckCooldown > 0 || random.nextInt(100) >= 3) {
            return false;
        }
        return !level().isClientSide;
    }

    private void startPoiDetour() {
        if (poiPoints.isEmpty()) {
            return;
        }
        RandomSource rng = getRandom();
        poiIndex = rng.nextInt(poiPoints.size());
        poiReturnRouteIndex = routeIndex;
        poiState = PoiState.TO_POI;
        waitTicks = 0;
        resetMovementTracking();
        poiCheckCooldown = POI_CHECK_COOLDOWN_AFTER_DETOUR_START;
    }

    private List<RoutePoint> currentRoute() {
        if (level() instanceof ServerLevel serverLevel && !getAssignedRouteId().isBlank()) {
            List<RouteStorage.RoutePoint> stored = RouteStorage.get(serverLevel).getRoute(getAssignedRouteId());
            if (!stored.isEmpty()) {
                if (stored != cachedStoredRouteSource) {
                    cachedStoredRouteSource = stored;
                    cachedRoute = stored.stream()
                        .map(p -> new RoutePoint(new Vec3(p.x(), p.y(), p.z()), normalizeWaitTicks(p.waitTicks())))
                        .toList();
                }
                return cachedRoute;
            }
        }
        cachedStoredRouteSource = List.of();
        cachedRoute = List.of();
        return route;
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

    public boolean addPoiPoint(Vec3 pos, int waitSeconds) {
        if (!poiPoints.isEmpty()) {
            return false;
        }
        poiPoints.add(new RoutePoint(pos, Mth.clamp(waitSeconds, 0, 3600) * 20));
        return true;
    }

    public void clearPoiPoints() {
        poiPoints.clear();
        resetPoiState();
        waitTicks = 0;
        resetMovementTracking();
        lastProgressPos = position();
        reengageRouteNavigation();
    }

    public List<String> poiSummary() {
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < poiPoints.size(); i++) {
            RoutePoint point = poiPoints.get(i);
            summary.add("#" + i + " (" + Mth.floor(point.pos().x) + "," + Mth.floor(point.pos().y) + "," + Mth.floor(point.pos().z) + ") wait=" + (point.waitTicks() / 20) + "s");
        }
        return summary;
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

    public void clearRoute() {
        route.clear();
        cachedRoute = List.of();
        cachedStoredRouteSource = List.of();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        routeEnabled = false;
        resetMovementTracking();
        resetPoiState();
        resetNightState();
        closeInteractingDoorIfAny();
        getNavigation().stop();
    }

    public void setRouteEnabled(boolean routeEnabled) {
        this.routeEnabled = routeEnabled;
        if (!routeEnabled) {
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
        entityData.set(SKIN_ID, skinId.toLowerCase(Locale.ROOT));
    }

    public void setNpcId(String npcId) {
        String previousNpcId = getNpcId();
        String normalizedNpcId = npcId.toLowerCase(Locale.ROOT);
        entityData.set(NPC_ID, normalizedNpcId);
        if (!level().isClientSide) {
            NpcRegistry.updateNpcId(this, previousNpcId, normalizedNpcId);
        }
    }

    public String getNpcId() {
        return entityData.get(NPC_ID);
    }

    public void setAssignedRouteId(String routeId) {
        entityData.set(ROUTE_ID, routeId.toLowerCase(Locale.ROOT));
        cachedRoute = List.of();
        cachedStoredRouteSource = List.of();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        resetMovementTracking();
        resetPoiState();
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

    public void setSlimModel(boolean slimModel) {
        entityData.set(SLIM_MODEL, slimModel);
    }

    public boolean isNightModeOnly() {
        return nightModeOnly;
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
        poiReturnRouteIndex = Mth.clamp(poiReturnRouteIndex, 0, points.size() - 1);
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
            + ", routePoints=" + points.size()
            + ", routeIndex=" + routeIndex
            + ", movingForward=" + movingForward
            + ", waitTicks=" + waitTicks
            + ", repathTicks=" + repathTicks
            + ", stuckTicks=" + stuckTicks
            + ", noProgressTicks=" + noProgressTicks
            + ", poiState=" + poiState
            + ", poiIndex=" + poiIndex
            + ", poiCount=" + poiPoints.size()
            + ", poiReturnRouteIndex=" + poiReturnRouteIndex
            + ", poiCheckCooldown=" + poiCheckCooldown
            + ", nightModeOnly=" + nightModeOnly
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

    private void resetPoiState() {
        poiState = PoiState.NONE;
        poiIndex = -1;
    }

    private void resetNightState() {
        nightModeState = NightModeState.NONE;
        nightRefugeIndex = -1;
    }

    private boolean isValidPoiTarget() {
        return poiIndex >= 0 && poiIndex < poiPoints.size();
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
        poiReturnRouteIndex = Mth.clamp(poiReturnRouteIndex, 0, points.size() - 1);
        Vec3 nextCenter = currentTargetPos(points);
        getNavigation().moveTo(nextCenter.x, nextCenter.y, nextCenter.z, 1.0D);
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
        tag.putInt("RouteIndex", routeIndex);
        tag.putBoolean("MovingForward", movingForward);
        tag.putInt("WaitTicks", waitTicks);
        tag.putInt("RepathTicks", repathTicks);
        tag.putInt("StuckTicks", stuckTicks);
        tag.putInt("PoiState", poiState.ordinal());
        tag.putInt("PoiIndex", poiIndex);
        tag.putInt("PoiReturnRouteIndex", poiReturnRouteIndex);
        tag.putInt("PoiCheckCooldown", poiCheckCooldown);
        tag.putInt("NightModeState", nightModeState.ordinal());
        tag.putInt("NightRefugeIndex", nightRefugeIndex);
        tag.putInt("NightReturnRouteIndex", nightReturnRouteIndex);

        ListTag points = new ListTag();
        for (RoutePoint point : route) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", point.pos().x);
            p.putDouble("Y", point.pos().y);
            p.putDouble("Z", point.pos().z);
            p.putInt("Wait", point.waitTicks());
            points.add(p);
        }
        tag.put("Route", points);

        ListTag poiTag = new ListTag();
        for (RoutePoint point : poiPoints) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", point.pos().x);
            p.putDouble("Y", point.pos().y);
            p.putDouble("Z", point.pos().z);
            p.putInt("Wait", point.waitTicks());
            poiTag.add(p);
        }
        tag.put("Pois", poiTag);

        ListTag refugesTag = new ListTag();
        for (RoutePoint point : nightRefugePoints) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", point.pos().x);
            p.putDouble("Y", point.pos().y);
            p.putDouble("Z", point.pos().z);
            p.putInt("Wait", point.waitTicks());
            refugesTag.add(p);
        }
        tag.put("NightRefuges", refugesTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinId(tag.getString("Skin"));
        setNpcId(tag.getString("NpcId"));
        setAssignedRouteId(tag.getString("RouteId"));
        setSlimModel(tag.getBoolean("SlimModel"));
        routeEnabled = tag.getBoolean("RouteEnabled");
        nightModeOnly = tag.getBoolean("NightModeOnly");
        routeIndex = tag.getInt("RouteIndex");
        movingForward = tag.getBoolean("MovingForward");
        waitTicks = tag.getInt("WaitTicks");
        repathTicks = tag.getInt("RepathTicks");
        stuckTicks = tag.getInt("StuckTicks");

        int poiStateIndex = tag.getInt("PoiState");
        poiState = poiStateIndex >= 0 && poiStateIndex < PoiState.values().length ? PoiState.values()[poiStateIndex] : PoiState.NONE;
        poiIndex = tag.getInt("PoiIndex");
        poiReturnRouteIndex = tag.getInt("PoiReturnRouteIndex");
        poiCheckCooldown = tag.getInt("PoiCheckCooldown");

        int nightStateIndex = tag.getInt("NightModeState");
        nightModeState = nightStateIndex >= 0 && nightStateIndex < NightModeState.values().length ? NightModeState.values()[nightStateIndex] : NightModeState.NONE;
        nightRefugeIndex = tag.getInt("NightRefugeIndex");
        nightReturnRouteIndex = tag.getInt("NightReturnRouteIndex");

        route.clear();
        ListTag points = tag.getList("Route", Tag.TAG_COMPOUND);
        for (Tag t : points) {
            CompoundTag p = (CompoundTag) t;
            route.add(new RoutePoint(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")), normalizeWaitTicks(p.getInt("Wait"))));
        }
        if (!route.isEmpty()) {
            routeIndex = Mth.clamp(routeIndex, 0, route.size() - 1);
        } else {
            routeIndex = 0;
        }

        poiPoints.clear();
        ListTag pois = tag.getList("Pois", Tag.TAG_COMPOUND);
        for (Tag t : pois) {
            CompoundTag p = (CompoundTag) t;
            poiPoints.add(new RoutePoint(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")), normalizeWaitTicks(p.getInt("Wait"))));
        }
        if (poiPoints.size() > 1) {
            RoutePoint firstPoi = poiPoints.getFirst();
            poiPoints.clear();
            poiPoints.add(firstPoi);
        }

        if (poiPoints.isEmpty() || poiIndex < 0 || poiIndex >= poiPoints.size()) {
            poiState = PoiState.NONE;
            poiIndex = -1;
        }

        nightRefugePoints.clear();
        ListTag refuges = tag.getList("NightRefuges", Tag.TAG_COMPOUND);
        for (Tag t : refuges) {
            CompoundTag p = (CompoundTag) t;
            nightRefugePoints.add(new RoutePoint(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")), normalizeWaitTicks(p.getInt("Wait"))));
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

    private enum PoiState {
        NONE,
        TO_POI,
        AT_POI,
        RETURNING
    }

    private enum NightModeState {
        NONE,
        GOING_TO_REFUGE,
        AT_REFUGE,
        RETURNING_TO_ROUTE
    }

    private record RoutePoint(Vec3 pos, int waitTicks) {
    }
}
