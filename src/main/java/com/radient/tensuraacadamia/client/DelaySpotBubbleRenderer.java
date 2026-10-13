package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.DelaySpotQuirk;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.*;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class DelaySpotBubbleRenderer {
    private static final int RINGS = 28;
    private static final int SEGMENTS = 56;
    private static final int STRIDE = SEGMENTS + 1;
    private static final int SNAP_TICKS = 9;

    private static final float[] NX = new float[(RINGS + 1) * STRIDE];
    private static final float[] NY = new float[(RINGS + 1) * STRIDE];
    private static final float[] NZ = new float[(RINGS + 1) * STRIDE];
    private static final Map<UUID, State> STATES = new HashMap<>();

    static {
        for (int ring = 0; ring <= RINGS; ring++) {
            float phi = (float) Math.PI * ring / RINGS;
            for (int seg = 0; seg <= SEGMENTS; seg++) {
                float theta = (float) (Math.PI * 2.0D * seg / SEGMENTS);
                int i = ring * STRIDE + seg;
                NX[i] = Mth.sin(phi) * Mth.cos(theta);
                NY[i] = Mth.cos(phi);
                NZ[i] = Mth.sin(phi) * Mth.sin(theta);
            }
        }
    }

    private DelaySpotBubbleRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        boolean cameraInside = false;
        Set<UUID> active = new HashSet<>();

        for (Player player : level.players()) {
            MobEffectInstance field = player.getEffect(MHAEffects.DELAY_SPOT_FIELD);
            if (field == null) continue;
            active.add(player.getUUID());

            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(time));
            boolean closingNow = field.getAmplifier() >= DelaySpotQuirk.PHASE_CLOSING;

            if (closingNow && !state.closing) {
                state.closing = true;
                state.closeStart = time;
                state.closeFrom = state.lastGrow;
            } else if (!closingNow && state.closing) {
                state.closing = false;
                state.start = time;
                state.snapStart = time;
            }

            if (consumer == null) consumer = buffers.getBuffer(RenderType.debugQuads());

            float snapAge = time - state.snapStart;
            if (!state.closing && snapAge >= 0.0F && snapAge < SNAP_TICKS) {
                Vec3 snapPos = player.getEyePosition(partial).add(player.getViewVector(partial).scale(0.9D)).add(0.0D, -0.35D, 0.0D).subtract(camera);
                poses.pushPose();
                poses.translate(snapPos.x, snapPos.y, snapPos.z);
                drawSnap(consumer, poses.last().pose(), snapPos, snapAge / SNAP_TICKS, player.getId());
                poses.popPose();
            }

            float grow = state.closing ? state.closeFrom * (float) DelaySpotQuirk.shrink(time - state.closeStart) : (float) DelaySpotQuirk.growth(time - state.start);
            state.lastGrow = grow;
            if (grow < 0.01F) continue;

            Vec3 center = player.getPosition(partial).add(0, player.getBbHeight() * 0.5D, 0);
            Vec3 relative = center.subtract(camera);
            float radius = (float) DelaySpotQuirk.RADIUS * grow * (1.0F + Mth.sin(time * 0.1F) * 0.01F);
            boolean inside = relative.lengthSqr() < (double) radius * radius;
            cameraInside |= inside;

            poses.pushPose();
            poses.translate(relative.x, relative.y, relative.z);
            drawBubble(consumer, poses.last().pose(), relative, radius, time, grow, inside);
            poses.popPose();
        }

        STATES.keySet().retainAll(active);

        if (cameraInside && consumer != null) {
            poses.pushPose();
            drawTint(consumer, poses.last().pose(), time);
            poses.popPose();
        }

        if (consumer != null) buffers.endBatch(RenderType.debugQuads());
    }

    private static void drawSnap(VertexConsumer c, Matrix4f m, Vec3 pos, float t, int seed) {
        Vec3 dir = pos.lengthSqr() > 1.0E-6D ? pos.normalize() : new Vec3(0, 0, 1);
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4D) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();

        float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        float fade = (1.0F - t);

        if (t < 0.5F) {
            float fr = 0.30F * (1.0F - t * 1.2F);
            float fa = (1.0F - t * 2.0F);
            fa *= fa * 0.9F;
            int n = 12;
            for (int i = 0; i < n; i++) {
                float a0 = (float) (Math.PI * 2.0D * i / n);
                float a1 = (float) (Math.PI * 2.0D * (i + 1) / n);
                snapVertex(c, m, right, up, 0, 0, 1.0F, 1.0F, 1.0F, fa);
                snapVertex(c, m, right, up, Mth.cos(a0) * fr, Mth.sin(a0) * fr, 0.5F, 1.0F, 0.95F, 0.0F);
                snapVertex(c, m, right, up, Mth.cos(a1) * fr, Mth.sin(a1) * fr, 0.5F, 1.0F, 0.95F, 0.0F);
                snapVertex(c, m, right, up, 0, 0, 1.0F, 1.0F, 1.0F, fa);
            }
        }

        float ringR = 0.15F + 1.05F * ease;
        float ringW = 0.07F * (1.0F - t) + 0.01F;
        float ringA = fade * fade * 0.85F;
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / segs);
            float a1 = (float) (Math.PI * 2.0D * (i + 1) / segs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float in = ringR - ringW, out = ringR + ringW;
            snapVertex(c, m, right, up, c0 * in, s0 * in, 0.55F, 1.0F, 0.95F, ringA * 0.3F);
            snapVertex(c, m, right, up, c0 * out, s0 * out, 0.75F, 1.0F, 1.0F, ringA);
            snapVertex(c, m, right, up, c1 * out, s1 * out, 0.75F, 1.0F, 1.0F, ringA);
            snapVertex(c, m, right, up, c1 * in, s1 * in, 0.55F, 1.0F, 0.95F, ringA * 0.3F);
        }

        int sparks = 10;
        for (int i = 0; i < sparks; i++) {
            float jitter = Mth.sin((i + seed) * 12.9898F) * 0.25F;
            float angle = (float) (Math.PI * 2.0D * i / sparks) + jitter;
            float ca = Mth.cos(angle), sa = Mth.sin(angle);
            float head = 0.2F + (0.9F + 0.3F * (i % 3) / 2.0F) * ease;
            float tail = Math.max(0.0F, head - 0.28F * (1.0F - t * 0.6F));
            float w = 0.025F * (1.0F - t);
            float px = -sa * w, py = ca * w;
            float sa2 = fade * 0.9F;

            snapVertex(c, m, right, up, ca * tail + px, sa * tail + py, 0.4F, 1.0F, 0.9F, 0.0F);
            snapVertex(c, m, right, up, ca * head + px * 0.2F, sa * head + py * 0.2F, 0.9F, 1.0F, 1.0F, sa2);
            snapVertex(c, m, right, up, ca * head - px * 0.2F, sa * head - py * 0.2F, 0.9F, 1.0F, 1.0F, sa2);
            snapVertex(c, m, right, up, ca * tail - px, sa * tail - py, 0.4F, 1.0F, 0.9F, 0.0F);
        }
    }

    private static void snapVertex(VertexConsumer c, Matrix4f m, Vec3 right, Vec3 up, float x, float y, float r, float g, float b, float a) {
        c.addVertex(m, (float) (right.x * x + up.x * y), (float) (right.y * x + up.y * y), (float) (right.z * x + up.z * y)).setColor(r, g, b, a);
    }

    private static void drawBubble(VertexConsumer consumer, Matrix4f matrix, Vec3 relative, float radius, float time, float grow, boolean inside) {
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                int i1 = ring * STRIDE + seg;
                int i2 = (ring + 1) * STRIDE + seg;
                vertex(consumer, matrix, relative, radius, time, grow, inside, i1);
                vertex(consumer, matrix, relative, radius, time, grow, inside, i2);
                vertex(consumer, matrix, relative, radius, time, grow, inside, i2 + 1);
                vertex(consumer, matrix, relative, radius, time, grow, inside, i1 + 1);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 relative, float radius, float time, float grow, boolean inside, int i) {
        float nx = NX[i], ny = NY[i], nz = NZ[i];

        float ripple = Mth.sin(time * 0.11F + ny * 9.0F + nx * 5.0F) * Mth.cos(time * 0.09F + nz * 8.0F);
        float r = radius * (1.0F + (inside ? 0.012F : 0.004F) * ripple);
        float x = nx * r, y = ny * r, z = nz * r;

        double vx = relative.x + x, vy = relative.y + y, vz = relative.z + z;
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        float facing = length > 0.0001D ? (float) Math.abs((nx * vx + ny * vy + nz * vz) / length) : 1.0F;
        float rim = 1.0F - facing;
        rim *= rim;

        float wave = 0.5F + 0.5F * Mth.sin(time * 0.07F + ny * 7.0F + (nx + nz) * 4.0F);

        float phase = swirl(nx, ny, nz, time) + ripple * 1.5F;
        float band = 0.5F + 0.5F * Mth.sin(phase * 2.0F);

        float blend = inside ? 1.0F : 0.3F;
        float red = Mth.lerp(blend, 0.10F + 0.40F * rim, channelR(phase));
        float green = Mth.lerp(blend, 0.85F + 0.15F * rim, channelG(phase));
        float blue = Mth.lerp(blend, 0.75F + 0.25F * rim, channelB(phase));

        float alpha = inside ? 0.10F + 0.12F * band : 0.05F + 0.32F * rim + 0.05F * wave + 0.04F * band;

        alpha += (1.0F - grow) * 0.30F;

        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }

    private static void drawTint(VertexConsumer consumer, Matrix4f matrix, float time) {
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                int i1 = ring * STRIDE + seg;
                int i2 = (ring + 1) * STRIDE + seg;
                tintVertex(consumer, matrix, time, i1);
                tintVertex(consumer, matrix, time, i2);
                tintVertex(consumer, matrix, time, i2 + 1);
                tintVertex(consumer, matrix, time, i1 + 1);
            }
        }
    }

    private static void tintVertex(VertexConsumer consumer, Matrix4f matrix, float time, int i) {
        float nx = NX[i], ny = NY[i], nz = NZ[i];
        float phase = swirl(nx, ny, nz, time * 1.4F);
        float alpha = 0.07F + 0.07F * (0.5F + 0.5F * Mth.sin(phase * 2.0F));
        consumer.addVertex(matrix, nx * 0.5F, ny * 0.5F, nz * 0.5F).setColor(channelR(phase), channelG(phase), channelB(phase), alpha);
    }

    private static float swirl(float nx, float ny, float nz, float time) {
        return time * 0.045F + nx * 3.1F + ny * 4.3F + nz * 2.7F + Mth.sin(time * 0.03F + ny * 6.0F) * 1.6F;
    }

    private static float channelR(float phase) {
        return 0.10F + 0.55F * (0.5F + 0.5F * Mth.sin(phase + 4.2F));
    }

    private static float channelG(float phase) {
        return 0.35F + 0.60F * (0.5F + 0.5F * Mth.sin(phase));
    }

    private static float channelB(float phase) {
        return 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(phase + 2.1F));
    }

    private static final class State {

        float start;
        float snapStart;
        boolean closing;
        float closeStart;
        float closeFrom;
        float lastGrow;

        State(float start) {
            this.start = start;
            this.snapStart = start;
        }
    }
}