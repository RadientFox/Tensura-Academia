package com.radient.tensuraacadamia.ability.ultimate.ofa;

import com.radient.tensuraacadamia.TensuraAcadamia;
import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record FullCowlLightningPayload(int entityId, int duration, int percent, boolean instantLightning, boolean hundredPercentUnlocked) implements CustomPacketPayload {
    public static final Type<FullCowlLightningPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "full_cowl_lightning"));
    public static final StreamCodec<FriendlyByteBuf, FullCowlLightningPayload> STREAM_CODEC = CustomPacketPayload.codec(FullCowlLightningPayload::encode, FullCowlLightningPayload::new);

    public FullCowlLightningPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.duration);
        buf.writeInt(this.percent);
        buf.writeBoolean(this.instantLightning);
        buf.writeBoolean(this.hundredPercentUnlocked);
    }

    public void handle(NetworkManager.PacketContext context) {
        if (context.getEnvironment() == Env.CLIENT) {
            context.queue(() -> FullCowlLightningClient.enable(this.entityId, this.duration, this.percent, this.instantLightning, this.hundredPercentUnlocked));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}