package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class WaldoClient {
    public static final float STRIKE_TICKS = 7.0F;

    public record Strike(int targetId, long start) {}

    private static final Map<Integer, Map<Integer, Strike>> STRIKES = new ConcurrentHashMap<>();

    private WaldoClient() {
    }

    public static void strike(int entityId, int leg, int targetId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            STRIKES.computeIfAbsent(entityId, id -> new HashMap<>()).put(leg, new Strike(targetId, level.getGameTime()));
        }
    }

    public static @Nullable Strike getStrike(int entityId, int leg) {
        Map<Integer, Strike> legs = STRIKES.get(entityId);
        return legs == null ? null : legs.get(leg);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            STRIKES.clear();
            return;
        }

        long now = level.getGameTime();
        STRIKES.values().forEach(legs -> legs.values().removeIf(strike -> now - strike.start() > STRIKE_TICKS + 1));
        STRIKES.values().removeIf(Map::isEmpty);
    }
}
