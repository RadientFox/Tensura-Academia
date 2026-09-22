package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.AcceleratorRingsFlightClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record AcceleratorRingsFlightPayload(int entityId, int duration) implements CustomPacketPayload {
    public static final Type<AcceleratorRingsFlightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "accelerator_rings_flight"));
    public static final StreamCodec<FriendlyByteBuf, AcceleratorRingsFlightPayload> STREAM_CODEC =
            CustomPacketPayload.codec(AcceleratorRingsFlightPayload::encode, AcceleratorRingsFlightPayload::new);

    public AcceleratorRingsFlightPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeInt(duration);
    }

    public static void handle(AcceleratorRingsFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AcceleratorRingsFlightClient.setActive(payload.entityId, payload.duration));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
