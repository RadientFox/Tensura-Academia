package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class PermeationPhaseClient {
    private static final Set<Integer> ACTIVE = new HashSet<>();

    private PermeationPhaseClient() {
    }

    public static void setActive(int entityId, boolean active) {
        if (active) ACTIVE.add(entityId);
        else ACTIVE.remove(entityId);
        apply(entityId, active);
    }

    public static boolean isActive(int entityId) {
        return ACTIVE.contains(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }
        for (int entityId : ACTIVE) apply(entityId, true);
    }

    private static void apply(int entityId, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity != null) entity.noPhysics = active;
    }
}
