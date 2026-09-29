package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.VibrationDetectionQuirk;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Map;

// Basically just seismic sense
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class VibrationDetectionClient {

    private static final ResourceLocation VIBRATION_VISION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "shaders/post/vibration_vision.json");
    private static final ResourceLocation RADAR = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "vibration_radar");
    private static final int SILHOUETTE_TICKS = 40;
    private static final int SILHOUETTE_HOLD = 15;
    private static final int RIPPLE_TICKS = 30;
    private static final int RINGS = 3;
    private static final float RING_DELAY = 0.2F;
    private static final int RING_SEGMENTS = 32;
    private static final float RING_WIDTH = 0.12F;
    private static final int RADAR_RADIUS = 34;

    private static boolean maskReady;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !VibrationDetectionQuirk.isActive(minecraft.player)) {
            maskReady = false;
            return;
        }

        PostChain chain = VisionRenderer.getChain(VIBRATION_VISION);
        if (chain == null) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            long time = minecraft.level.getGameTime();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            Map<Integer, Long> moving = VibrationDetectionQuirk.getClientMoving();
            maskReady = VisionRenderer.renderMask(event, chain, true, entity -> moving.containsKey(entity.getId()), entity -> {
                return intensity(silhouetteFade(time - moving.get(entity.getId()) + partialTick));
            });
            if (maskReady) {
                Vec3 camera = event.getCamera().getPosition();
                VisionRenderer.renderMaskBoxes(chain, consumer -> {
                    for (VibrationDetectionQuirk.ClientRipple ripple : VibrationDetectionQuirk.getClientRipples()) {
                        renderRipple(consumer, ripple, camera, (time - ripple.time() + partialTick) / RIPPLE_TICKS);
                    }
                });
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (maskReady) {
                VisionRenderer.process(event, chain);
            }

            maskReady = false;
        }
    }

    private static void renderRipple(VertexConsumer consumer, VibrationDetectionQuirk.ClientRipple ripple, Vec3 camera, float age) {
        float x = (float) (ripple.pos().x - camera.x);
        float y = (float) (ripple.pos().y - camera.y) + 0.05F;
        float z = (float) (ripple.pos().z - camera.z);
        float reach = 1.0F + ripple.strength() * 2.5F;
        for (int ring = 0; ring < RINGS; ring++) {
            float progress = (age - ring * RING_DELAY) / (1.0F - (RINGS - 1) * RING_DELAY);
            if (progress <= 0.0F || progress >= 1.0F) {
                continue;
            }

            float radius = progress * reach;
            int color = intensity((1.0F - progress) * ripple.strength());
            for (int i = 0; i < RING_SEGMENTS; i++) {
                float a = Mth.TWO_PI * i / RING_SEGMENTS;
                float b = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
                float inner = Math.max(0.0F, radius - RING_WIDTH);
                consumer.addVertex(x + Mth.cos(a) * inner, y, z + Mth.sin(a) * inner).setColor(color);
                consumer.addVertex(x + Mth.cos(a) * radius, y, z + Mth.sin(a) * radius).setColor(color);
                consumer.addVertex(x + Mth.cos(b) * radius, y, z + Mth.sin(b) * radius).setColor(color);
                consumer.addVertex(x + Mth.cos(b) * inner, y, z + Mth.sin(b) * inner).setColor(color);
            }
        }
    }

    private static float silhouetteFade(float age) {
        return Mth.clamp(1.0F - (age - SILHOUETTE_HOLD) / (SILHOUETTE_TICKS - SILHOUETTE_HOLD), 0.0F, 1.0F);
    }

    private static int intensity(float value) {
        int level = Mth.clamp(Math.round(value * 255.0F), 0, 255);
        return 0xFF000000 | level << 16 | level << 8 | level;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long time = level.getGameTime();
        VibrationDetectionQuirk.getClientMoving().values().removeIf(seen -> time - seen > SILHOUETTE_TICKS || seen > time);
        VibrationDetectionQuirk.getClientRipples().removeIf(ripple -> time - ripple.time() > RIPPLE_TICKS || ripple.time() > time);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VibrationDetectionQuirk.clearClient();
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, RADAR, VibrationDetectionClient::renderRadar);
    }

    // Cool ring around the crosshair pointing at vibrations
    private static void renderRadar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui || !VibrationDetectionQuirk.isActive(player)) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        for (int i = 0; i < 36; i++) {
            float angle = Mth.TWO_PI * i / 36.0F;
            dot(graphics, centerX + Mth.sin(angle) * RADAR_RADIUS, centerY - Mth.cos(angle) * RADAR_RADIUS, 1, 0x40FFFFFF);
        }

        long time = minecraft.level.getGameTime();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        double range = Math.max(1.0D, VibrationDetectionQuirk.getClientRange());
        for (VibrationDetectionQuirk.ClientRipple ripple : VibrationDetectionQuirk.getClientRipples()) {
            float fade = 1.0F - (time - ripple.time() + partialTick) / RIPPLE_TICKS;
            blip(graphics, player, ripple.pos(), fade * ripple.strength(), range, centerX, centerY);
        }

        for (Map.Entry<Integer, Long> entry : VibrationDetectionQuirk.getClientMoving().entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (entity != null) {
                float fade = silhouetteFade(time - entry.getValue() + partialTick);
                blip(graphics, player, entity.position(), fade, range, centerX, centerY);
            }
        }
    }

    private static void blip(GuiGraphics graphics, LocalPlayer player, Vec3 pos, float fade, double range, int centerX, int centerY) {
        Vec3 offset = pos.subtract(player.position());
        double distance = offset.horizontalDistance();
        if (fade <= 0.0F || distance < 1.5D) {
            return;
        }

        float angle = (float) Math.toRadians(Mth.wrapDegrees(Mth.atan2(offset.z, offset.x) * Mth.RAD_TO_DEG - 90.0D - player.getYRot()));
        float closeness = (float) Mth.clamp(1.0D - distance / range, 0.2D, 1.0D);
        float radius = RADAR_RADIUS + 3.0F;
        int alpha = Mth.clamp(Math.round(fade * (0.4F + closeness * 0.6F) * 255.0F), 0, 255);
        dot(graphics, centerX + Mth.sin(angle) * radius, centerY - Mth.cos(angle) * radius, closeness > 0.6F ? 2 : 1, alpha << 24 | 0xFFFFFF);
    }

    private static void dot(GuiGraphics graphics, float x, float y, int size, int color) {
        int left = Math.round(x) - size;
        int top = Math.round(y) - size;
        graphics.fill(left, top, left + size * 2 + 1, top + size * 2 + 1, color);
    }

}
