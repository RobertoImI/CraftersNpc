package org.crafterscr.craftersnpc.route;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RouteWandManager {
    private static final String WAND_KEY = "cnpc_wand_item";
    private static final double PREVIEW_MAX_DISTANCE_SQR = 96.0D * 96.0D;
    private static final Map<UUID, BuildSession> BUILD_SESSIONS = new HashMap<>();
    private static final Set<UUID> ROUTES_PREVIEW_ENABLED = new java.util.HashSet<>();

    private RouteWandManager() {
    }

    public static void startSession(ServerPlayer player, String routeId, List<RouteStorage.RoutePoint> existing) {
        BUILD_SESSIONS.put(player.getUUID(), new BuildSession(routeId, new ArrayList<>(existing)));
    }

    public static Optional<BuildSession> session(ServerPlayer player) {
        return Optional.ofNullable(BUILD_SESSIONS.get(player.getUUID()));
    }

    public static void clearSession(ServerPlayer player) {
        BUILD_SESSIONS.remove(player.getUUID());
    }

    public static void clearPlayerState(Player player) {
        UUID uuid = player.getUUID();
        BUILD_SESSIONS.remove(uuid);
        ROUTES_PREVIEW_ENABLED.remove(uuid);
    }

    public static boolean toggleAllRoutesPreview(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (ROUTES_PREVIEW_ENABLED.contains(uuid)) {
            ROUTES_PREVIEW_ENABLED.remove(uuid);
            return false;
        }
        ROUTES_PREVIEW_ENABLED.add(uuid);
        return true;
    }

    public static boolean isAllRoutesPreviewEnabled(ServerPlayer player) {
        return ROUTES_PREVIEW_ENABLED.contains(player.getUUID());
    }

    public static boolean hasWandInHand(ServerPlayer player) {
        return isWandStack(player, player.getMainHandItem());
    }

    private static boolean isWandStack(ServerPlayer player, ItemStack stack) {
        String wandItem = player.getPersistentData().getString(WAND_KEY);
        if (wandItem.isBlank() || stack.isEmpty()) {
            return false;
        }
        ResourceLocation held = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return held != null && wandItem.equals(held.toString());
    }

    public static void setWand(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        player.getPersistentData().putString(WAND_KEY, id == null ? "" : id.toString());
    }

    public static void clearWand(ServerPlayer player) {
        player.getPersistentData().remove(WAND_KEY);
    }

    public static String getWandItemId(ServerPlayer player) {
        return player.getPersistentData().getString(WAND_KEY);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!player.hasPermissions(2) || !hasWandInHand(player)) {
            return;
        }
        BuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        event.setCanceled(true);
        BlockPos clicked = event.getPos();
        Vec3 point = Vec3.atBottomCenterOf(clicked.above());
        if (player.isShiftKeyDown()) {
            if (!session.points().isEmpty()) {
                session.points().remove(session.points().size() - 1);
                player.sendSystemMessage(Component.literal("Último punto eliminado de ruta " + session.routeId()));
            }
            return;
        }

        int waitTicks = session.getSelectedWaitSeconds() * 20;
        session.points().add(new RouteStorage.RoutePoint(point.x, point.y, point.z, waitTicks));
        player.sendSystemMessage(Component.literal("Punto agregado a ruta " + session.routeId() + " (#" + session.points().size() + ") espera " + session.getSelectedWaitSeconds() + "s"));
    }


    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % 10 == 0 && player.hasPermissions(2) && hasWandInHand(player)) {
            renderAllSavedRoutes(player);
        }

        BuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (player.tickCount % 10 == 0 && player.hasPermissions(2) && hasWandInHand(player)) {
            player.displayClientMessage(Component.literal("Editando ruta " + session.routeId() + " | espera: " + session.getSelectedWaitSeconds() + "s | puntos: " + session.points().size()), true);
        }

        if (player.tickCount % 10 != 0 || !player.hasPermissions(2) || !hasWandInHand(player) || session.points().isEmpty()) {
            return;
        }

        for (int i = 0; i < session.points().size(); i++) {
            RouteStorage.RoutePoint current = session.points().get(i);
            if (!isWithinPreviewRange(player, current.x(), current.y(), current.z())) {
                continue;
            }
            sendParticle(player, current.x(), current.y(), current.z());
            if (i > 0) {
                RouteStorage.RoutePoint prev = session.points().get(i - 1);
                drawSegment(player, prev, current);
            }
        }
    }

    private static void renderAllSavedRoutes(ServerPlayer player) {
        if (!isAllRoutesPreviewEnabled(player)) {
            return;
        }
        RouteStorage storage = RouteStorage.get(player.serverLevel());
        for (List<RouteStorage.RoutePoint> routePoints : storage.allRoutes().values()) {
            for (int i = 0; i < routePoints.size(); i++) {
                RouteStorage.RoutePoint current = routePoints.get(i);
                if (!isWithinPreviewRange(player, current.x(), current.y(), current.z())) {
                    continue;
                }
                sendParticle(player, current.x(), current.y(), current.z());
                if (i > 0) {
                    drawSegment(player, routePoints.get(i - 1), current);
                }
            }
        }
    }

    public static void adjustWaitTime(ServerPlayer player, int delta) {
        BuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null || !player.hasPermissions(2) || !player.isShiftKeyDown() || !hasWandInHand(player)) {
            return;
        }

        session.adjustSelectedWaitSeconds(delta);
        player.displayClientMessage(Component.literal("Espera por punto: " + session.getSelectedWaitSeconds() + "s"), true);
    }


    private static void drawSegment(ServerPlayer player, RouteStorage.RoutePoint a, RouteStorage.RoutePoint b) {
        if (!isWithinPreviewRange(player, a.x(), a.y(), a.z()) && !isWithinPreviewRange(player, b.x(), b.y(), b.z())) {
            return;
        }
        Vec3 start = new Vec3(a.x(), a.y(), a.z());
        Vec3 end = new Vec3(b.x(), b.y(), b.z());
        double distance = start.distanceTo(end);
        int steps = Math.max(2, (int) (distance * 4));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 pos = start.lerp(end, t);
            sendParticle(player, pos.x, pos.y, pos.z);
        }
    }

    private static void sendParticle(ServerPlayer player, double x, double y, double z) {
        player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, false, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
    }

    private static boolean isWithinPreviewRange(ServerPlayer player, double x, double y, double z) {
        return player.distanceToSqr(x, y, z) <= PREVIEW_MAX_DISTANCE_SQR;
    }

    public static final class BuildSession {
        private final String routeId;
        private final List<RouteStorage.RoutePoint> points;
        private int selectedWaitSeconds;

        public BuildSession(String routeId, List<RouteStorage.RoutePoint> points) {
            this.routeId = routeId;
            this.points = points;
            this.selectedWaitSeconds = 0;
        }

        public String routeId() {
            return routeId;
        }

        public List<RouteStorage.RoutePoint> points() {
            return points;
        }

        public int getSelectedWaitSeconds() {
            return selectedWaitSeconds;
        }

        public void adjustSelectedWaitSeconds(int delta) {
            selectedWaitSeconds = Mth.clamp(selectedWaitSeconds + delta, 0, 3600);
        }
    }
}
