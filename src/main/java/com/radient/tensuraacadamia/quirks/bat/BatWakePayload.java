package com.radient.tensuraacadamia.quirks.bat;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD)
public record BatWakePayload() implements CustomPacketPayload {
    public static final BatWakePayload INSTANCE = new BatWakePayload();
    public static final Type<BatWakePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "bat_wake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BatWakePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (BatEvents.hanging(context.player())) BatEvents.wake(context.player());
        }));
    }
}
