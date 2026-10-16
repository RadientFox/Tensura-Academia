package com.radient.tensuraacadamia.ability.unique.quirks.attraction;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AttractionPayloads {

    private static final List<BlackHolePayload> CLIENT_HOLES = new ArrayList<>();
    private static final List<OrbPayload> CLIENT_ORBS = new ArrayList<>();
    private static final List<SwallowPayload> CLIENT_SWALLOWS = new ArrayList<>();

    private AttractionPayloads() {
    }

    public static List<BlackHolePayload> getClientHoles() {
        return CLIENT_HOLES;
    }

    public static List<OrbPayload> getClientOrbs() {
        return CLIENT_ORBS;
    }

    public static List<SwallowPayload> getClientSwallows() {
        return CLIENT_SWALLOWS;
    }

    public record SwallowPayload(int id, int[] states, long[] positions) implements CustomPacketPayload {
        public static final Type<SwallowPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "attraction_swallow"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SwallowPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.id());
            buf.writeVarIntArray(payload.states());
            buf.writeLongArray(payload.positions());
        }, buf -> new SwallowPayload(buf.readVarInt(), buf.readVarIntArray(), buf.readLongArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BlackHolePayload(int id, Vec3 center, float radius, float core, int absorbTicks) implements CustomPacketPayload {
        public static final Type<BlackHolePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "attraction_black_hole"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BlackHolePayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.id());
            buf.writeVec3(payload.center());
            buf.writeFloat(payload.radius());
            buf.writeFloat(payload.core());
            buf.writeVarInt(payload.absorbTicks());
        }, buf -> new BlackHolePayload(buf.readVarInt(), buf.readVec3(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OrbPayload(int id, int userId, int state, Vec3 from, Vec3 direction, float speed) implements CustomPacketPayload {
        public static final int CONJURING = 0;
        public static final int FLYING = 1;
        public static final int GONE = 2;
        public static final Type<OrbPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "attraction_orb"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrbPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.id());
            buf.writeVarInt(payload.userId());
            buf.writeVarInt(payload.state());
            buf.writeVec3(payload.from());
            buf.writeVec3(payload.direction());
            buf.writeFloat(payload.speed());
        }, buf -> new OrbPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void clearClient() {
        CLIENT_HOLES.clear();
        CLIENT_ORBS.clear();
        CLIENT_SWALLOWS.clear();
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlackHolePayload.TYPE, BlackHolePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_HOLES.add(payload)));
        registrar.playToClient(OrbPayload.TYPE, OrbPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_ORBS.add(payload)));
        registrar.playToClient(SwallowPayload.TYPE, SwallowPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_SWALLOWS.add(payload)));
    }

}
