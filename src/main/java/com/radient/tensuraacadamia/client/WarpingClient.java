package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.WarpingQuirk;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class WarpingClient {

    private WarpingClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        WarpingQuirk.WheelPayload wheel = WarpingQuirk.takeClientWheel();
        Minecraft minecraft = Minecraft.getInstance();
        if (wheel != null && minecraft.player != null) {
            minecraft.setScreen(new WarpWheelScreen(wheel));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WarpingQuirk.clearClient();
    }

}
