package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.client.OverhaulClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OverhaulStatePayload(int entityId, CompoundTag state) implements CustomPacketPayload {
    public static final Type<OverhaulStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("tracadamia", "overhaul_state"));
    public static final StreamCodec<FriendlyByteBuf, OverhaulStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(OverhaulStatePayload::encode, OverhaulStatePayload::new);

    private OverhaulStatePayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readNbt());
    }
    private void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(entityId); buffer.writeNbt(state); }
    public static void handle(OverhaulStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OverhaulClient.receive(payload.entityId, payload.state));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
