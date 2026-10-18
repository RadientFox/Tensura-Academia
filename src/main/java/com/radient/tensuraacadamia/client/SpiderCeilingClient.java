package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.network.SpiderCeilingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class SpiderCeilingClient {
    public static final int NONE = SpiderCeilingPayload.NONE;
    public static final int CRAWL = SpiderCeilingPayload.CRAWL;
    public static final int HANG = SpiderCeilingPayload.HANG;

    private static final double GRAB = 0.1D;
    private static final double HOLD = 1.3D;
    private static final double PULL = 0.08D;
    private static final double SCAN = 1.5D;
    private static final double WALK_PER_SPEED = 2.2D;
    private static final double DROP_FORWARD = 0.15D;
    private static final double DROP_DOWN = -0.2D;
    private static final float DROP_LENGTH = 10.0F;
    private static final float CRAWL_CENTER = 0.3F;
    private static final float CRAWL_RAISE = 0.15F;
    private static final float HANG_SWAY = 0.05F;

    private static final Map<Integer, Integer> MODES = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> DROPS = new ConcurrentHashMap<>();
    private static final Deque<Integer> TRANSFORMED = new ArrayDeque<>();
    private static int localMode = NONE;
    private static boolean forced;

    private SpiderCeilingClient() {
    }

    public static void receive(int entityId, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        if (kind == SpiderCeilingPayload.DROP) {
            DROPS.put(entityId, mc.level.getGameTime());
            MODES.remove(entityId);
        } else if (kind == NONE) {
            MODES.remove(entityId);
        } else {
            MODES.put(entityId, kind);
        }

        LocalPlayer player = mc.player;
        if (player != null && player.getId() == entityId && kind != localMode && kind != SpiderCeilingPayload.DROP) {
            apply(player, kind, false);
        }
    }

    public static int getMode(LivingEntity entity) {
        return MODES.getOrDefault(entity.getId(), NONE);
    }

    public static float dropProgress(LivingEntity entity, float partial) {
        Long start = DROPS.get(entity.getId());
        if (start == null) {
            return -1.0F;
        }

        float age = entity.level().getGameTime() - start + partial;
        return age < 0.0F || age > DROP_LENGTH ? -1.0F : age / DROP_LENGTH;
    }

    public static void clear() {
        MODES.clear();
        DROPS.clear();
        localMode = NONE;
        forced = false;
        SpiderQuirk.clientCeiling = NONE;
    }

    public static boolean tick(LocalPlayer player, boolean shiftPressed, boolean jumpPressed) {
        ClientLevel level = (ClientLevel) player.level();
        long now = level.getGameTime();
        DROPS.values().removeIf(start -> now < start || now - start > DROP_LENGTH);

        if (!SpiderQuirk.canCling(player)) {
            setMode(player, NONE);
            return false;
        }

        double gap = ceilingGap(player);
        if (localMode == NONE) {
            if (player.onGround() || gap > GRAB || !setMode(player, CRAWL)) {
                return false;
            }
            crawl(player, ceilingGap(player));
            return true;
        }

        if (jumpPressed) {
            drop(player);
            return true;
        }
        if (gap > HOLD) {
            setMode(player, NONE);
            return false;
        }

        if (shiftPressed && setMode(player, localMode == CRAWL ? HANG : CRAWL)) {
            gap = ceilingGap(player);
        }
        if (localMode == HANG) {
            hold(player, gap);
        } else {
            crawl(player, gap);
        }
        return true;
    }

    private static boolean setMode(LocalPlayer player, int mode) {
        if (mode == localMode) {
            return true;
        }

        float from = player.getBbHeight();
        float to = player.getDimensions(mode == CRAWL ? Pose.SWIMMING : Pose.STANDING).height();
        Vec3 moved = player.position().add(0.0D, from - to, 0.0D);
        boolean fits = player.level().noCollision(player, player.getDimensions(mode == CRAWL ? Pose.SWIMMING : Pose.STANDING).makeBoundingBox(moved));
        if (!fits && mode != NONE) {
            return false;
        }

        apply(player, mode, true);
        if (fits) {
            if (mode == NONE) {
                player.setPose(Pose.STANDING);
            }
            player.setPos(moved);
        }
        return true;
    }

    private static void apply(LocalPlayer player, int mode, boolean send) {
        localMode = mode;
        SpiderQuirk.clientCeiling = mode;
        if (mode == NONE) {
            MODES.remove(player.getId());
            if (forced) {
                forced = false;
                player.setForcedPose(null);
            }
        } else {
            MODES.put(player.getId(), mode);
            Pose pose = mode == CRAWL ? Pose.SWIMMING : Pose.STANDING;
            forced = true;
            player.setForcedPose(pose);
            player.setPose(pose);
        }
        player.refreshDimensions();
        if (send) {
            PacketDistributor.sendToServer(new SpiderCeilingPayload(player.getId(), mode));
        }
    }

    private static void drop(LocalPlayer player) {
        setMode(player, NONE);
        Vec3 fwd = forward(player.getYRot());
        player.setDeltaMovement(fwd.x * DROP_FORWARD, DROP_DOWN, fwd.z * DROP_FORWARD);
        player.fallDistance = 0.0F;
        DROPS.put(player.getId(), player.level().getGameTime());
        PacketDistributor.sendToServer(new SpiderCeilingPayload(player.getId(), SpiderCeilingPayload.DROP));
    }

    private static double ceilingGap(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        AABB above = new AABB(box.minX + 0.01D, box.maxY, box.minZ + 0.01D, box.maxX - 0.01D, box.maxY + SCAN, box.maxZ - 0.01D);
        double gap = Double.MAX_VALUE;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, above)) {
            if (shape.isEmpty()) {
                continue;
            }

            double bottom = shape.min(Direction.Axis.Y);
            if (bottom >= box.maxY - 1.0E-3D) {
                gap = Math.min(gap, bottom - box.maxY);
            }
        }
        return gap;
    }

    private static void crawl(LocalPlayer player, double gap) {
        Vec3 fwd = forward(player.getYRot());
        Vec3 left = new Vec3(-fwd.z, 0.0D, fwd.x);
        Vec3 wish = fwd.scale(player.input.forwardImpulse).add(left.scale(player.input.leftImpulse));
        wish = wish.lengthSqr() > 1.0E-4D ? wish.normalize() : Vec3.ZERO;

        Vec3 target = wish.scale(player.getAttributeValue(Attributes.MOVEMENT_SPEED) * WALK_PER_SPEED);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(Mth.lerp(0.5D, motion.x, target.x), Math.min(0.5D, gap + PULL), Mth.lerp(0.5D, motion.z, target.z));
        player.fallDistance = 0.0F;
    }

    private static void hold(LocalPlayer player, double gap) {
        player.setDeltaMovement(0.0D, Math.min(0.5D, gap + PULL), 0.0D);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (localMode == HANG && event.getEntity() == Minecraft.getInstance().player) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
        }
    }

    private static Vec3 forward(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled()) {
            return;
        }

        Player player = event.getEntity();
        float partial = event.getPartialTick();
        int mode = getMode(player);
        float drop = mode == NONE ? dropProgress(player, partial) : -1.0F;
        if (mode == NONE && drop < 0.0F) {
            return;
        }

        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        TRANSFORMED.push(player.getId());

        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 fwd = forward(yaw);
        float height = player.getBbHeight();
        if (mode == CRAWL) {
            rotateAbout(poses, fwd, Mth.PI, CRAWL_CENTER);
            poses.translate(0.0F, CRAWL_RAISE, 0.0F);
        } else if (mode == HANG) {
            Vec3 side = new Vec3(-fwd.z, 0.0D, fwd.x);
            float t = player.tickCount + partial;
            rotateAbout(poses, side, Mth.sin(t * 0.07F) * HANG_SWAY, height);
            rotateAbout(poses, fwd, Mth.PI, height * 0.5F);
        } else {
            float ease = drop * drop * (3.0F - 2.0F * drop);
            rotateAbout(poses, fwd, Mth.PI * (1.0F - ease), height * 0.5F);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (!TRANSFORMED.isEmpty() && TRANSFORMED.peek().equals(event.getEntity().getId())) {
            TRANSFORMED.pop();
            event.getPoseStack().popPose();
        }
    }

    private static void rotateAbout(PoseStack poses, Vec3 axis, float angle, float pivotY) {
        poses.translate(0.0F, pivotY, 0.0F);
        poses.mulPose(new Quaternionf().rotationAxis(angle, (float) axis.x, (float) axis.y, (float) axis.z));
        poses.translate(0.0F, -pivotY, 0.0F);
    }
}
