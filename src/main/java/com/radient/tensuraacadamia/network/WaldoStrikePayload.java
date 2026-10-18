package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.WaldoClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record WaldoStrikePayload(int entityId, int leg, int targetId) implements CustomPacketPayload {
    public static final Type<WaldoStrikePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "waldo_strike"));
    public static final StreamCodec<FriendlyByteBuf, WaldoStrikePayload> STREAM_CODEC =
            CustomPacketPayload.codec(WaldoStrikePayload::encode, WaldoStrikePayload::new);

    public WaldoStrikePayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt(), buffer.readInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(leg);
        buffer.writeInt(targetId);
    }

    public static void handle(WaldoStrikePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WaldoClient.strike(payload.entityId, payload.leg, payload.targetId));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
