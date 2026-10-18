package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.SlideAndGlideClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SlideAndGlideStatePayload(int entityId, int flags) implements CustomPacketPayload {
    public static final Type<SlideAndGlideStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_state"));
    public static final StreamCodec<FriendlyByteBuf, SlideAndGlideStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(SlideAndGlideStatePayload::encode, SlideAndGlideStatePayload::new);

    public SlideAndGlideStatePayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(flags);
    }

    public static void handle(SlideAndGlideStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SlideAndGlideClient.setFlags(payload.entityId, payload.flags));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
