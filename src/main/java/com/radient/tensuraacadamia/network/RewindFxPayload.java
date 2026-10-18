package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.RewindClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RewindFxPayload(int ownerId, int targetId) implements CustomPacketPayload {
    public static final int STOP = -1;

    public static final Type<RewindFxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "rewind_fx"));
    public static final StreamCodec<FriendlyByteBuf, RewindFxPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RewindFxPayload::encode, RewindFxPayload::new);

    public RewindFxPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(ownerId);
        buffer.writeInt(targetId);
    }

    public static void handle(RewindFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RewindClient.set(payload.ownerId, payload.targetId));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
