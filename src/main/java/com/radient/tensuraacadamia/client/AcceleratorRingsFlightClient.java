package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AcceleratorRingsFlightClient {
    private static final Map<Integer, Integer> ACTIVE = new HashMap<>();

    private AcceleratorRingsFlightClient() {
    }

    public static void setActive(int entityId, int duration) {
        if (duration <= 0) ACTIVE.remove(entityId);
        else ACTIVE.put(entityId, duration);
    }

    public static boolean isActive(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Iterator<Map.Entry<Integer, Integer>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (entry.getValue() <= 1) iterator.remove();
            else entry.setValue(entry.getValue() - 1);
        }
    }
}
