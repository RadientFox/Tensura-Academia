package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.WarpGateQuirk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WarpGateDestinationPayload(long token, String dimension, int x, int y, int z)
        implements CustomPacketPayload {
    public static final Type<WarpGateDestinationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "warp_gate_destination"));
    public static final StreamCodec<FriendlyByteBuf, WarpGateDestinationPayload> STREAM_CODEC =
            CustomPacketPayload.codec(WarpGateDestinationPayload::encode, WarpGateDestinationPayload::new);

    public WarpGateDestinationPayload(FriendlyByteBuf buffer) {
        this(buffer.readLong(), buffer.readUtf(128), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(token);
        buffer.writeUtf(dimension, 128);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }

    public static void handle(WarpGateDestinationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                WarpGateQuirk.openGate(player, payload.token, payload.dimension,
                        payload.x, payload.y, payload.z);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
