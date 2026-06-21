package org.crafterscr.craftersnpc.network;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.client.ClientReputationIndicators;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReputationIndicatorPayload(int entityId, int reputationDelta, int currentReputation, int durationTicks) implements CustomPacketPayload {
    public static final Type<ReputationIndicatorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "reputation_indicator"));
    public static final StreamCodec<FriendlyByteBuf, ReputationIndicatorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeInt(payload.reputationDelta());
                buf.writeInt(payload.currentReputation());
                buf.writeInt(payload.durationTicks());
            },
            buf -> new ReputationIndicatorPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReputationIndicatorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientReputationIndicators.show(
                payload.entityId(),
                payload.reputationDelta(),
                payload.currentReputation(),
                payload.durationTicks()
        ));
    }
}
