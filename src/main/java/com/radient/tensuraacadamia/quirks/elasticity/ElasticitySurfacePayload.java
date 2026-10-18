package com.radient.tensuraacadamia.quirks.elasticity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD)
public record ElasticitySurfacePayload(UUID id, ResourceLocation dimension, Vec3 center, Vec3 normal,
                                       Vec3 right, float radius, int layers, long expiresAt,
                                       boolean sandwich, Vec3 impact, float strength) implements CustomPacketPayload {
    public static final Type<ElasticitySurfacePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "elasticity_surface"));
    public static final StreamCodec<FriendlyByteBuf, ElasticitySurfacePayload> CODEC =
            CustomPacketPayload.codec(ElasticitySurfacePayload::encode, ElasticitySurfacePayload::new);

    private ElasticitySurfacePayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readResourceLocation(), buffer.readVec3(), buffer.readVec3(),
                buffer.readVec3(), buffer.readFloat(), buffer.readVarInt(), buffer.readLong(),
                buffer.readBoolean(), buffer.readVec3(), buffer.readFloat());
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeResourceLocation(dimension);
        buffer.writeVec3(center);
        buffer.writeVec3(normal);
        buffer.writeVec3(right);
        buffer.writeFloat(radius);
        buffer.writeVarInt(layers);
        buffer.writeLong(expiresAt);
        buffer.writeBoolean(sandwich);
        buffer.writeVec3(impact);
        buffer.writeFloat(strength);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) ->
                context.enqueueWork(() -> ElasticityRenderer.receive(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
