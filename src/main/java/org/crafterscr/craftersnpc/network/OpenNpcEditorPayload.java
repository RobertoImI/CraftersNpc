package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenNpcEditorPayload(int entityId, String npcId, String skinId, boolean slimModel, double speed,
                                   String temperament, String routeId, boolean nightMode, boolean damageEnabled, java.util.List<String> skinIds, java.util.List<String> routeIds) implements CustomPacketPayload {
    public static final Type<OpenNpcEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "open_npc_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeUtf(payload.npcId(), 64);
                buf.writeUtf(payload.skinId(), 64);
                buf.writeBoolean(payload.slimModel());
                buf.writeDouble(payload.speed());
                buf.writeUtf(payload.temperament(), 32);
                buf.writeUtf(payload.routeId(), 64);
                buf.writeBoolean(payload.nightMode());
                buf.writeBoolean(payload.damageEnabled());
                buf.writeVarInt(payload.skinIds().size());
                for (String skinId : payload.skinIds()) buf.writeUtf(skinId, 64);
                buf.writeVarInt(payload.routeIds().size());
                for (String routeId : payload.routeIds()) buf.writeUtf(routeId, 64);
            },
            buf -> {
                int entityId = buf.readInt();
                String npcId = buf.readUtf(64);
                String skinId = buf.readUtf(64);
                boolean slimModel = buf.readBoolean();
                double speed = buf.readDouble();
                String temperament = buf.readUtf(32);
                String routeId = buf.readUtf(64);
                boolean nightMode = buf.readBoolean();
                boolean damageEnabled = buf.readBoolean();
                int skinSize = Math.min(buf.readVarInt(), 512);
                java.util.List<String> skinIds = new java.util.ArrayList<>(skinSize);
                for (int index = 0; index < skinSize; index++) skinIds.add(buf.readUtf(64));
                int routeSize = Math.min(buf.readVarInt(), 512);
                java.util.List<String> routeIds = new java.util.ArrayList<>(routeSize);
                for (int index = 0; index < routeSize; index++) routeIds.add(buf.readUtf(64));
                return new OpenNpcEditorPayload(entityId, npcId, skinId, slimModel, speed, temperament, routeId, nightMode, damageEnabled, skinIds, routeIds);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
