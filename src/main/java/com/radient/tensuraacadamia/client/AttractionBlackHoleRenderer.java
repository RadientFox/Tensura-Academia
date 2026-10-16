package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.attraction.AttractionPayloads;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AttractionBlackHoleRenderer {

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int GROW_TICKS = 8;
    private static final int COLLAPSE_TICKS = 5;
    private static final int BLAST_TICKS = 16;
    private static final int CONJURE_TICKS = 10;
    private static final float ORB_CORE = 0.22F;
    private static final int DISK_SLICES = 64;
    private static final int DISK_RINGS = 5;
    private static final float DISK_TILT = 0.5F;
    private static final double SHAKE_REACH = 3.0D;

    private record Hole(Vec3 center, float radius, float core, int absorbTicks, long start) {}

    private record Swallowed(int holeId, BlockState state, Vec3 from, long start, int ticks, Vec3 axis, float spin) {}

    private static final List<Swallowed> SWALLOWED = new ArrayList<>();
    private static final int MAX_SWALLOWED = 600;
    private static final double SWALLOW_SPEED = 0.9D;
    private static final float SWIRL = 2.4F;

    private static final class Orb {
        private final int userId;
        private final long start;
        private int state;
        private Vec3 from = Vec3.ZERO;
        private Vec3 direction = Vec3.ZERO;
        private float speed;
        private long launched;

        private Orb(int userId, long start) {
            this.userId = userId;
            this.start = start;
        }
    }

    private static final Map<Integer, Hole> HOLES = new HashMap<>();
    private static final Map<Integer, Orb> ORBS = new HashMap<>();
    private static float shake;
    private static float shakeOld;

    private AttractionBlackHoleRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            HOLES.clear();
            ORBS.clear();
            SWALLOWED.clear();
            AttractionPayloads.clearClient();
            return;
        }

        long time = level.getGameTime();
        for (AttractionPayloads.OrbPayload payload : AttractionPayloads.getClientOrbs()) {
            if (payload.state() == AttractionPayloads.OrbPayload.GONE) {
                ORBS.remove(payload.id());
                continue;
            }

            Orb orb = ORBS.computeIfAbsent(payload.id(), id -> new Orb(payload.userId(), time));
            orb.state = payload.state();
            if (payload.state() == AttractionPayloads.OrbPayload.FLYING) {
                orb.from = payload.from();
                orb.direction = payload.direction();
                orb.speed = payload.speed();
                orb.launched = time;
            }
        }
        AttractionPayloads.getClientOrbs().clear();
        for (AttractionPayloads.BlackHolePayload payload : AttractionPayloads.getClientHoles()) {
            ORBS.remove(payload.id());
            HOLES.put(payload.id(), new Hole(payload.center(), payload.radius(), payload.core(), payload.absorbTicks(), time));
        }
        AttractionPayloads.getClientHoles().clear();
        for (AttractionPayloads.SwallowPayload payload : AttractionPayloads.getClientSwallows()) {
            Hole hole = HOLES.get(payload.id());
            for (int i = 0; i < payload.states().length && hole != null && SWALLOWED.size() < MAX_SWALLOWED; i++) {
                Vec3 from = Vec3.atCenterOf(BlockPos.of(payload.positions()[i]));
                int ticks = Math.max(6, Mth.ceil(6.0D + from.distanceTo(hole.center()) * SWALLOW_SPEED));
                Vec3 axis = new Vec3(level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D).normalize();
                SWALLOWED.add(new Swallowed(payload.id(), Block.stateById(payload.states()[i]), from, time, ticks, axis, 0.3F + level.random.nextFloat() * 0.5F));
            }
        }
        AttractionPayloads.getClientSwallows().clear();
        SWALLOWED.removeIf(block -> time - block.start() > block.ticks() || !HOLES.containsKey(block.holeId()));
        ORBS.values().removeIf(orb -> time - orb.start > 200L);

        shakeOld = shake;
        shake = shake < 0.01F ? 0.0F : shake * 0.85F;
        Vec3 eye = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
        HOLES.entrySet().removeIf(entry -> {
            Hole hole = entry.getValue();
            long age = time - hole.start();
            if (age > hole.absorbTicks() + BLAST_TICKS) {
                return true;
            }

            if (!minecraft.isPaused()) {
                suck(level, hole, age);
            }
            double distance = eye.distanceTo(hole.center());
            double reach = hole.radius() * SHAKE_REACH + 8.0D;
            if (distance < reach) {
                float closeness = (float) (1.0D - distance / reach);
                if (age == hole.absorbTicks()) {
                    shake = Math.max(shake, Math.min(1.5F, hole.radius() / 10.0F + 0.4F) * closeness);
                } else if (age < hole.absorbTicks()) {
                    shake = Math.max(shake, 0.06F * closeness * Math.min(2.0F, hole.radius() / 8.0F));
                }
            }
            return false;
        });
    }

    private static void suck(ClientLevel level, Hole hole, long age) {
        if (age >= hole.absorbTicks()) {
            return;
        }

        int count = Math.min(40, 6 + (int) (hole.radius() * 0.8F));
        for (int i = 0; i < count; i++) {
            Vec3 out = new Vec3(level.random.nextDouble() - 0.5D, (level.random.nextDouble() - 0.5D) * 0.7D, level.random.nextDouble() - 0.5D).normalize()
                    .scale(hole.core() * 1.5D + level.random.nextDouble() * hole.radius());
            level.addParticle(ParticleTypes.PORTAL, hole.center().x, hole.center().y, hole.center().z, out.x, out.y, out.z);
        }
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static Vec3 getHands(LivingEntity user, float partialTick) {
        Vec3 look = user.getViewVector(partialTick);
        return user.getEyePosition(partialTick).add(look.scale(0.9D * user.getScale())).subtract(0.0D, 0.35D * user.getScale(), 0.0D);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || HOLES.isEmpty() && ORBS.isEmpty()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            renderSwallowed(event, minecraft, level);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 view = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-view.x, -view.y, -view.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        List<float[]> draws = new ArrayList<>();
        for (Hole hole : HOLES.values()) {
            float age = now - hole.start();
            float grow = smooth(age / GROW_TICKS);
            float collapse = 1.0F - smooth((age - (hole.absorbTicks() - COLLAPSE_TICKS)) / COLLAPSE_TICKS) * 0.85F;
            float core = Mth.lerp(grow, ORB_CORE, hole.core()) * collapse * (1.0F + 0.04F * Mth.sin(age * 0.5F));
            float blast = age >= hole.absorbTicks() ? (age - hole.absorbTicks()) / BLAST_TICKS : -1.0F;
            draws.add(new float[]{(float) hole.center().x, (float) hole.center().y, (float) hole.center().z, blast >= 0.0F ? core * (1.0F - smooth(blast * 3.0F)) : core, age, blast, hole.radius()});
        }
        for (Orb orb : ORBS.values()) {
            if (!(level.getEntity(orb.userId) instanceof LivingEntity user)) {
                continue;
            }

            float age = now - orb.start;
            Vec3 at = orb.state == AttractionPayloads.OrbPayload.FLYING ? orb.from.add(orb.direction.scale(orb.speed * Math.max(0.0F, now - orb.launched))) : getHands(user, partialTick);
            float core = ORB_CORE * (orb.state == AttractionPayloads.OrbPayload.FLYING ? 1.0F : smooth(age / CONJURE_TICKS));
            draws.add(new float[]{(float) at.x, (float) at.y, (float) at.z, core, age, -1.0F, 0.0F});
        }

        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(WHITE));
        for (float[] draw : draws) {
            if (draw[3] > 0.01F) {
                sphere(solid, poseStack.last(), new Vec3(draw[0], draw[1], draw[2]), draw[3]);
            }
        }
        buffers.endBatch(RenderType.entitySolid(WHITE));

        VertexConsumer haze = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (float[] draw : draws) {
            if (draw[3] > 0.01F) {
                billboardDisk(haze, poseStack.last(), camera, new Vec3(draw[0], draw[1], draw[2]), draw[3] * 0.98F, draw[3] * 2.6F, FastColor.ARGB32.color(210, 0, 0, 0), FastColor.ARGB32.color(0, 10, 0, 20), true);
            }
        }
        buffers.endBatch(RenderType.entityTranslucent(WHITE));

        VertexConsumer band = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        for (float[] draw : draws) {
            if (draw[3] > 0.01F) {
                disk(band, poseStack.last(), new Vec3(draw[0], draw[1], draw[2]), draw[3], draw[4]);
            }
        }
        buffers.endBatch(RenderType.entityTranslucentEmissive(WHITE));

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (float[] draw : draws) {
            Vec3 center = new Vec3(draw[0], draw[1], draw[2]);
            float core = draw[3];
            float blast = draw[5];
            if (core > 0.01F) {
                billboardDisk(glow, poseStack.last(), camera, center, core * 1.04F, core * 1.22F, FastColor.ARGB32.color(255, 255, 230, 255), FastColor.ARGB32.color(0, 160, 90, 255), false);
                billboardDisk(glow, poseStack.last(), camera, center, core * 1.22F, core * 1.9F, FastColor.ARGB32.color(90, 120, 40, 200), FastColor.ARGB32.color(0, 40, 0, 80), false);
            }
            if (blast >= 0.0F && blast <= 1.0F) {
                float radius = draw[6];
                float fade = 1.0F - smooth(blast);
                float flash = Mth.lerp(smooth(blast * 2.0F), Math.max(1.0F, radius * 0.2F), radius * 1.2F);
                billboardDisk(glow, poseStack.last(), camera, center, 0.0F, flash, FastColor.ARGB32.color((int) (255 * fade), 255, 240, 255), FastColor.ARGB32.color(0, 150, 60, 255), false);
                float wave = radius * smooth(blast) * 1.3F;
                billboardDisk(glow, poseStack.last(), camera, center, wave * 0.86F, wave, FastColor.ARGB32.color(0, 120, 40, 255), FastColor.ARGB32.color((int) (220 * fade), 230, 200, 255), false);
                flatRing(glow, poseStack.last(), center, wave * 0.9F, wave * 1.05F, FastColor.ARGB32.color((int) (200 * fade), 210, 160, 255));
            }
        }
        buffers.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderSwallowed(RenderLevelStageEvent event, Minecraft minecraft, ClientLevel level) {
        if (SWALLOWED.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partialTick;
        Vec3 view = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Swallowed block : SWALLOWED) {
            Hole hole = HOLES.get(block.holeId());
            if (hole == null) {
                continue;
            }

            float t = Mth.clamp((now - block.start()) / block.ticks(), 0.0F, 1.0F);
            float pull = t * t * (3.0F - 2.0F * t);
            float inward = 1.0F - pull;
            Vec3 offset = block.from().subtract(hole.center());
            double angle = SWIRL * Math.pow(t, 1.5D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double lift = Math.sin(t * Math.PI) * 0.8D;
            Vec3 turned = new Vec3(offset.x * cos - offset.z * sin, offset.y * (1.0D - pull * 0.5D) + lift, offset.x * sin + offset.z * cos);
            Vec3 at = hole.center().add(turned.scale(inward));
            float size = Math.max(0.0F, 1.0F - t * t * t * t) * (1.0F - 0.35F * t);
            if (size <= 0.02F) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(at.x - view.x, at.y - view.y, at.z - view.z);
            poseStack.mulPose(new Quaternionf().rotationAxis(block.spin() * (now - block.start()), (float) block.axis().x, (float) block.axis().y, (float) block.axis().z));
            poseStack.scale(size, size, size);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            minecraft.getBlockRenderer().renderSingleBlock(block.state(), poseStack, buffers, LevelRenderer.getLightColor(level, BlockPos.containing(at)), OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void sphere(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, float radius) {
        int stacks = 12;
        int slices = 20;
        int color = 0xFF000000;
        for (int i = 0; i < stacks; i++) {
            double lat0 = Math.PI * (-0.5D + (double) i / stacks);
            double lat1 = Math.PI * (-0.5D + (double) (i + 1) / stacks);
            for (int j = 0; j < slices; j++) {
                double lon0 = Mth.TWO_PI * j / slices;
                double lon1 = Mth.TWO_PI * (j + 1) / slices;
                Vec3 a = point(lat0, lon0);
                Vec3 b = point(lat0, lon1);
                Vec3 c = point(lat1, lon1);
                Vec3 d = point(lat1, lon0);
                solidVertex(consumer, pose, center.add(a.scale(radius)), a, color);
                solidVertex(consumer, pose, center.add(d.scale(radius)), d, color);
                solidVertex(consumer, pose, center.add(c.scale(radius)), c, color);
                solidVertex(consumer, pose, center.add(b.scale(radius)), b, color);
            }
        }
    }

    private static Vec3 point(double lat, double lon) {
        return new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
    }

    private static void solidVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 at, Vec3 normal, int color) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void disk(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, float core, float age) {
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotateX(DISK_TILT).rotateY(age * 0.01F);
        Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F).rotateX(DISK_TILT).rotateY(age * 0.01F);
        Vector3f forward = new Vector3f(side).cross(up).normalize();
        Vec3 normal = new Vec3(up.x, up.y, up.z);
        float inner = core * 1.15F;
        float outer = core * 3.8F;
        for (int ring = 0; ring < DISK_RINGS; ring++) {
            float r0 = Mth.lerp((float) ring / DISK_RINGS, inner, outer);
            float r1 = Mth.lerp((float) (ring + 1) / DISK_RINGS, inner, outer);
            for (int i = 0; i < DISK_SLICES; i++) {
                float a0 = Mth.TWO_PI * i / DISK_SLICES;
                float a1 = Mth.TWO_PI * (i + 1) / DISK_SLICES;
                int c00 = diskColor(r0, inner, outer, a0, age);
                int c01 = diskColor(r0, inner, outer, a1, age);
                int c10 = diskColor(r1, inner, outer, a0, age);
                int c11 = diskColor(r1, inner, outer, a1, age);
                Vec3 p00 = onDisk(center, side, forward, r0, a0);
                Vec3 p01 = onDisk(center, side, forward, r0, a1);
                Vec3 p10 = onDisk(center, side, forward, r1, a0);
                Vec3 p11 = onDisk(center, side, forward, r1, a1);
                texturedVertex(consumer, pose, p00, c00, normal);
                texturedVertex(consumer, pose, p10, c10, normal);
                texturedVertex(consumer, pose, p11, c11, normal);
                texturedVertex(consumer, pose, p01, c01, normal);
                texturedVertex(consumer, pose, p01, c01, normal.reverse());
                texturedVertex(consumer, pose, p11, c11, normal.reverse());
                texturedVertex(consumer, pose, p10, c10, normal.reverse());
                texturedVertex(consumer, pose, p00, c00, normal.reverse());
            }
        }
    }

    private static Vec3 onDisk(Vec3 center, Vector3f side, Vector3f forward, float radius, float angle) {
        float cos = Mth.cos(angle) * radius;
        float sin = Mth.sin(angle) * radius;
        return center.add(side.x * cos + forward.x * sin, side.y * cos + forward.y * sin, side.z * cos + forward.z * sin);
    }

    private static int diskColor(float radius, float inner, float outer, float angle, float age) {
        float t = (radius - inner) / (outer - inner);
        float swirl = 0.55F + 0.45F * Mth.sin(angle * 3.0F - age * 0.35F + t * 9.0F);
        float edge = (1.0F - t) * smooth(t * 6.0F);
        float alpha = Mth.clamp(edge * swirl * 1.5F, 0.0F, 1.0F);
        int red = (int) Mth.lerp(t, 250.0F, 90.0F);
        int green = (int) Mth.lerp(t, 210.0F, 20.0F);
        int blue = (int) Mth.lerp(t, 255.0F, 170.0F);
        return FastColor.ARGB32.color((int) (alpha * 235.0F), red, green, blue);
    }

    private static void billboardDisk(VertexConsumer consumer, PoseStack.Pose pose, Camera camera, Vec3 center, float inner, float outer, int innerColor, int outerColor, boolean textured) {
        Vector3f left = new Vector3f(camera.getLeftVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        int slices = 40;
        for (int i = 0; i < slices; i++) {
            float a0 = Mth.TWO_PI * i / slices;
            float a1 = Mth.TWO_PI * (i + 1) / slices;
            Vec3 i0 = onDisk(center, left, up, inner, a0);
            Vec3 i1 = onDisk(center, left, up, inner, a1);
            Vec3 o0 = onDisk(center, left, up, outer, a0);
            Vec3 o1 = onDisk(center, left, up, outer, a1);
            if (textured) {
                Vector3f normal = new Vector3f(camera.getLookVector()).negate();
                Vec3 n = new Vec3(normal.x, normal.y, normal.z);
                texturedVertex(consumer, pose, i0, innerColor, n);
                texturedVertex(consumer, pose, o0, outerColor, n);
                texturedVertex(consumer, pose, o1, outerColor, n);
                texturedVertex(consumer, pose, i1, innerColor, n);
            } else {
                glowVertex(consumer, pose, i0, innerColor);
                glowVertex(consumer, pose, o0, outerColor);
                glowVertex(consumer, pose, o1, outerColor);
                glowVertex(consumer, pose, i1, innerColor);
                glowVertex(consumer, pose, i1, innerColor);
                glowVertex(consumer, pose, o1, outerColor);
                glowVertex(consumer, pose, o0, outerColor);
                glowVertex(consumer, pose, i0, innerColor);
            }
        }
    }

    private static void flatRing(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, float inner, float outer, int color) {
        Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f forward = new Vector3f(0.0F, 0.0F, 1.0F);
        int clear = color & 0x00FFFFFF;
        int slices = 64;
        for (int i = 0; i < slices; i++) {
            float a0 = Mth.TWO_PI * i / slices;
            float a1 = Mth.TWO_PI * (i + 1) / slices;
            Vec3 i0 = onDisk(center, side, forward, inner, a0);
            Vec3 i1 = onDisk(center, side, forward, inner, a1);
            Vec3 o0 = onDisk(center, side, forward, outer, a0);
            Vec3 o1 = onDisk(center, side, forward, outer, a1);
            glowVertex(consumer, pose, i0, clear);
            glowVertex(consumer, pose, o0, color);
            glowVertex(consumer, pose, o1, color);
            glowVertex(consumer, pose, i1, clear);
            glowVertex(consumer, pose, i1, clear);
            glowVertex(consumer, pose, o1, color);
            glowVertex(consumer, pose, o0, color);
            glowVertex(consumer, pose, i0, clear);
        }
    }

    private static void glowVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 at, int color) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 at, int color, Vec3 normal) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amount = Mth.lerp((float) event.getPartialTick(), shakeOld, shake);
        Minecraft minecraft = Minecraft.getInstance();
        if (amount <= 0.01F || minecraft.level == null) {
            return;
        }

        amount *= minecraft.options.screenEffectScale().get().floatValue() * 3.0F;
        float time = minecraft.level.getGameTime() + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + Mth.sin(time * 2.3F) * amount);
        event.setYaw(event.getYaw() + Mth.sin(time * 1.9F + 1.1F) * amount * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(time * 3.1F + 2.3F) * amount * 0.8F);
    }

}
