package com.radient.tensuraacadamia.ability.ultimate.ofa;

import com.radient.tensuraacadamia.TensuraAcadamia;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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

    public void handle(NetworkManager.PacketContext context) {
        if (context.getEnvironment() == Env.CLIENT) {
            context.queue(() -> com.radient.tensuraacadamia.ability.ultimate.ofa.GearshiftBlueTrailClient.enable(this.entityId, this.duration, this.gear));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}