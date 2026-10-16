package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.WarpGateScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record WarpGateOpenPayload(long token, List<String> dimensions) implements CustomPacketPayload {
    public static final Type<WarpGateOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "warp_gate_open"));
    public static final StreamCodec<FriendlyByteBuf, WarpGateOpenPayload> STREAM_CODEC =
            CustomPacketPayload.codec(WarpGateOpenPayload::encode, WarpGateOpenPayload::new);

    public WarpGateOpenPayload(FriendlyByteBuf buffer) {
        this(buffer.readLong(), readDimensions(buffer));
    }

    private static List<String> readDimensions(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), 256);
        List<String> dimensions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) dimensions.add(buffer.readUtf(128));
        return dimensions;
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(token);
        buffer.writeVarInt(dimensions.size());
        for (String dimension : dimensions) buffer.writeUtf(dimension, 128);
    }

    public static void handle(WarpGateOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WarpGateScreen.open(payload));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
