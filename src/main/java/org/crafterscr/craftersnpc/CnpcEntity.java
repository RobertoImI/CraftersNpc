package org.crafterscr.craftersnpc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class CnpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NPC_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ROUTE_ID = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM_MODEL = SynchedEntityData.defineId(CnpcEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<RoutePoint> route = new ArrayList<>();
    private int routeIndex;
    private boolean movingForward = true;
    private int waitTicks;
    private boolean routeEnabled;

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
        if (!routeEnabled) {
            return;
        }

        List<RoutePoint> points = currentRoute();
        if (points.isEmpty()) {
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            getNavigation().stop();
            return;
        }

        RoutePoint point = points.get(routeIndex);
        Vec3 center = point.pos().add(0.5D, 0.0D, 0.5D);
        if (distanceToSqr(center) <= 1.2D) {
            waitTicks = point.waitTicks();
            advanceIndex(points.size());
        } else if (getNavigation().isDone()) {
            getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
        }
    }

    private List<RoutePoint> currentRoute() {
        if (level() instanceof ServerLevel serverLevel && !getAssignedRouteId().isBlank()) {
            List<RouteStorage.RoutePoint> stored = RouteStorage.get(serverLevel).getRoute(getAssignedRouteId());
            if (!stored.isEmpty()) {
                return stored.stream()
                    .map(p -> new RoutePoint(new Vec3(p.x(), p.y(), p.z()), p.waitTicks()))
                    .toList();
            }
        }
        return route;
    }

    private void advanceIndex(int size) {
        if (size <= 1) {
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

    public void addRoutePoint(Vec3 pos, int waitSeconds) {
        route.add(new RoutePoint(pos, Mth.clamp(waitSeconds, 0, 3600) * 20));
    }

    public void clearRoute() {
        route.clear();
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
        routeEnabled = false;
        getNavigation().stop();
    }

    public void setRouteEnabled(boolean routeEnabled) {
        this.routeEnabled = routeEnabled;
    }

    public String getSkinId() {
        return entityData.get(SKIN_ID);
    }

    public void setSkinId(String skinId) {
        entityData.set(SKIN_ID, skinId.toLowerCase());
    }

    public void setNpcId(String npcId) {
        entityData.set(NPC_ID, npcId.toLowerCase());
    }

    public String getNpcId() {
        return entityData.get(NPC_ID);
    }

    public void setAssignedRouteId(String routeId) {
        entityData.set(ROUTE_ID, routeId.toLowerCase());
        routeIndex = 0;
        movingForward = true;
        waitTicks = 0;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", getSkinId());
        tag.putString("NpcId", getNpcId());
        tag.putString("RouteId", getAssignedRouteId());
        tag.putBoolean("SlimModel", isSlimModel());
        tag.putBoolean("RouteEnabled", routeEnabled);
        tag.putInt("RouteIndex", routeIndex);
        tag.putBoolean("MovingForward", movingForward);
        tag.putInt("WaitTicks", waitTicks);

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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinId(tag.getString("Skin"));
        setNpcId(tag.getString("NpcId"));
        setAssignedRouteId(tag.getString("RouteId"));
        setSlimModel(tag.getBoolean("SlimModel"));
        routeEnabled = tag.getBoolean("RouteEnabled");
        routeIndex = tag.getInt("RouteIndex");
        movingForward = tag.getBoolean("MovingForward");
        waitTicks = tag.getInt("WaitTicks");

        route.clear();
        ListTag points = tag.getList("Route", Tag.TAG_COMPOUND);
        for (Tag t : points) {
            CompoundTag p = (CompoundTag) t;
            route.add(new RoutePoint(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")), p.getInt("Wait")));
        }
        if (!route.isEmpty()) {
            routeIndex = Mth.clamp(routeIndex, 0, route.size() - 1);
        } else {
            routeIndex = 0;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
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

    private record RoutePoint(Vec3 pos, int waitTicks) {
    }
}
