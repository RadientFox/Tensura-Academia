package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ElasticityQuirk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ElasticityBouncePayload() implements CustomPacketPayload {
    public static final Type<ElasticityBouncePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "elasticity_bounce"));
    public static final StreamCodec<FriendlyByteBuf, ElasticityBouncePayload> STREAM_CODEC =
            CustomPacketPayload.codec(ElasticityBouncePayload::encode, ElasticityBouncePayload::new);

    public ElasticityBouncePayload(FriendlyByteBuf buffer) {
        this();
    }

    private void encode(FriendlyByteBuf buffer) {
    }

    public static void handle(ElasticityBouncePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ElasticityQuirk.tryAerialBounce(player);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
