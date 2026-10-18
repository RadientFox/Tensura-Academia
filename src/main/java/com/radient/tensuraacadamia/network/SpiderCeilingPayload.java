package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.client.SpiderCeilingClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SpiderCeilingPayload(int entityId, int kind) implements CustomPacketPayload {
    public static final int NONE = 0;
    public static final int CRAWL = 1;
    public static final int HANG = 2;
    public static final int DROP = 3;

    public static final Type<SpiderCeilingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spider_ceiling"));
    public static final StreamCodec<FriendlyByteBuf, SpiderCeilingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpiderCeilingPayload::encode, SpiderCeilingPayload::new);

    public SpiderCeilingPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(kind);
    }

    public static void handle(SpiderCeilingPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    SpiderQuirk.receiveCeiling(player, payload.kind);
                }
            });
        } else {
            context.enqueueWork(() -> SpiderCeilingClient.receive(payload.entityId, payload.kind));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
