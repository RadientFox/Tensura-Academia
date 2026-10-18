package com.radient.tensuraacadamia.client;

import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.network.SpiderSwingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class SpiderClient {
    private static final int WEB = 0xF4F4F4;
    private static final double MIN_ROPE = 2.0D;
    private static final double EXTRA_GRAVITY = 0.035D;
    private static final double PUMP = 0.075D;
    private static final double TENSION = 0.5D;
    private static final double AIR_DRAG = 0.91D;
    private static final double KEEP = 0.996D;
    private static final double MAX_SPEED = 3.2D;
    private static final double START_PUSH = 0.35D;
    private static final double RELEASE_FORWARD = 0.45D;
    private static final double RELEASE_UP = 0.35D;
    private static final double LEAP_FORWARD = 0.35D;
    private static final double LEAP_UP = 0.85D;
    private static final double ZIP_SPEED = 1.5D;
    private static final double ZIP_UP = 0.3D;
    private static final int ZIP_COOLDOWN = 20;
    private static final int GROUND_RELEASE = 8;
    private static final double TOP_RELEASE = 1.0D;
    private static final float WEB_TRAVEL = 3.0F;
    private static final float FOV_PER_SPEED = 7.0F;
    private static final float FOV_MAX = 18.0F;

    private record Swing(Vec3 anchor, long attachedAt) {}

    private static final Map<Integer, Swing> SWINGS = new ConcurrentHashMap<>();
    private static double rope;
    private static boolean wasShift;
    private static boolean wasJumping;
    private static boolean airChain;
    private static int zipCooldown;
    private static int groundTicks;
    private static float fovBoost;

    private SpiderClient() {
    }

    public static @Nullable Vec3 getAnchor(int entityId) {
        Swing swing = SWINGS.get(entityId);
        return swing == null ? null : swing.anchor();
    }

    public static void receive(int entityId, int state, Vec3 anchor) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean local = player != null && player.getId() == entityId;
        if (state == SpiderSwingPayload.ATTACH) {
            long now = mc.level == null ? 0L : mc.level.getGameTime();
            SWINGS.put(entityId, new Swing(anchor, now));
            if (local) {
                rope = Math.max(MIN_ROPE, center(player).distanceTo(anchor));
                Vec3 motion = player.getDeltaMovement();
                if (motion.horizontalDistance() < 0.6D) {
                    player.setDeltaMovement(motion.add(flatLook(player).scale(START_PUSH)));
                }
                airChain = true;
                groundTicks = 0;
                wasShift = true;
                wasJumping = true;
            }
        } else {
            boolean wasSwinging = SWINGS.remove(entityId) != null;
            if (local && wasSwinging && state == SpiderSwingPayload.RELEASE) {
                launch(player, false);
            }
        }
        SpiderQuirk.clientSwinging = player != null && SWINGS.containsKey(player.getId());
    }

    private static Vec3 center(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static Vec3 flatLook(LivingEntity entity) {
        Vec3 look = entity.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        return flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    private static void launch(LocalPlayer player, boolean leap) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 flat = new Vec3(motion.x, 0.0D, motion.z);
        flat = flat.lengthSqr() < 0.01D ? flatLook(player) : flat.normalize();
        Vec3 boosted = motion.add(flat.scale(leap ? LEAP_FORWARD : RELEASE_FORWARD)).add(0.0D, leap ? LEAP_UP : RELEASE_UP, 0.0D);
        if (boosted.y < (leap ? LEAP_UP : RELEASE_UP)) {
            boosted = new Vec3(boosted.x, leap ? LEAP_UP : RELEASE_UP, boosted.z);
        }
        if (boosted.length() > MAX_SPEED) {
            boosted = boosted.normalize().scale(MAX_SPEED);
        }
        player.setDeltaMovement(boosted);
        player.fallDistance = 0.0F;
        airChain = true;
        zipCooldown = 0;
    }

    private static void detach(LocalPlayer player, int state) {
        SWINGS.remove(player.getId());
        SpiderQuirk.clientSwinging = false;
        PacketDistributor.sendToServer(new SpiderSwingPayload(player.getId(), state, Vec3.ZERO));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            SWINGS.clear();
            SpiderQuirk.clientSwinging = false;
            airChain = false;
            SpiderCeilingClient.clear();
            return;
        }

        SWINGS.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive());
        Swing swing = SWINGS.get(player.getId());
        SpiderQuirk.clientSwinging = swing != null;
        boolean shift = player.input.shiftKeyDown;
        boolean jumping = player.input.jumping;
        boolean shiftPressed = shift && !wasShift;
        boolean jumpPressed = jumping && !wasJumping;
        wasShift = shift;
        wasJumping = jumping;
        if (zipCooldown > 0) {
            zipCooldown--;
        }

        if (SpiderCeilingClient.tick(player, shiftPressed, jumpPressed)) {
            airChain = false;
            return;
        }

        if (swing == null) {
            if (player.onGround() || player.isInWater() || player.isFallFlying()) {
                airChain = false;
            } else if (airChain && jumpPressed && zipCooldown == 0 && SpiderQuirk.getSpider(player).isPresent()) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.scale(ZIP_SPEED).add(0.0D, ZIP_UP, 0.0D));
                player.fallDistance = 0.0F;
                zipCooldown = ZIP_COOLDOWN;
                PacketDistributor.sendToServer(new SpiderSwingPayload(player.getId(), SpiderSwingPayload.ZIP, Vec3.ZERO));
            }
            return;
        }

        if (shiftPressed) {
            detach(player, SpiderSwingPayload.DETACH);
            return;
        }

        if (jumpPressed) {
            detach(player, SpiderSwingPayload.LEAP);
            launch(player, true);
            return;
        }

        Vec3 anchor = swing.anchor();
        Vec3 center = center(player);
        if (center.y >= anchor.y - TOP_RELEASE && player.getDeltaMovement().y > 0.0D) {
            detach(player, SpiderSwingPayload.LEAP);
            launch(player, false);
            return;
        }

        groundTicks = player.onGround() ? groundTicks + 1 : 0;
        if (groundTicks > GROUND_RELEASE) {
            detach(player, SpiderSwingPayload.DETACH);
            return;
        }

        Vec3 toAnchor = anchor.subtract(center);
        double distance = toAnchor.length();
        if (distance < 1.0E-4D) {
            return;
        }

        Vec3 dir = toAnchor.scale(1.0D / distance);
        Vec3 motion = player.getDeltaMovement().add(0.0D, -EXTRA_GRAVITY, 0.0D);

        double yaw = Math.toRadians(player.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 wish = fwd.scale(player.input.forwardImpulse).add(left.scale(player.input.leftImpulse));
        if (wish.lengthSqr() > 1.0E-4D) {
            double strength = Math.min(1.0D, wish.length());
            Vec3 tangent = wish.subtract(dir.scale(wish.dot(dir)));
            if (tangent.lengthSqr() > 1.0E-4D) {
                motion = motion.add(tangent.normalize().scale(PUMP * strength));
            }
        }

        motion = new Vec3(motion.x / AIR_DRAG * KEEP, motion.y, motion.z / AIR_DRAG * KEEP);

        if (distance > rope) {
            double outward = motion.dot(dir);
            if (outward < 0.0D) {
                motion = motion.subtract(dir.scale(outward));
            }
            motion = motion.add(dir.scale((distance - rope) * TENSION));
        }

        if (motion.length() > MAX_SPEED) {
            motion = motion.normalize().scale(MAX_SPEED);
        }
        player.setDeltaMovement(motion);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            fovBoost = 0.0F;
            return;
        }

        boolean flying = SWINGS.containsKey(player.getId()) || airChain;
        float target = flying ? Math.min(FOV_MAX, (float) player.getDeltaMovement().length() * FOV_PER_SPEED) : 0.0F;
        fovBoost += (target - fovBoost) * 0.08F;
        if (fovBoost > 0.05F) {
            event.setFOV(event.getFOV() + fovBoost);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SWINGS.isEmpty()) {
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
        long now = level.getGameTime();
        for (Map.Entry<Integer, Swing> entry : SWINGS.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living)) {
                continue;
            }

            Vec3 hand = hand(mc, living, partial);
            float travel = Mth.clamp((now - entry.getValue().attachedAt() + partial) / WEB_TRAVEL, 0.0F, 1.0F);
            Vec3 tip = hand.lerp(entry.getValue().anchor(), travel);
            VFXHelper.Billboards.streak(layers.glow, world, hand, tip, 0.07F, 0.05F, VFXHelper.Colors.pack(0.25F, WEB), VFXHelper.Colors.pack(0.2F, WEB), cam);
            VFXHelper.Billboards.streak(layers.glow, world, hand, tip, 0.025F, 0.025F, VFXHelper.Colors.pack(0.95F, WEB), VFXHelper.Colors.pack(0.85F, WEB), cam);
            if (travel >= 1.0F) {
                VFXHelper.Billboards.glowDisc(layers.glow, world, tip, cam, 0.25F, WEB, 0.5F, 10);
            }
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        layers.emit(buffers, world);
        VFXHelper.Layers.endBatch(buffers);
    }

    private static Vec3 hand(Minecraft mc, LivingEntity living, float partial) {
        double yaw = Math.toRadians(Mth.lerp(partial, living.yBodyRotO, living.yBodyRot));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        if (living == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 look = living.getViewVector(partial);
            return living.getEyePosition(partial).add(look.scale(0.4D)).add(right.scale(0.3D)).add(0.0D, -0.25D, 0.0D);
        }
        return living.getPosition(partial).add(0.0D, living.getBbHeight() * 0.85D, 0.0D).add(right.scale(0.35D));
    }
}
