package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.client.gui.NpcEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenNpcEditorPayload(int entityId, String npcId, String skinId, boolean slimModel, double speed,
                                   String temperament, String routeId, boolean nightMode, boolean damageEnabled) implements CustomPacketPayload {
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
            },
            buf -> new OpenNpcEditorPayload(buf.readInt(), buf.readUtf(64), buf.readUtf(64), buf.readBoolean(), buf.readDouble(),
                    buf.readUtf(32), buf.readUtf(64), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcEditorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NpcEditorScreen(payload.entityId(), payload.npcId(), payload.skinId(),
                payload.slimModel(), payload.speed(), payload.temperament(), payload.routeId(), payload.nightMode(), payload.damageEnabled())));
    }
}
