package org.crafterscr.craftersnpc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WandWaitAdjustPayload(int delta) implements CustomPacketPayload {
    public static final Type<WandWaitAdjustPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CraftersNpc.MODID, "wand_wait_adjust"));
    public static final StreamCodec<FriendlyByteBuf, WandWaitAdjustPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.delta()),
            buf -> new WandWaitAdjustPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WandWaitAdjustPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int delta = Integer.signum(payload.delta());
        if (delta == 0) {
            return;
        }

        context.enqueueWork(() -> RouteWandManager.adjustWaitTime(serverPlayer, delta));
    }
}
