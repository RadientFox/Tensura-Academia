package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Everything New Order sends between client and server
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderPayloads {

    private static @Nullable RulesPayload clientRules;
    private static final List<CalloutPayload> CLIENT_CALLOUTS = new ArrayList<>();
    private static final List<GhostStrikePayload> CLIENT_STRIKES = new ArrayList<>();
    private static final List<CopyActionPayload> CLIENT_ACTIONS = new ArrayList<>();
    private static final List<ArcPayload> CLIENT_ARCS = new ArrayList<>();
    private static final List<BarragePayload> CLIENT_BARRAGES = new ArrayList<>();
    private static final List<CagePayload> CLIENT_CAGES = new ArrayList<>();
    private static final List<ImpactPayload> CLIENT_IMPACTS = new ArrayList<>();
    private static int clientHeld;

    private NewOrderPayloads() {
    }

    // used by NewOrderClient
    public static @Nullable RulesPayload takeClientRules() {
        RulesPayload rules = clientRules;
        clientRules = null;
        return rules;
    }

    public static List<CalloutPayload> getClientCallouts() {
        return CLIENT_CALLOUTS;
    }

    public static List<GhostStrikePayload> getClientStrikes() {
        return CLIENT_STRIKES;
    }

    // used by NewOrderClient
    public static List<CopyActionPayload> getClientActions() {
        return CLIENT_ACTIONS;
    }

    public static List<ArcPayload> getClientArcs() {
        return CLIENT_ARCS;
    }

    public static List<BarragePayload> getClientBarrages() {
        return CLIENT_BARRAGES;
    }

    public static List<CagePayload> getClientCages() {
        return CLIENT_CAGES;
    }

    public static List<ImpactPayload> getClientImpacts() {
        return CLIENT_IMPACTS;
    }

    // What New Order is holding the local player to, see HeldPayload
    public static int getClientHeld() {
        return clientHeld;
    }

    public record RuleEntry(int rule, Component target, int rebelTicks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RuleEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RuleEntry::rule,
                ComponentSerialization.TRUSTED_STREAM_CODEC, RuleEntry::target,
                ByteBufCodecs.VAR_INT, RuleEntry::rebelTicks,
                RuleEntry::new
        );
    }

    public record RulesPayload(boolean open, int kind, Component name, int entityId, String block, List<RuleEntry> rules, boolean mastered) implements CustomPacketPayload {
        public static final Type<RulesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_rules"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RulesPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeBoolean(payload.open());
            buf.writeVarInt(payload.kind());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.name());
            buf.writeVarInt(payload.entityId());
            buf.writeUtf(payload.block());
            RuleEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.rules());
            buf.writeBoolean(payload.mastered());
        }, buf -> new RulesPayload(buf.readBoolean(), buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(),
                RuleEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // A rule or a slot to revoke
    public record RuleChoicePayload(int action, int value) implements CustomPacketPayload {
        public static final Type<RuleChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RuleChoicePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RuleChoicePayload::action,
                ByteBufCodecs.VAR_INT, RuleChoicePayload::value,
                RuleChoicePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // The Air Will Restrain You closing on someone, ticks 0 when it lets go
    public record CagePayload(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<CagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_cage"));
        public static final StreamCodec<FriendlyByteBuf, CagePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CagePayload::entityId,
                ByteBufCodecs.VAR_INT, CagePayload::ticks,
                CagePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // Can Not Move, the air's cage and Can Not Interact With Items on the local player
    public record HeldPayload(int flags) implements CustomPacketPayload {
        public static final int ROOTED = 1;
        public static final int CAGED = 2;
        public static final int NO_ITEMS = 4;
        public static final Type<HeldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_held"));
        public static final StreamCodec<FriendlyByteBuf, HeldPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeldPayload::flags,
                HeldPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // The Air Is My Weapon starting on someone
    public record BarragePayload(int userId, int arms, int punches) implements CustomPacketPayload {
        public static final Type<BarragePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_barrage"));
        public static final StreamCodec<FriendlyByteBuf, BarragePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BarragePayload::userId,
                ByteBufCodecs.VAR_INT, BarragePayload::arms,
                ByteBufCodecs.VAR_INT, BarragePayload::punches,
                BarragePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // A name or rule called out over something
    // entityId -1 stays where it was said
    public record CalloutPayload(Vec3 pos, int entityId, Component text, int color) implements CustomPacketPayload {
        public static final Type<CalloutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_callout"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CalloutPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeDouble(payload.pos().x);
            buf.writeDouble(payload.pos().y);
            buf.writeDouble(payload.pos().z);
            buf.writeVarInt(payload.entityId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.text());
            buf.writeInt(payload.color());
        }, buf -> new CalloutPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GhostStrikePayload(int userId, int targetId) implements CustomPacketPayload {
        public static final Type<GhostStrikePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_strike"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GhostStrikePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GhostStrikePayload::userId,
                ByteBufCodecs.VAR_INT, GhostStrikePayload::targetId,
                GhostStrikePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CopyActionPayload(int userId, int action, int charge, int ticks, int targetId, Vec3 point) implements CustomPacketPayload {
        public static final Type<CopyActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_copy_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CopyActionPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.userId());
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.charge());
            buf.writeVarInt(payload.ticks());
            buf.writeVarInt(payload.targetId());
            buf.writeVec3(payload.point());
        }, buf -> new CopyActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVec3()));

        public CopyActionPayload(int userId, int action, int charge, int ticks) {
            this(userId, action, charge, ticks, -1, Vec3.ZERO);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ImpactPayload(Vec3 at, int kind, float size) implements CustomPacketPayload {
        public static final int FIST_BUMP = 0;
        public static final int SPEAR = 1;
        public static final int CLAP = 2;
        public static final int EXPLODE = 3;
        public static final Type<ImpactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_impact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImpactPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVec3(payload.at());
            buf.writeVarInt(payload.kind());
            buf.writeFloat(payload.size());
        }, buf -> new ImpactPayload(buf.readVec3(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    //Bolt of lightning between two points
    public record ArcPayload(Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {
        public static final Type<ArcPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_arc"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArcPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
            buf.writeVec3(payload.from());
            buf.writeVec3(payload.to());
            buf.writeVarInt(payload.ticks());
        }, buf -> new ArcPayload(buf.readVec3(), buf.readVec3(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void clearClient() {
        clientRules = null;
        CLIENT_CALLOUTS.clear();
        CLIENT_STRIKES.clear();
        CLIENT_ACTIONS.clear();
        CLIENT_ARCS.clear();
        CLIENT_BARRAGES.clear();
        CLIENT_CAGES.clear();
        CLIENT_IMPACTS.clear();
        clientHeld = 0;
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RulesPayload.TYPE, RulesPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientRules = payload));
        registrar.playToServer(RuleChoicePayload.TYPE, RuleChoicePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NewOrderRules.choose(player, payload);
            }
        }));
        registrar.playToClient(CagePayload.TYPE, CagePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_CAGES.add(payload)));
        registrar.playToClient(HeldPayload.TYPE, HeldPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientHeld = payload.flags()));
        registrar.playToClient(BarragePayload.TYPE, BarragePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_BARRAGES.add(payload)));
        registrar.playToClient(CalloutPayload.TYPE, CalloutPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_CALLOUTS.add(payload)));
        registrar.playToClient(GhostStrikePayload.TYPE, GhostStrikePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_STRIKES.add(payload)));
        registrar.playToClient(CopyActionPayload.TYPE, CopyActionPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_ACTIONS.add(payload)));
        registrar.playToClient(ImpactPayload.TYPE, ImpactPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_IMPACTS.add(payload)));
        registrar.playToClient(ArcPayload.TYPE, ArcPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_ARCS.add(payload)));
    }

}
