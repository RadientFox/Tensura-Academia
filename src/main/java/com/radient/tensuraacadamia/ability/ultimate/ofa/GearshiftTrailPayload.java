package com.radient.tensuraacadamia.ability.ultimate.ofa;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record GearshiftTrailPayload(int entityId, int duration, int gear) implements CustomPacketPayload {
    public static final Type<GearshiftTrailPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "gearshift_trail"));
    public static final StreamCodec<FriendlyByteBuf, GearshiftTrailPayload> STREAM_CODEC = CustomPacketPayload.codec(GearshiftTrailPayload::encode, GearshiftTrailPayload::new);

    public GearshiftTrailPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.duration);
        buf.writeInt(this.gear);
    }

    public static void handle(GearshiftTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.duration <= 0) {
                GearshiftBlueTrailClient.disable(payload.entityId);
            } else {
                GearshiftBlueTrailClient.enable(payload.entityId, payload.duration, payload.gear);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
