package com.radient.tensuraacadamia.client;

import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.network.RewindFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class RewindClient {
    private static final int GOLD = 0xFFD36B;
    private static final int AMBER = 0xFFB13B;
    private static final int CREAM = 0xFFF4D6;
    private static final int WHITE = 0xFFFFFF;

    private static final float FADE_IN = 4.0F;
    private static final float FADE_OUT = 8.0F;
    private static final float FLASH_TICKS = 7.0F;
    private static final int BANDS = 3;
    private static final int STRANDS = 14;
    private static final int SPARKS = 5;
    private static final int ARCS = 2;
    private static final int BEADS = 6;
    private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);

    private static final class Active {
        private int targetId;
        private long start;
        private long stop = -1L;
        private long seed;
    }

    private static final Map<Integer, Active> ACTIVE = new ConcurrentHashMap<>();

    private RewindClient() {
    }

    public static void set(int ownerId, int targetId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long now = level.getGameTime();
        if (targetId == RewindFxPayload.STOP) {
            Active active = ACTIVE.get(ownerId);
            if (active != null && active.stop < 0L) {
                active.stop = now;
            }
            return;
        }

        Active active = new Active();
        active.targetId = targetId;
        active.start = now;
        active.seed = ownerId * 7919L + now;
        ACTIVE.put(ownerId, active);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }

        long now = level.getGameTime();
        ACTIVE.values().removeIf(active -> active.stop >= 0L && now - active.stop > FADE_OUT);
        if (ACTIVE.isEmpty()) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f world = new Matrix4f().translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
        VFXHelper.Layers layers = new VFXHelper.Layers();

        for (Map.Entry<Integer, Active> entry : ACTIVE.entrySet()) {
            Active active = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity owner) || !(level.getEntity(active.targetId) instanceof LivingEntity target)) {
                continue;
            }

            float age = now - active.start + partial;
            float alpha = VFXHelper.MathHelper.smoothstep(0.0F, FADE_IN, age);
            if (active.stop >= 0L) {
                alpha *= 1.0F - Mth.clamp((now - active.stop + partial) / FADE_OUT, 0.0F, 1.0F);
            }
            if (alpha <= 0.01F) {
                continue;
            }

            boolean firstPerson = owner == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 horn = horn(owner, partial);
            if (!firstPerson) {
                VFXHelper.Billboards.glowDisc(layers.glow, world, horn, cam, 0.22F, GOLD, 0.55F * alpha, 16);
                VFXHelper.Billboards.starBurst(layers.glow, world, horn, cam, 4, 0.3F, 0.04F, CREAM, 0.8F * alpha, -age * 0.12D);
            }

            boolean inside = target == mc.player && mc.options.getCameraType().isFirstPerson();
            surge(layers, world, cam, target, partial, age, alpha, active.seed, inside);
            if (target != owner) {
                tether(layers, world, cam, firstPerson ? owner.getEyePosition(partial).add(0.0D, -0.4D, 0.0D) : horn,
                        target.getPosition(partial).add(0.0D, target.getBbHeight() * 0.55D, 0.0D), age, alpha);
            }
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        layers.emit(buffers, world);
        VFXHelper.Layers.endBatch(buffers);
    }

    private static Vec3 horn(LivingEntity owner, float partial) {
        double yaw = Math.toRadians(Mth.lerp(partial, owner.yHeadRotO, owner.yHeadRot));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        return owner.getEyePosition(partial).add(0.0D, 0.28D, 0.0D).add(fwd.scale(0.12D));
    }

    private static Vec3 onBody(Vec3 feet, double radius, double angle, double height) {
        return feet.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void surge(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, LivingEntity target, float partial, float age, float alpha, long seed, boolean inside) {
        float h = target.getBbHeight();
        double radius = target.getBbWidth() * 0.6D + 0.1D;
        Vec3 feet = target.getPosition(partial);
        Vec3 center = feet.add(0.0D, h * 0.5D, 0.0D);

        float flash = 1.0F - Mth.clamp(age / FLASH_TICKS, 0.0F, 1.0F);
        if (flash > 0.0F && !inside) {
            VFXHelper.Billboards.glowDisc(layers.glow, world, center, cam, h * (0.5F + 0.6F * flash), CREAM, 0.85F * flash * flash, 28);
            VFXHelper.Billboards.starBurst(layers.glow, world, center, cam, 8, h * (0.7F + 0.5F * flash), 0.1F, WHITE, flash, seed * 0.13D);
        }

        if (!inside) {
            float beat = 0.5F + 0.5F * Mth.sin(age * 0.7F);
            VFXHelper.Billboards.glowRing(layers.glow, world, center, cam, h * 0.22F, h * 0.38F, h * 0.6F, GOLD, (0.12F + 0.06F * beat) * alpha, 32);
            VFXHelper.Billboards.glowDisc(layers.glow, world, center, cam, h * 0.45F, AMBER, 0.08F * alpha, 24);
        }

        for (int band = 0; band < BANDS; band++) {
            float k = (age * 0.075F + band / (float) BANDS) % 1.0F;
            float fade = Mth.sin(k * Mth.PI) * alpha;
            Vec3 at = feet.add(0.0D, h * k, 0.0D);
            double r = radius * (1.05D + 0.2D * Mth.sin(k * Mth.PI));
            VFXHelper.Billboards.ring(layers.glow, world, at, X, Z, r, 0.1F * fade + 0.02F, GOLD, 0.6F * fade, cam, age * 0.3D, 32);
            VFXHelper.Billboards.ring(layers.glow, world, at, X, Z, r * 0.97D, 0.03F, CREAM, 0.85F * fade, cam, -age * 0.3D, 32);
        }

        RandomSource strands = RandomSource.create(seed * 4099L);
        for (int i = 0; i < STRANDS; i++) {
            double angle = strands.nextDouble() * Mth.TWO_PI;
            float speed = 0.06F + strands.nextFloat() * 0.06F;
            float phase = (age * speed + strands.nextFloat()) % 1.0F;
            double twist = angle + phase * 1.4D;
            double length = h * (0.18D + strands.nextDouble() * 0.15D);
            Vec3 base = onBody(feet, radius * 1.02D, twist, h * phase - length * 0.5D);
            Vec3 tip = onBody(feet, radius * 1.02D, twist + 0.25D, h * phase + length * 0.5D);
            float fade = Mth.sin(phase * Mth.PI) * alpha;
            VFXHelper.Billboards.streak(layers.glow, world, base, tip, 0.02F, 0.07F,
                    VFXHelper.Colors.pack(0.0F, AMBER), VFXHelper.Colors.pack(0.9F * fade, CREAM), cam);
        }

        long step = (long) (age / 2.0F);
        RandomSource flicker = RandomSource.create(seed * 9173L + step);
        for (int i = 0; i < SPARKS; i++) {
            Vec3 spark = onBody(feet, radius * (0.9D + flicker.nextDouble() * 0.3D), flicker.nextDouble() * Mth.TWO_PI, h * flicker.nextDouble());
            VFXHelper.Billboards.starBurst(layers.glow, world, spark, cam, 4, 0.18F + flicker.nextFloat() * 0.12F, 0.03F, CREAM,
                    0.9F * alpha, flicker.nextDouble() * Mth.TWO_PI);
        }

        for (int i = 0; i < ARCS; i++) {
            double angle = flicker.nextDouble() * Mth.TWO_PI;
            double height = h * (0.15D + flicker.nextDouble() * 0.7D);
            Vec3 from = onBody(feet, radius, angle, height);
            Vec3 to = onBody(feet, radius, angle + 0.8D + flicker.nextDouble() * 0.8D, height + h * (flicker.nextDouble() - 0.5D) * 0.4D);
            VFXHelper.Shapes.drawLightningArc(layers.glow, world, from, to, 0.12F, 3, 0.025F, VFXHelper.Colors.pack(0.85F * alpha, CREAM), seed + step * 13L + i);
        }

        if (!inside) {
            VFXHelper.Billboards.streak(layers.glow, world, feet, feet.add(UP.scale(h * 1.15D)), (float) radius * 1.6F, (float) radius * 0.4F,
                    VFXHelper.Colors.pack(0.18F * alpha, GOLD), VFXHelper.Colors.pack(0.0F, CREAM), cam);
        }
    }

    private static void tether(VFXHelper.Layers layers, Matrix4f world, Vec3 cam, Vec3 from, Vec3 to, float age, float alpha) {
        VFXHelper.Billboards.streak(layers.glow, world, from, to, 0.12F, 0.2F, VFXHelper.Colors.pack(0.35F * alpha, GOLD), VFXHelper.Colors.pack(0.25F * alpha, AMBER), cam);
        VFXHelper.Billboards.streak(layers.glow, world, from, to, 0.03F, 0.05F, VFXHelper.Colors.pack(0.8F * alpha, CREAM), VFXHelper.Colors.pack(0.6F * alpha, CREAM), cam);
        Vec3 span = to.subtract(from);
        for (int i = 0; i < BEADS; i++) {
            float k = 1.0F - ((age * 0.06F + i / (float) BEADS) % 1.0F);
            Vec3 bead = from.add(span.scale(k));
            VFXHelper.Billboards.glowDisc(layers.glow, world, bead, cam, 0.09F, CREAM, 0.8F * alpha * Mth.sin(k * Mth.PI), 8);
        }
    }
}
