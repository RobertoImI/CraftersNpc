package org.crafterscr.craftersnpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        BUILD_SESSIONS.put(player.getUUID(), new BuildSession(routeId, new ArrayList<>(existing)));
    }

    public static Optional<BuildSession> session(ServerPlayer player) {
        return Optional.ofNullable(BUILD_SESSIONS.get(player.getUUID()));
    }

    public static void clearSession(ServerPlayer player) {
        BUILD_SESSIONS.remove(player.getUUID());
    }

    public static boolean hasWandInHand(ServerPlayer player) {
        String wandItem = player.getPersistentData().getString(WAND_KEY);
        if (wandItem.isBlank()) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
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
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Último punto eliminado de ruta " + session.routeId()));
            }
            return;
        }
        session.points().add(new RouteStorage.RoutePoint(point.x, point.y, point.z, 0));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Punto agregado a ruta " + session.routeId() + " (#" + session.points().size() + ")"));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 10 != 0 || !player.hasPermissions(2) || !hasWandInHand(player)) {
            return;
        }
        BuildSession session = BUILD_SESSIONS.get(player.getUUID());
        if (session == null || session.points().isEmpty()) {
            return;
        }

        Level level = player.level();
        for (int i = 0; i < session.points().size(); i++) {
            RouteStorage.RoutePoint current = session.points().get(i);
            sendParticle(player, current.x(), current.y(), current.z());
            if (i > 0) {
                RouteStorage.RoutePoint prev = session.points().get(i - 1);
                drawSegment(player, prev, current);
            }
        }
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

    public record BuildSession(String routeId, List<RouteStorage.RoutePoint> points) {
    }
}
