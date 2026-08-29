package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.NpcRegistry;
import org.crafterscr.craftersnpc.route.RouteStorage;
import org.crafterscr.craftersnpc.storage.NpcSettingsStorage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

public record SaveNpcEditorPayload(int entityId, String npcId, String npcName, String skinId, boolean slimModel, double speed,
                                   String temperament, String routeId, boolean nightMode, boolean damageEnabled) implements CustomPacketPayload {
    private static final int MAX_ID_LENGTH = 64;
    public static final Type<SaveNpcEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "save_npc_editor"));
    public static final StreamCodec<FriendlyByteBuf, SaveNpcEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.npcId(), MAX_ID_LENGTH);
                buf.writeUtf(payload.npcName(), CnpcEntity.MAX_DISPLAY_NAME_LENGTH);
                buf.writeUtf(payload.skinId(), MAX_ID_LENGTH);
                buf.writeBoolean(payload.slimModel());
                buf.writeDouble(payload.speed());
                buf.writeUtf(payload.temperament(), 32);
                buf.writeUtf(payload.routeId(), MAX_ID_LENGTH);
                buf.writeBoolean(payload.nightMode());
                buf.writeBoolean(payload.damageEnabled());
            },
            buf -> new SaveNpcEditorPayload(buf.readInt(), buf.readUtf(MAX_ID_LENGTH), buf.readUtf(CnpcEntity.MAX_DISPLAY_NAME_LENGTH), buf.readUtf(MAX_ID_LENGTH), buf.readBoolean(), buf.readDouble(),
                    buf.readUtf(32), buf.readUtf(MAX_ID_LENGTH), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveNpcEditorPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> apply(payload, player));
    }

    private static void apply(SaveNpcEditorPayload payload, ServerPlayer player) {
        if (!player.hasPermissions(2) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof CnpcEntity npc) || npc.distanceToSqr(player) > 100.0D) {
            player.sendSystemMessage(Component.literal("NPC no válido o demasiado lejos."));
            return;
        }

        String newNpcId = sanitizeId(payload.npcId());
        if (newNpcId.isBlank()) {
            player.sendSystemMessage(Component.literal("El ID del NPC no puede estar vacío."));
            return;
        }
        if (NpcRegistry.findById(player.getServer(), newNpcId).filter(existing -> existing.getId() != npc.getId()).isPresent()) {
            player.sendSystemMessage(Component.literal("Ya existe un NPC con ID: " + newNpcId));
            return;
        }

        String routeId = sanitizeId(payload.routeId());
        if (!routeId.isBlank() && !RouteStorage.get(level).hasRoute(routeId)) {
            player.sendSystemMessage(Component.literal("Ruta no encontrada: " + routeId));
            return;
        }

        npc.setNpcId(newNpcId);
        npc.setNpcName(payload.npcName());
        String skinId = sanitizeId(payload.skinId());
        npc.setSkinId(skinId.isBlank() ? "steve" : skinId);
        npc.setSlimModel(payload.slimModel());
        npc.setWalkSpeed(Double.isFinite(payload.speed()) ? Mth.clamp(payload.speed(), 0.05D, 1.0D) : CnpcEntity.DEFAULT_WALK_SPEED);
        npc.setTemperament(CnpcEntity.Temperament.fromId(payload.temperament()));
        npc.setAssignedRouteId(routeId);
        npc.setRouteEnabled(!routeId.isBlank());
        npc.setNightModeOnly(payload.nightMode());
        NpcSettingsStorage.get(level).setNpcDamageEnabled(payload.damageEnabled());
        player.sendSystemMessage(Component.literal("NPC actualizado desde la GUI: " + npc.getNpcId()));
    }

    private static String sanitizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "");
    }
}
