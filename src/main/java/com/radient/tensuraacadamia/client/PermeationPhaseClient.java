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
    private static final Set<Integer> NO_CLIP = new HashSet<>();
    private static int oxygen = 200;
    private static final java.util.Map<Integer, Boolean> ORIGINAL_COLLISION = new java.util.HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;

    private PermeationPhaseClient() {
    }

    public static void setActive(int entityId, boolean active, boolean noClip, int remaining) {
        if (world != Minecraft.getInstance().level) {
            ACTIVE.clear();
            NO_CLIP.clear();
            ORIGINAL_COLLISION.clear();
            world = Minecraft.getInstance().level;
            oxygen = 200;
        }
        if (active) ACTIVE.add(entityId);
        else ACTIVE.remove(entityId);
        if (noClip) NO_CLIP.add(entityId);
        else NO_CLIP.remove(entityId);
        apply(entityId, noClip);
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getId() == entityId) oxygen = remaining;
    }

    public static boolean isActive(int entityId) {
        return NO_CLIP.contains(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != world) {
            ACTIVE.clear();
            NO_CLIP.clear();
            ORIGINAL_COLLISION.clear();
            oxygen = 200;
            world = minecraft.level;
        }
        if (minecraft.level == null) {
            return;
        }
        for (int entityId : NO_CLIP) apply(entityId, true);
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
            event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "permeation_oxygen"), (graphics, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.options.hideGui || !ACTIVE.contains(mc.player.getId()) && oxygen >= 200) return;
                int x = graphics.guiWidth() / 2 - 45, y = graphics.guiHeight() - 65;
                graphics.fill(x - 1, y - 1, x + 91, y + 7, 0xC0000000);
                graphics.fill(x, y, x + (int) (90 * oxygen / 200.0), y + 6, oxygen > 40 ? 0xFF76DDEE : 0xFFFF5544);
                graphics.drawCenteredString(mc.font, "Oxygen", x + 45, y - 10, 0xFFFFFFFF);
            });
        }
    }

    private static void apply(int entityId, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity != null) {
            if (active) {
                ORIGINAL_COLLISION.putIfAbsent(entityId, entity.noPhysics);
                entity.noPhysics = true;
            } else if (ORIGINAL_COLLISION.containsKey(entityId)) entity.noPhysics = ORIGINAL_COLLISION.remove(entityId);
        }
    }
}
