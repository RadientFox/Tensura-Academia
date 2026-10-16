package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AirImpacts {

    private static final int MAX_PIECES = 360;
    private static final double SHAKE_RANGE = 40.0D;
    private static final float SHAKE_DECAY = 0.8F;

    private static final class Piece {
        private Vec3 pos;
        private Vec3 old;
        private Vec3 motion;
        private final Vec3 axis;
        private final float spin;
        private float turn;
        private float turnOld;
        private final float size;
        private final float grow;
        private final int life;
        private final float drag;
        private final boolean gathering;
        private final float shade;
        private int age;

        private Piece(Vec3 pos, Vec3 motion, float size, float grow, int life, float drag, boolean gathering, RandomSource random) {
            this.pos = pos;
            this.old = pos;
            this.motion = motion;
            this.axis = new Vec3(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D).normalize();
            this.spin = (random.nextFloat() - 0.5F) * 0.6F;
            this.turn = random.nextFloat() * Mth.TWO_PI;
            this.turnOld = this.turn;
            this.size = size;
            this.grow = grow;
            this.life = life;
            this.drag = drag;
            this.gathering = gathering;
            this.shade = random.nextFloat();
        }
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static float shake;
    private static float shakeOld;

    private AirImpacts() {
    }

    private static void add(Piece piece) {
        if (PIECES.size() < MAX_PIECES) {
            PIECES.add(piece);
        }
    }

    private static Vec3 around(RandomSource random) {
        return new Vec3(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D).normalize();
    }

    // Air pulled in toward a point
    public static void gather(ClientLevel level, Vec3 at, double reach, int count, float size) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 from = at.add(around(random).scale(reach * (0.7D + random.nextDouble() * 0.3D)));
            int life = 6 + random.nextInt(3);
            add(new Piece(from, at.subtract(from).scale(1.0D / life), size * (0.6F + random.nextFloat() * 0.6F), 0.0F, life, 1.0F, true, random));
        }
    }

    private static void burst(ClientLevel level, NewOrderPayloads.ImpactPayload impact) {
        RandomSource random = level.random;
        Vec3 at = impact.at();
        float size = impact.size();
        float strength = switch (impact.kind()) {
            case NewOrderPayloads.ImpactPayload.FIST_BUMP -> {
                for (int i = 0; i < 26; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    double out = 0.5D + random.nextDouble() * 0.6D;
                    add(new Piece(at.add(0.0D, 0.6D, 0.0D), new Vec3(Math.cos(angle) * out, 0.15D + random.nextDouble() * 0.45D, Math.sin(angle) * out),
                            0.8F + random.nextFloat() * 0.9F, 1.2F, 12 + random.nextInt(8), 0.86F, false, random));
                }
                yield 1.0F;
            }
            case NewOrderPayloads.ImpactPayload.SPEAR -> {
                for (int i = 0; i < 14; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    double out = 0.35D + random.nextDouble() * 0.35D;
                    add(new Piece(at.add(0.0D, 0.3D, 0.0D), new Vec3(Math.cos(angle) * out, 0.05D + random.nextDouble() * 0.25D, Math.sin(angle) * out),
                            0.45F + random.nextFloat() * 0.5F, 0.9F, 10 + random.nextInt(6), 0.85F, false, random));
                }
                int ring = Math.min(48, 16 + (int) (size * 2.0F));
                for (int i = 0; i < ring; i++) {
                    double angle = (i + random.nextDouble() * 0.5D) * Mth.TWO_PI / ring;
                    double out = size / 5.3D * (0.9D + random.nextDouble() * 0.2D);
                    add(new Piece(at.add(Math.cos(angle) * 0.8D, 0.25D, Math.sin(angle) * 0.8D), new Vec3(Math.cos(angle) * out, 0.02D, Math.sin(angle) * out),
                            0.6F + random.nextFloat() * 0.5F, 1.4F, 12 + random.nextInt(4), 0.85F, false, random));
                }
                yield 0.6F;
            }
            default -> {
                for (int i = 0; i < 20; i++) {
                    Vec3 out = around(random);
                    add(new Piece(at.add(out.scale(size * 0.3D)), out.scale(0.35D + random.nextDouble() * 0.35D), size * (0.15F + random.nextFloat() * 0.15F), 1.0F,
                            9 + random.nextInt(6), 0.84F, false, random));
                }
                yield impact.kind() == NewOrderPayloads.ImpactPayload.CLAP ? 0.35F : 0.3F;
            }
        };

        Minecraft minecraft = Minecraft.getInstance();
        if (strength > 0.0F && minecraft.player != null) {
            double distance = minecraft.player.position().distanceTo(at);
            if (distance < SHAKE_RANGE) {
                shake = Math.max(shake, strength * (float) (1.0D - distance / SHAKE_RANGE));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            PIECES.clear();
            return;
        }

        for (NewOrderPayloads.ImpactPayload impact : NewOrderPayloads.getClientImpacts()) {
            burst(level, impact);
        }
        NewOrderPayloads.getClientImpacts().clear();
        shakeOld = shake;
        shake = shake < 0.01F ? 0.0F : shake * SHAKE_DECAY;
        if (PIECES.isEmpty() || minecraft.isPaused()) {
            return;
        }

        PIECES.removeIf(piece -> ++piece.age > piece.life);
        for (Piece piece : PIECES) {
            piece.old = piece.pos;
            piece.turnOld = piece.turn;
            piece.turn += piece.spin;
            piece.pos = piece.pos.add(piece.motion);
            piece.motion = piece.motion.scale(piece.drag);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (PIECES.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (RenderType type : new RenderType[]{AirShapes.core(), AirShapes.wind(now, 0.04F, 0.0F)}) {
            VertexConsumer consumer = buffers.getBuffer(type);
            boolean wind = type != AirShapes.core();
            for (Piece piece : PIECES) {
                renderPuff(consumer, poseStack, level, piece, partialTick, wind);
            }
            buffers.endBatch(type);
        }
        poseStack.popPose();
    }

    private static float getScale(Piece piece, float partialTick) {
        float age = piece.age + partialTick;
        if (piece.gathering) {
            return 1.0F - Mth.clamp((age - piece.life * 0.5F) / (piece.life * 0.5F), 0.0F, 1.0F);
        }
        return 1.0F + piece.grow * age / piece.life;
    }

    private static void renderPuff(VertexConsumer consumer, PoseStack poseStack, ClientLevel level, Piece piece, float partialTick, boolean wind) {
        float age = piece.age + partialTick;
        float alpha = piece.gathering ? Mth.clamp(age / 3.0F, 0.0F, 1.0F) : 1.0F - Mth.clamp(age / piece.life, 0.0F, 1.0F);
        float size = piece.size * getScale(piece, partialTick);
        if (alpha <= 0.02F || size <= 0.01F) {
            return;
        }

        Vec3 at = piece.old.lerp(piece.pos, partialTick);
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(new Quaternionf().rotationAxis(Mth.lerp(partialTick, piece.turnOld, piece.turn), (float) piece.axis.x, (float) piece.axis.y, (float) piece.axis.z));
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
        double half = size * 0.5D;
        if (wind) {
            AirShapes.box(consumer, poseStack.last(), Vec3.ZERO, X, Y, Z, half, half, half, AirShapes.fade(AirShapes.WIND_COLOR, alpha), light);
        } else {
            AirShapes.coreBox(consumer, poseStack.last(), Vec3.ZERO, X, Y, Z, half, half, half, piece.shade * 0.5F, 0.0F, AirShapes.fade(AirShapes.CORE_COLOR, alpha), light);
        }
        poseStack.popPose();
    }

    private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);

    // A short shake for anyone near the hit
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amount = Mth.lerp((float) event.getPartialTick(), shakeOld, shake);
        Minecraft minecraft = Minecraft.getInstance();
        if (amount <= 0.01F || minecraft.level == null) {
            return;
        }

        amount *= minecraft.options.screenEffectScale().get().floatValue() * 3.0F;
        float time = minecraft.level.getGameTime() + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + Mth.sin(time * 2.7F) * amount);
        event.setYaw(event.getYaw() + Mth.sin(time * 2.1F + 1.3F) * amount * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(time * 3.3F + 2.1F) * amount * 0.8F);
    }

    static void clear() {
        PIECES.clear();
        shake = 0.0F;
        shakeOld = 0.0F;
    }

}
