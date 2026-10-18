package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.SlideAndGlideClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SlideAndGlideBurstPayload(int kind, Vec3 pos, Vec3 dir) implements CustomPacketPayload {
    public static final int KNUCKLE_BLAST = 0;
    public static final int SLAP_REPEL = 1;
    public static final int THRUST_FLASH = 2;

    public static final Type<SlideAndGlideBurstPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_burst"));
    public static final StreamCodec<FriendlyByteBuf, SlideAndGlideBurstPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SlideAndGlideBurstPayload::encode, SlideAndGlideBurstPayload::new);

    public SlideAndGlideBurstPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVec3(), buffer.readVec3());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(kind);
        buffer.writeVec3(pos);
        buffer.writeVec3(dir);
    }

    public static void handle(SlideAndGlideBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SlideAndGlideClient.addBurst(payload.kind, payload.pos, payload.dir));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
