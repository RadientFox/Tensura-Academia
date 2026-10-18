package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.QuirkAnimClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record QuirkAnimPayload(int entityId, int anim, boolean on) implements CustomPacketPayload {
    public static final int SPIDER_SHOT = 1;
    public static final int SPIDER_TRIP = 2;
    public static final int SPIDER_BLOSSOM = 3;
    public static final int SPIDER_CRAWL = 4;
    public static final int SPIDER_FLIP = 5;
    public static final int SPIDER_WALDOS = 6;
    public static final int WINGS_LAUNCH = 10;
    public static final int WINGS_BOOST = 11;
    public static final int WINGS_CANNON = 12;
    public static final int WINGS_BLADE = 13;
    public static final int WINGS_FEATHER_BOOST = 14;

    public static final Type<QuirkAnimPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "quirk_anim"));
    public static final StreamCodec<FriendlyByteBuf, QuirkAnimPayload> STREAM_CODEC =
            CustomPacketPayload.codec(QuirkAnimPayload::encode, QuirkAnimPayload::new);

    public QuirkAnimPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(anim);
        buffer.writeBoolean(on);
    }

    public static void play(Entity entity, int anim) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new QuirkAnimPayload(entity.getId(), anim, true));
    }

    public static void state(Entity entity, int anim, boolean on) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new QuirkAnimPayload(entity.getId(), anim, on));
    }

    public static void stateTo(ServerPlayer viewer, Entity entity, int anim) {
        PacketDistributor.sendToPlayer(viewer, new QuirkAnimPayload(entity.getId(), anim, true));
    }

    public static void handle(QuirkAnimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> QuirkAnimClient.receive(payload.entityId, payload.anim, payload.on));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
