package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.PermeationPhaseClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PermeationPhasePayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<PermeationPhasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "permeation_phase"));
    public static final StreamCodec<FriendlyByteBuf, PermeationPhasePayload> STREAM_CODEC =
            CustomPacketPayload.codec(PermeationPhasePayload::encode, PermeationPhasePayload::new);

    public PermeationPhasePayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readBoolean());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeBoolean(active);
    }

    public static void handle(PermeationPhasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PermeationPhaseClient.setActive(payload.entityId, payload.active));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
