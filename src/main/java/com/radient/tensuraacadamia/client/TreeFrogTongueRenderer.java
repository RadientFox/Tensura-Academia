package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.TreeFrogQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class TreeFrogTongueRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/brain_coral_block.png");
    private static final int RETRACT = 3;
    private static final int SEGMENTS = 12;
    private static final int SIDES = 6;
    private static final float THICK = 0.09F;
    private static final float TIP = 0.16F;
    private static final int COLOR = 0xFFFF8FA8;

    private record Tongue(int targetId, long start, int reach, int ticks) {}

    private static final Map<Integer, Tongue> TONGUES = new HashMap<>();
    private static final Map<Integer, Long> RETRACTING = new HashMap<>();
    private static final Map<Integer, Vec3> LAST_TIP = new HashMap<>();

    private TreeFrogTongueRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TONGUES.clear();
            RETRACTING.clear();
            LAST_TIP.clear();
            return;
        }

        long time = level.getGameTime();
        for (TreeFrogQuirk.TonguePayload payload : TreeFrogQuirk.getClientTongues()) {
            if (payload.targetId() >= 0) {
                TONGUES.put(payload.userId(), new Tongue(payload.targetId(), time, payload.reach(), payload.ticks()));
                RETRACTING.remove(payload.userId());
            } else if (TONGUES.remove(payload.userId()) != null) {
                RETRACTING.put(payload.userId(), time);
            }
        }
        TreeFrogQuirk.getClientTongues().clear();
        TONGUES.entrySet().removeIf(entry -> {
            if (time - entry.getValue().start() > entry.getValue().ticks() + 5) {
                RETRACTING.put(entry.getKey(), time);
                return true;
            }
            return false;
        });
        RETRACTING.values().removeIf(start -> time - start > RETRACT || start > time);
        LAST_TIP.keySet().removeIf(id -> !TONGUES.containsKey(id) && !RETRACTING.containsKey(id));
    }

    private static Vec3 getMouth(LivingEntity user, float partialTick) {
        Vec3 look = user.getViewVector(partialTick);
        Vec3 mouth = user.getEyePosition(partialTick).add(look.scale(0.3D)).subtract(0.0D, 0.15D * user.getScale(), 0.0D);
        Minecraft minecraft = Minecraft.getInstance();
        if (user == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
            mouth = mouth.subtract(0.0D, 0.25D * user.getScale(), 0.0D);
        }
        return mouth;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || level == null || TONGUES.isEmpty() && RETRACTING.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (Map.Entry<Integer, Tongue> entry : TONGUES.entrySet()) {
            Tongue tongue = entry.getValue();
            if (level.getEntity(entry.getKey()) instanceof LivingEntity user && level.getEntity(tongue.targetId()) instanceof Entity target) {
                Vec3 mouth = getMouth(user, partialTick);
                Vec3 grab = target.getPosition(partialTick).add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
                float out = Mth.clamp((now - tongue.start()) / tongue.reach(), 0.0F, 1.0F);
                Vec3 tip = mouth.lerp(grab, out * out * (3.0F - 2.0F * out));
                LAST_TIP.put(entry.getKey(), tip);
                draw(consumer, poseStack.last(), level, mouth, tip, now, out < 1.0F);
            }
        }
        for (Map.Entry<Integer, Long> entry : RETRACTING.entrySet()) {
            Vec3 last = LAST_TIP.get(entry.getKey());
            if (last != null && level.getEntity(entry.getKey()) instanceof LivingEntity user) {
                Vec3 mouth = getMouth(user, partialTick);
                float back = Mth.clamp((now - entry.getValue()) / RETRACT, 0.0F, 1.0F);
                draw(consumer, poseStack.last(), level, mouth, last.lerp(mouth, back), now, true);
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.popPose();
    }

    private static void draw(VertexConsumer consumer, PoseStack.Pose pose, ClientLevel level, Vec3 from, Vec3 to, float now, boolean flying) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 0.05D) {
            return;
        }

        Vec3 forward = path.scale(1.0D / length);
        Vec3 side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 up = side.cross(forward).normalize();
        double sag = flying ? 0.0D : Math.min(0.6D, length * 0.04D);
        Vec3[] centers = new Vec3[SEGMENTS + 1];
        float[] radii = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double wobble = Math.sin(t * Math.PI * 3.0D + now * 0.9D) * 0.04D * Math.sin(t * Math.PI);
            centers[i] = from.add(path.scale(t)).add(up.scale(-sag * Math.sin(t * Math.PI) + wobble)).add(side.scale(wobble * 0.6D));
            radii[i] = (float) Mth.lerp(t, THICK * 1.25D, THICK * 0.85D);
        }

        int light = LevelRenderer.getLightColor(level, BlockPos.containing(to));
        float v = 0.0F;
        for (int i = 0; i < SEGMENTS; i++) {
            float nextV = v + (float) (centers[i].distanceTo(centers[i + 1]) * 0.5D);
            ring(consumer, pose, centers[i], centers[i + 1], side, up, radii[i], radii[i + 1], v, nextV, light);
            v = nextV;
        }

        Vec3 end = centers[SEGMENTS];
        ring(consumer, pose, end.subtract(forward.scale(TIP * 0.6D)), end.add(forward.scale(TIP * 0.4D)), side, up, TIP, TIP * 0.9F, 0.0F, 0.2F, light);
        ring(consumer, pose, end.add(forward.scale(TIP * 0.4D)), end.add(forward.scale(TIP * 0.9D)), side, up, TIP * 0.9F, 0.02F, 0.2F, 0.4F, light);
    }

    private static void ring(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side, Vec3 up, float radiusA, float radiusB, float vA, float vB, int light) {
        for (int k = 0; k < SIDES; k++) {
            double angle0 = Mth.TWO_PI * k / SIDES;
            double angle1 = Mth.TWO_PI * (k + 1) / SIDES;
            Vec3 n0 = side.scale(Math.cos(angle0)).add(up.scale(Math.sin(angle0)));
            Vec3 n1 = side.scale(Math.cos(angle1)).add(up.scale(Math.sin(angle1)));
            float u0 = (float) k / SIDES;
            float u1 = (float) (k + 1) / SIDES;
            vertex(consumer, pose, a.add(n0.scale(radiusA)), u0, vA, n0, light);
            vertex(consumer, pose, b.add(n0.scale(radiusB)), u0, vB, n0, light);
            vertex(consumer, pose, b.add(n1.scale(radiusB)), u1, vB, n1, light);
            vertex(consumer, pose, a.add(n1.scale(radiusA)), u1, vA, n1, light);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 at, float u, float v, Vec3 normal, int light) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(COLOR).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

}
