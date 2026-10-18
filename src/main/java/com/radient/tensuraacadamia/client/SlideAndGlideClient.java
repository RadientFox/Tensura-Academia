package com.radient.tensuraacadamia.client;


import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ExtraArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SlideAndGlideQuirk;
import com.radient.tensuraacadamia.network.SlideAndGlideBurstPayload;
import com.radient.tensuraacadamia.network.SlideAndGlideMotionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class SlideAndGlideClient {
    private static final int CYAN = 0x3FE6FF;
    private static final int DEEP = 0x1A9FD8;
    private static final int CORE = 0xE6FFFF;
    private static final int WHITE = 0xFFFFFF;

    private static final int TRAIL_NODES = 14;
    private static final double LADDER_CLIMB = 0.2D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);

    private record Burst(int kind, Vec3 pos, Vec3 dir, long born, long seed) {}

    private static final Map<Integer, Integer> FLAGS = new ConcurrentHashMap<>();
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new ConcurrentHashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static long burstSeed;

    private SlideAndGlideClient() {
    }

    public static void setFlags(int entityId, int flags) {
        if (flags == 0) {
            FLAGS.remove(entityId);
        } else {
            FLAGS.put(entityId, flags);
        }
    }

    public static int getFlags(int entityId) {
        return FLAGS.getOrDefault(entityId, 0);
    }

    public static void addBurst(int kind, Vec3 pos, Vec3 dir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            BURSTS.add(new Burst(kind, pos, dir, level.getGameTime(), ++burstSeed));
        }
    }

    private static float burstLife(int kind) {
        return switch (kind) {
            case SlideAndGlideBurstPayload.KNUCKLE_BLAST -> 12.0F;
            case SlideAndGlideBurstPayload.SLAP_REPEL -> 10.0F;
            default -> 5.0F;
        };
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            FLAGS.clear();
            TRAILS.clear();
            BURSTS.clear();
            return;
        }

        long now = level.getGameTime();
        BURSTS.removeIf(burst -> now < burst.born() || now - burst.born() > burstLife(burst.kind()));

        Iterator<Map.Entry<Integer, Integer>> flags = FLAGS.entrySet().iterator();
        while (flags.hasNext()) {
            Map.Entry<Integer, Integer> entry = flags.next();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living) || !living.isAlive()) {
                flags.remove();
                TRAILS.remove(entry.getKey());
            }
        }

        TRAILS.values().removeIf(ArrayDeque::isEmpty);
        for (Map.Entry<Integer, Integer> entry : FLAGS.entrySet()) {
            if ((entry.getValue() & SlideAndGlideQuirk.FLAG_SLIDING) != 0 && level.getEntity(entry.getKey()) instanceof LivingEntity living
                    && SlideAndGlideMotionClient.glideAmount(living) > 0.0F) {
                ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(entry.getKey(), id -> new ArrayDeque<>());
                boolean ceiling = SlideAndGlideMotionClient.getMode(living) == SlideAndGlideMotionPayload.CEILING;
                trail.addFirst(living.position().add(0.0D, ceiling ? living.getBbHeight() - 0.05D : 0.05D, 0.0D));
                while (trail.size() > TRAIL_NODES) {
                    trail.removeLast();
                }
            }
        }

        for (Map.Entry<Integer, ArrayDeque<Vec3>> entry : TRAILS.entrySet()) {
            boolean gliding = (FLAGS.getOrDefault(entry.getKey(), 0) & SlideAndGlideQuirk.FLAG_SLIDING) != 0
                    && level.getEntity(entry.getKey()) instanceof LivingEntity living && SlideAndGlideMotionClient.glideAmount(living) > 0.0F;
            if (!gliding && !entry.getValue().isEmpty()) {
                entry.getValue().removeLast();
            }
        }

        climb(mc.player);
    }

    private static void climb(LocalPlayer player) {
        if (player == null || !player.horizontalCollision || player.isShiftKeyDown() || !SlideAndGlideQuirk.canWallSlide(player)) {
            return;
        }

        double speed = SlideAndGlideQuirk.getSlideAndGlide(player).map(SlideAndGlideQuirk::getSlideSpeed).orElse(0.0D);
        double climb = LADDER_CLIMB * (1.0D + speed);
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < climb) {
            player.setDeltaMovement(motion.x, climb, motion.z);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (FLAGS.isEmpty() && BURSTS.isEmpty() && TRAILS.isEmpty())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f world = new Matrix4f().translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
        VFXHelper.Layers layers = new VFXHelper.Layers();

        for (Map.Entry<Integer, ArrayDeque<Vec3>> entry : TRAILS.entrySet()) {
            drawTrail(layers, world, cam, entry.getValue());
        }

        for (Map.Entry<Integer, Integer> entry : FLAGS.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && !living.isInvisible()) {
                boolean firstPerson = living == mc.player && mc.options.getCameraType().isFirstPerson();
                drawUser(layers, world, cam, living, entry.getValue(), partial, firstPerson);
            }
        }

        long now = level.getGameTime();
        for (Burst burst : BURSTS) {
            drawBurst(layers, world, cam, burst, now - burst.born() + partial);
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        layers.emit(buffers, world);
        VFXHelper.Layers.endBatch(buffers);
    }

    private static void drawTrail(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, ArrayDeque<Vec3> trail) {
        if (trail.size() < 2) {
            return;
        }

        Vec3[] pts = trail.toArray(new Vec3[0]);
        float[] outer = new float[pts.length];
        float[] inner = new float[pts.length];
        int[] glow = new int[pts.length];
        int[] core = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            float k = 1.0F - (float) i / (pts.length - 1);
            outer[i] = 0.55F * k + 0.05F;
            inner[i] = 0.2F * k + 0.02F;
            glow[i] = VFXHelper.Colors.pack(0.35F * k, CYAN);
            core[i] = VFXHelper.Colors.pack(0.7F * k * k, CORE);
        }

        VFXHelper.Billboards.ribbon(layers.glow, world, pts, outer, glow, cam);
        VFXHelper.Billboards.ribbon(layers.glow, world, pts, inner, core, cam);
    }

    private static void drawUser(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, LivingEntity living, int flags, float partial, boolean firstPerson) {
        Vec3 pos = living.getPosition(partial);
        double yaw = Math.toRadians(Mth.lerp(partial, living.yBodyRotO, living.yBodyRot));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        float h = living.getBbHeight();
        float t = living.tickCount + partial;
        boolean onWall = !living.onGround() && ExtraArmsQuirk.isTouchingWall(living);

        int mode = SlideAndGlideMotionClient.getMode(living);
        float glide = SlideAndGlideMotionClient.glideAmount(living);
        if ((flags & SlideAndGlideQuirk.FLAG_SLIDING) != 0 && glide > 0.0F) {
            if (mode != SlideAndGlideMotionPayload.UPRIGHT) {
                crawlPads(layers, world, cam, pos.add(0.0D, mode == SlideAndGlideMotionPayload.CEILING ? h - 0.03D : 0.03D, 0.0D), fwd, right, t, glide);
            } else if (onWall) {
                wallPads(layers, world, cam, pos, fwd, right, h, t, 0.55F * glide);
            } else if (living.onGround()) {
                groundPads(layers, world, cam, pos, right, t, 0.55F * glide);
            }
        }

        if ((flags & SlideAndGlideQuirk.FLAG_SLAPPING) != 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 palm = pos.add(0.0D, h * 0.62D, 0.0D).add(right.scale(0.33D * side)).add(fwd.scale(0.55D));
                float pulse = 1.0F + 0.06F * Mth.sin(t * 0.8F + side);
                VFXHelper.Billboards.ring(layers.glow, world, palm, right, UP, 0.24D * pulse, 0.07F, CYAN, 0.9F, cam, t * 0.15D, 28);
                VFXHelper.Billboards.ring(layers.glow, world, palm, right, UP, 0.24D * pulse, 0.025F, CORE, 0.9F, cam, t * 0.15D, 28);
                VFXHelper.Billboards.glowDisc(layers.glow, world, palm, cam, 0.32F, CYAN, 0.12F, 16);
            }
        }

        if (firstPerson) {
            return;
        }

        if ((flags & SlideAndGlideQuirk.FLAG_KNUCKLE) != 0) {
            Vec3 fist = pos.add(0.0D, h * 0.42D, 0.0D).add(right.scale(0.38D)).add(fwd.scale(0.12D));
            float twinkle = 0.6F + 0.4F * VFXHelper.MathHelper.sharpPulse(t, 1.3F);
            VFXHelper.Billboards.glowDisc(layers.glow, world, fist, cam, 0.16F, CYAN, 0.45F, 16);
            VFXHelper.Billboards.starBurst(layers.glow, world, fist, cam, 4, 0.38F * twinkle, 0.05F, WHITE, 0.9F, t * 0.05D);
            VFXHelper.Billboards.starBurst(layers.glow, world, fist, cam, 4, 0.2F, 0.035F, CORE, 0.7F * twinkle, Math.PI * 0.25D - t * 0.08D);
        }

        if ((flags & SlideAndGlideQuirk.FLAG_OOMPH) != 0) {
            Vec3 center = pos.add(0.0D, h * 0.5D, 0.0D);
            float beat = 0.5F + 0.5F * Mth.sin(t * 0.5F);
            VFXHelper.Billboards.glowRing(layers.glow, world, center, cam, h * 0.32F, h * 0.42F, h * 0.58F, CYAN, 0.1F + 0.08F * beat, 32);
        }
    }

    private static void groundPads(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, Vec3 pos, Vec3 right, float t, float strength) {
        for (int side = -1; side <= 1; side += 2) {
            Vec3 foot = pos.add(right.scale(0.17D * side)).add(0.0D, 0.03D, 0.0D);
            float pulse = 1.0F + 0.08F * Mth.sin(t * 0.7F + side * 1.3F);
            VFXHelper.Shapes.drawFlatDisc(layers.glow, world, foot, 0.32F * pulse, 20, VFXHelper.Colors.pack(0.5F * strength, CYAN), VFXHelper.Colors.pack(0.0F, DEEP));
            VFXHelper.Billboards.ring(layers.glow, world, foot, X, Z, 0.26D * pulse, 0.05F, CORE, 0.8F * strength, cam, t * 0.1D, 24);
        }
    }

    private static void crawlPads(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, Vec3 plane, Vec3 fwd, Vec3 right, float t, float strength) {
        for (int side = -1; side <= 1; side += 2) {
            for (int end = 0; end < 2; end++) {
                Vec3 pad = end == 0 ? plane.add(fwd.scale(1.05D)).add(right.scale(0.45D * side)) : plane.add(fwd.scale(-0.85D)).add(right.scale(0.4D * side));
                float pulse = 1.0F + 0.1F * Mth.sin(t * 0.7F + side + end * 2.0F);
                VFXHelper.Shapes.drawFlatDisc(layers.glow, world, pad, 0.3F * pulse, 20, VFXHelper.Colors.pack(0.55F * strength, CYAN), VFXHelper.Colors.pack(0.0F, DEEP));
                VFXHelper.Billboards.ring(layers.glow, world, pad, X, Z, 0.24D * pulse, 0.05F, CORE, 0.85F * strength, cam, t * 0.1D, 24);
            }
        }
    }

    private static void wallPads(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, Vec3 pos, Vec3 fwd, Vec3 right, float h, float t, float strength) {
        Vec3 wall = pos.add(fwd.scale(0.32D));
        for (int side = -1; side <= 1; side += 2) {
            for (int row = 0; row < 2; row++) {
                Vec3 pad = wall.add(right.scale(0.22D * side)).add(0.0D, h * (row == 0 ? 0.2D : 0.72D), 0.0D);
                float pulse = 1.0F + 0.08F * Mth.sin(t * 0.7F + side + row * 2.0F);
                VFXHelper.Billboards.glowDisc(layers.glow, world, pad, cam, 0.26F * pulse, CYAN, 0.35F * strength, 16);
                VFXHelper.Billboards.ring(layers.glow, world, pad, right, UP, 0.2D * pulse, 0.045F, CORE, 0.8F * strength, cam, t * 0.1D, 24);
            }
        }
    }

    private static void drawBurst(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, Burst burst, float age) {
        float life = burstLife(burst.kind());
        float k = Mth.clamp(age / life, 0.0F, 1.0F);
        float fade = 1.0F - k;
        switch (burst.kind()) {
            case SlideAndGlideBurstPayload.KNUCKLE_BLAST ->
                    VFXHelper.Firing.explosion(layers, world, cam, burst.pos(), age, life, 0.9F, CORE, CYAN, burst.seed());
            case SlideAndGlideBurstPayload.SLAP_REPEL -> {
                VFXHelper.MathHelper.Basis basis = VFXHelper.MathHelper.Basis.of(burst.dir());
                float grow = VFXHelper.MathHelper.easeOutCubic(k);
                VFXHelper.Billboards.ring(layers.glow, world, burst.pos(), basis.side(), basis.up(), 0.3D + 1.1D * grow, 0.12F * fade + 0.02F, CYAN, fade, cam, age * 0.2D, 36);
                VFXHelper.Billboards.ring(layers.glow, world, burst.pos(), basis.side(), basis.up(), 0.2D + 0.7D * grow, 0.05F * fade + 0.01F, WHITE, fade, cam, -age * 0.2D, 36);
                if (age < 3.0F) {
                    VFXHelper.Billboards.starBurst(layers.glow, world, burst.pos(), cam, 8, 0.9F, 0.06F, WHITE, 0.8F * (1.0F - age / 3.0F), burst.seed() * 0.3D);
                }
            }
            default -> VFXHelper.Firing.muzzleFlash(layers.glow, world, cam, burst.pos(), 0.55F, 0.1F, CYAN, CORE, fade, age * 0.3D);
        }
    }
}
