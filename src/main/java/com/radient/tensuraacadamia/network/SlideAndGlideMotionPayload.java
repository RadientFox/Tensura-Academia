package com.radient.tensuraacadamia.network;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SlideAndGlideQuirk;
import com.radient.tensuraacadamia.client.SlideAndGlideMotionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SlideAndGlideMotionPayload(int entityId, int kind) implements CustomPacketPayload {
    public static final int UPRIGHT = 0;
    public static final int CRAWL = 1;
    public static final int CEILING = 2;
    public static final int JUMP_FLIP = 3;
    public static final int JUMP_POUNCE = 4;
    public static final int JUMP_WALL_KICK = 5;
    public static final int JUMP_CEILING_DROP = 6;

    public static final Type<SlideAndGlideMotionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_motion"));
    public static final StreamCodec<FriendlyByteBuf, SlideAndGlideMotionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SlideAndGlideMotionPayload::encode, SlideAndGlideMotionPayload::new);

    public SlideAndGlideMotionPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readVarInt());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeVarInt(kind);
    }

    public static boolean isJump(int kind) {
        return kind >= JUMP_FLIP && kind <= JUMP_CEILING_DROP;
    }

    public static void handle(SlideAndGlideMotionPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    SlideAndGlideQuirk.receiveMotion(player, payload.kind);
                }
            });
        } else {
            context.enqueueWork(() -> SlideAndGlideMotionClient.receive(payload.entityId, payload.kind));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
