package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ScanningQuirk;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class ScanningClient {

    private static final ResourceLocation SCAN_VISION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "shaders/post/scan_vision.json");

    private static final float QUIRKLESS_STRENGTH = 0.2F;
    private static final float QUIRK_STRENGTH = 0.5F;
    private static final float STRENGTH_PER_QUIRK = 0.1F;

    private static final int PANEL_FILL = 0x66FF5C7C;
    private static final int PANEL_EDGE = 0xE6FFA6B8;
    private static final int PANEL_TEXT = 0xFFFFFFFF;
    private static final int PANEL_SUBTEXT = 0xFFFFD9E1;
    private static final int PANEL_WIDTH = 96;
    private static final int PANEL_PADDING = 7;
    private static final int PANEL_MARGIN = 10;
    private static final int LINE_HEIGHT = 10;
    private static final int FADE_TICKS = 5;
    private static final int FOCUS_GRACE = 10;
    private static final int COUNT_UP_TICKS = 12;

    private static final DecimalFormat EP_FORMAT = new DecimalFormat("#,###");

    private static boolean maskReady;

    private static int focusId = -1;
    private static int focusStart;
    private static int focusSeen;
    private static @Nullable ScanningQuirk.ScanPayload focusScan;
    private static @Nullable Vec3 focusScreen;

    // Lock On
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }

        int targetId = ScanningQuirk.getLockTarget(player);
        if (targetId < 0) {
            return;
        }

        Entity target = minecraft.level.getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(player) > ScanningQuirk.getLockOnRange(player)) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 delta = living.getEyePosition(partialTick).subtract(player.getEyePosition(partialTick));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = player.getYRot() + Mth.wrapDegrees((float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F - player.getYRot());
        float pitch = Mth.clamp((float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG), -90.0F, 90.0F);

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    // Scan vision
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ScanningQuirk.isScanning(minecraft.player)) {
            maskReady = false;
            focusScreen = null;
            return;
        }

        PostChain chain = VisionRenderer.getChain(SCAN_VISION);
        if (chain == null) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            Map<Integer, Integer> quirks = getSightings();
            maskReady = VisionRenderer.renderMask(event, chain, false, entity -> true, entity -> {
                int count = entity == minecraft.player ? ScanningQuirk.getQuirks(entity).size() : quirks.getOrDefault(entity.getId(), 0);
                float strength = count <= 0 ? QUIRKLESS_STRENGTH : Math.min(1.0F, QUIRK_STRENGTH + STRENGTH_PER_QUIRK * (count - 1));
                return FastColor.ARGB32.color(255, 0, Mth.floor(strength * 255.0F), 0);
            });
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            focusScreen = projectFocus(minecraft, event);
            if (maskReady) {
                VisionRenderer.process(event, chain);
            }

            maskReady = false;
        }
    }

    private static Map<Integer, Integer> getSightings() {
        ScanningQuirk.ScanPayload scan = ScanningQuirk.getClientScan();
        Map<Integer, Integer> quirks = new HashMap<>();
        if (scan != null) {
            for (ScanningQuirk.Sighting sighting : scan.sightings()) {
                quirks.put(sighting.entityId(), sighting.quirks());
            }
        }

        return quirks;
    }

    // Where the entity is in gui coordinates
    private static @Nullable Vec3 projectFocus(Minecraft minecraft, RenderLevelStageEvent event) {
        LivingEntity focus = getFocus(minecraft);
        if (focus == null) {
            return null;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        Vec3 point = focus.getPosition(partialTick).add(0.0D, focus.getBbHeight() * 0.6D, 0.0D).subtract(camera);

        Vector4f clip = new Vector4f((float) point.x, (float) point.y, (float) point.z, 1.0F);
        clip.mul(event.getModelViewMatrix());
        clip.mul(event.getProjectionMatrix());
        if (clip.w <= 0.05F) {
            return null;
        }

        float x = clip.x / clip.w;
        float y = clip.y / clip.w;
        if (Math.abs(x) > 1.2F || Math.abs(y) > 1.2F) {
            return null;
        }

        return new Vec3((x + 1.0F) * 0.5F * minecraft.getWindow().getGuiScaledWidth(), (1.0F - y) * 0.5F * minecraft.getWindow().getGuiScaledHeight(), 0.0D);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ScanningQuirk.ScanPayload scan = ScanningQuirk.getClientScan();
        if (minecraft.player == null || scan == null || !ScanningQuirk.isScanning(minecraft.player)) {
            focusId = -1;
            focusScan = null;
            return;
        }

        int tick = minecraft.player.tickCount;
        if (scan.focusId() >= 0) {
            if (scan.focusId() != focusId) {
                focusId = scan.focusId();
                focusStart = tick;
            }

            focusScan = scan;
            focusSeen = tick;
        } else if (tick - focusSeen > FOCUS_GRACE) {
            focusId = -1;
            focusScan = null;
        }
    }

    private static @Nullable LivingEntity getFocus(Minecraft minecraft) {
        if (focusId < 0 || focusScan == null || minecraft.level == null) {
            return null;
        }

        return minecraft.level.getEntity(focusId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ScanningQuirk.ScanPayload scan = focusScan;
        LivingEntity focus = getFocus(minecraft);
        if (player == null || scan == null || focus == null || !ScanningQuirk.isScanning(player)) {
            return;
        }

        float time = player.tickCount - focusStart + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float fade = Mth.clamp(time / FADE_TICKS, 0.0F, 1.0F);
        float countUp = Mth.clamp(time / COUNT_UP_TICKS, 0.0F, 1.0F);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;

        String name = focus.getDisplayName().getString().toUpperCase(Locale.ROOT);
        boolean obscured = scan.obscured();
        Component ep = Component.translatable("tracadamia.skill.scanning.ep", obscured
                ? Component.literal("000000").withStyle(ChatFormatting.OBFUSCATED)
                : Component.literal(EP_FORMAT.format(Math.round(scan.focusEp() * countUp))));
        List<Component> lines = new ArrayList<>(scan.focusQuirks().stream().<Component>map(line -> obscured
                ? Component.literal(line.getString()).withStyle(ChatFormatting.OBFUSCATED)
                : Component.literal(line.getString())).toList());
        if (lines.isEmpty()) {
            lines.add(Component.translatable("tracadamia.skill.scanning.no_quirks"));
        }

        int textWidth = Math.max(font.width(name), font.width(ep));
        for (Component line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int width = Math.max(PANEL_WIDTH, textWidth + PANEL_PADDING * 2);
        int height = 42 + lines.size() * LINE_HEIGHT;
        int right = graphics.guiWidth() - PANEL_MARGIN;
        int left = right - width;
        int top = Math.max(PANEL_MARGIN, graphics.guiHeight() / 3 - height / 2);
        int bottom = top + height;

        if (focusScreen != null) {
            float x = (float) focusScreen.x;
            float y = (float) focusScreen.y;
            int edge = fadeColor(PANEL_EDGE, fade);
            drawLine(graphics, x, y, left, top + height * 0.5F, 1.0F, edge);
            drawDiamond(graphics, x, y, 5.0F, edge);
        }

        drawPanel(graphics, left, top, right, bottom, fade);

        int text = fadeColor(PANEL_TEXT, fade);
        int subtext = fadeColor(PANEL_SUBTEXT, fade);
        int textRight = right - PANEL_PADDING;
        graphics.drawString(font, name, textRight - font.width(name), top + 6, text, true);
        drawLine(graphics, left + PANEL_PADDING, top + 17, textRight, top + 17, 1.0F, fadeColor(PANEL_EDGE, fade));
        graphics.drawString(font, ep, textRight - font.width(ep), top + 21, text, true);

        int y = top + 35;
        for (Component line : lines) {
            graphics.drawString(font, line, textRight - font.width(line), y, subtext, true);
            y += LINE_HEIGHT;
        }

        graphics.flush();
    }

    private static int fadeColor(int color, float fade) {
        return FastColor.ARGB32.color(Mth.floor(FastColor.ARGB32.alpha(color) * fade), color);
    }

    private static void drawPanel(GuiGraphics graphics, float left, float top, float right, float bottom, float fade) {
        int edge = fadeColor(PANEL_EDGE, fade);
        drawQuad(graphics, left, top, left, bottom, right, bottom, right, top, fadeColor(PANEL_FILL, fade));
        drawLine(graphics, left, top, right, top, 1.0F, edge);
        drawLine(graphics, right, top, right, bottom, 1.0F, edge);
        drawLine(graphics, right, bottom, left, bottom, 1.0F, edge);
        drawLine(graphics, left, bottom, left, top, 1.0F, edge);
    }

    private static void drawDiamond(GuiGraphics graphics, float x, float y, float size, int color) {
        drawLine(graphics, x, y - size, x + size, y, 1.0F, color);
        drawLine(graphics, x + size, y, x, y + size, 1.0F, color);
        drawLine(graphics, x, y + size, x - size, y, 1.0F, color);
        drawLine(graphics, x - size, y, x, y - size, 1.0F, color);
    }

    private static void drawLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-3F) {
            return;
        }

        float nx = -dy / length * width * 0.5F;
        float ny = dx / length * width * 0.5F;
        drawQuad(graphics, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
    }

    private static void drawQuad(GuiGraphics graphics, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        if (area <= 0.0F) {
            consumer.addVertex(pose, x1, y1, 0.0F).setColor(color);
            consumer.addVertex(pose, x2, y2, 0.0F).setColor(color);
            consumer.addVertex(pose, x3, y3, 0.0F).setColor(color);
            consumer.addVertex(pose, x4, y4, 0.0F).setColor(color);
        } else {
            consumer.addVertex(pose, x4, y4, 0.0F).setColor(color);
            consumer.addVertex(pose, x3, y3, 0.0F).setColor(color);
            consumer.addVertex(pose, x2, y2, 0.0F).setColor(color);
            consumer.addVertex(pose, x1, y1, 0.0F).setColor(color);
        }
    }

}
