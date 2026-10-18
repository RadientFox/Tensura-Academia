package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.client.SpiderClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SpiderSwingPayload(int entityId, int state, Vec3 anchor) implements CustomPacketPayload {
    public static final int DETACH = 0;
    public static final int ATTACH = 1;
    public static final int RELEASE = 2;
    public static final int LEAP = 3;
    public static final int ZIP = 4;

    public static final Type<SpiderSwingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spider_swing"));
    public static final StreamCodec<FriendlyByteBuf, SpiderSwingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpiderSwingPayload::encode, SpiderSwingPayload::new);

    public SpiderSwingPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt(), buffer.readVec3());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(state);
        buffer.writeVec3(anchor);
    }

    public static void handle(SpiderSwingPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    switch (payload.state) {
                        case DETACH -> SpiderQuirk.release(player, DETACH);
                        case LEAP -> SpiderQuirk.release(player, LEAP);
                        case ZIP -> SpiderQuirk.zip(player);
                        default -> {
                        }
                    }
                }
            });
        } else {
            context.enqueueWork(() -> SpiderClient.receive(payload.entityId, payload.state, payload.anchor));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
