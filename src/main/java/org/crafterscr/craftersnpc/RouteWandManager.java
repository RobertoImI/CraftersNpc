package org.crafterscr.craftersnpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import java.util.UUID;

public final class RouteWandManager {
    private static final String WAND_KEY = "cnpc_wand_item";
    private static final Map<UUID, BuildSession> BUILD_SESSIONS = new HashMap<>();

    private RouteWandManager() {
    }

    public static void startSession(ServerPlayer player, String routeId, List<RouteStorage.RoutePoint> existing) {
        BUILD_SESSIONS.put(player.getUUID(), new BuildSession(routeId, new ArrayList<>(existing), player.getInventory().selected));
    }

    public static Optional<BuildSession> session(ServerPlayer player) {
        return Optional.ofNullable(BUILD_SESSIONS.get(player.getUUID()));
    }

    public static void clearSession(ServerPlayer player) {
        BUILD_SESSIONS.remove(player.getUUID());
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
        Vec3 point = Vec3.atCenterOf(clicked.above());
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

    private static int scrollDirection(int previousSlot, int newSlot) {
        int forward = Math.floorMod(newSlot - previousSlot, 9);
        int backward = Math.floorMod(previousSlot - newSlot, 9);

        if (forward == 0) {
            return 0;
        }
        return forward <= backward ? 1 : -1;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        int currentSlot = player.getInventory().selected;
        if (session.lastSelectedSlot() != currentSlot) {
            handleSlotScroll(player, session, currentSlot);
        }

        if (player.tickCount % 10 == 0 && player.hasPermissions(2) && hasWandInHand(player)) {
            player.displayClientMessage(Component.literal("Editando ruta " + session.routeId() + " | espera: " + session.getSelectedWaitSeconds() + "s | puntos: " + session.points().size()), true);
        }

        if (player.tickCount % 10 != 0 || !player.hasPermissions(2) || !hasWandInHand(player) || session.points().isEmpty()) {
            return;
        }

        for (int i = 0; i < session.points().size(); i++) {
            RouteStorage.RoutePoint current = session.points().get(i);
            sendParticle(player, current.x(), current.y(), current.z());
            if (i > 0) {
                RouteStorage.RoutePoint prev = session.points().get(i - 1);
                drawSegment(player, prev, current);
            }
        }
    }

    private static void handleSlotScroll(ServerPlayer player, BuildSession session, int currentSlot) {
        int previousSlot = session.lastSelectedSlot();
        session.setLastSelectedSlot(currentSlot);
        if (!player.hasPermissions(2) || !player.isShiftKeyDown()) {
            return;
        }

        ItemStack previousStack = player.getInventory().getItem(previousSlot);
        if (!isWandStack(player, previousStack)) {
            return;
        }

        int direction = scrollDirection(previousSlot, currentSlot);
        if (direction == 0) {
            return;
        }

        player.getInventory().selected = previousSlot;
        session.adjustSelectedWaitSeconds(-direction);
        player.displayClientMessage(Component.literal("Espera por punto: " + session.getSelectedWaitSeconds() + "s"), true);
    }

    private static void drawSegment(ServerPlayer player, RouteStorage.RoutePoint a, RouteStorage.RoutePoint b) {
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

    public static final class BuildSession {
        private final String routeId;
        private final List<RouteStorage.RoutePoint> points;
        private int selectedWaitSeconds;
        private int lastSelectedSlot;

        public BuildSession(String routeId, List<RouteStorage.RoutePoint> points, int lastSelectedSlot) {
            this.routeId = routeId;
            this.points = points;
            this.selectedWaitSeconds = 0;
            this.lastSelectedSlot = lastSelectedSlot;
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

        public int lastSelectedSlot() {
            return lastSelectedSlot;
        }

        public void setLastSelectedSlot(int lastSelectedSlot) {
            this.lastSelectedSlot = lastSelectedSlot;
        }

        public void adjustSelectedWaitSeconds(int delta) {
            selectedWaitSeconds = Mth.clamp(selectedWaitSeconds + delta, 0, 3600);
        }
    }
}
