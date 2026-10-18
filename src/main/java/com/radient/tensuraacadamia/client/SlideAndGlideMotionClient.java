package com.radient.tensuraacadamia.client;

import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ExtraArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SlideAndGlideQuirk;
import com.radient.tensuraacadamia.network.SlideAndGlideMotionPayload;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class SlideAndGlideMotionClient {
    private static final int UPRIGHT = SlideAndGlideMotionPayload.UPRIGHT;
    private static final int CRAWL = SlideAndGlideMotionPayload.CRAWL;
    private static final int CEILING = SlideAndGlideMotionPayload.CEILING;

    private static final double CEILING_GRAB = 0.1D;
    private static final double CEILING_HOLD = 1.3D;
    private static final double CEILING_PULL = 0.08D;
    private static final double CEILING_SCAN = 1.5D;
    private static final double WALK_PER_SPEED = 2.2D;

    private static final double FLIP_FORWARD = 0.45D;
    private static final double FLIP_UP = 1.3D;
    private static final double POUNCE_FORWARD = 1.1D;
    private static final double POUNCE_UP = 0.32D;
    private static final double KICK_AWAY = 0.75D;
    private static final double KICK_UP = 0.65D;
    private static final double KICK_LOOK = 0.25D;
    private static final double DROP_FORWARD = 0.5D;
    private static final double DROP_DOWN = -0.3D;

    private static final float CRAWL_RAISE = 0.15F;
    private static final float CRAWL_CENTER = 0.3F;
    private static final float MAX_LEAN = 0.45F;
    private static final float GLIDE_START = 0.03F;
    private static final float GLIDE_FULL = 0.12F;

    private record Jump(int kind, long start) {}

    private static final Map<Integer, Integer> MODES = new ConcurrentHashMap<>();
    private static final Map<Integer, Jump> JUMPS = new ConcurrentHashMap<>();
    private static final Deque<Integer> TRANSFORMED = new ArrayDeque<>();
    private static int localMode = UPRIGHT;
    private static boolean forcedLocal;
    private static boolean wasJumping;

    private SlideAndGlideMotionClient() {
    }

    public static void receive(int entityId, int kind) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        if (SlideAndGlideMotionPayload.isJump(kind)) {
            JUMPS.put(entityId, new Jump(kind, level.getGameTime()));
            if (kind == SlideAndGlideMotionPayload.JUMP_CEILING_DROP) {
                MODES.remove(entityId);
            }
        } else if (kind == UPRIGHT) {
            MODES.remove(entityId);
        } else {
            MODES.put(entityId, kind);
        }
    }

    public static int getMode(LivingEntity entity) {
        return MODES.getOrDefault(entity.getId(), UPRIGHT);
    }

    private static float jumpLength(int kind) {
        return switch (kind) {
            case SlideAndGlideMotionPayload.JUMP_WALL_KICK -> 14.0F;
            case SlideAndGlideMotionPayload.JUMP_CEILING_DROP -> 10.0F;
            default -> 12.0F;
        };
    }

    private static float jumpProgress(Jump jump, LivingEntity entity, float partial) {
        if (jump == null) {
            return -1.0F;
        }

        float age = entity.level().getGameTime() - jump.start() + partial;
        float length = jumpLength(jump.kind());
        return age < 0.0F || age > length ? -1.0F : age / length;
    }

    private static boolean isSliding(LivingEntity entity) {
        return (SlideAndGlideClient.getFlags(entity.getId()) & SlideAndGlideQuirk.FLAG_SLIDING) != 0;
    }

    private static boolean isOnWall(LivingEntity entity) {
        return !entity.onGround() && ExtraArmsQuirk.isTouchingWall(entity);
    }

    private static Vec3 forward(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private static Vec3 right(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            MODES.clear();
            JUMPS.clear();
            localMode = UPRIGHT;
            forcedLocal = false;
            wasJumping = false;
            return;
        }

        long now = mc.level.getGameTime();
        JUMPS.values().removeIf(jump -> now < jump.start() || now - jump.start() > jumpLength(jump.kind()));

        boolean jumping = player.input.jumping;
        boolean jumpPressed = jumping && !wasJumping;
        wasJumping = jumping;

        Optional<ManasSkillInstance> quirk = SlideAndGlideQuirk.getSlideAndGlide(player).filter(SlideAndGlideQuirk::isSliding);
        boolean sliding = quirk.isPresent();
        boolean climber = quirk.filter(SlideAndGlideQuirk::isSlideToggle).isPresent();
        if (!sliding || player.isPassenger() || player.getAbilities().flying || player.isInWater() || player.isSpectator()) {
            setLocalMode(player, UPRIGHT);
            return;
        }

        boolean shift = player.input.shiftKeyDown;
        double gap = ceilingGap(player);
        int mode;
        if (localMode == CEILING) {
            mode = shift || !climber || gap > CEILING_HOLD ? UPRIGHT : CEILING;
            if (mode == CEILING && jumpPressed) {
                setLocalMode(player, UPRIGHT);
                Vec3 look = forward(player.getYRot());
                player.setDeltaMovement(look.x * DROP_FORWARD, DROP_DOWN, look.z * DROP_FORWARD);
                startJump(player, SlideAndGlideMotionPayload.JUMP_CEILING_DROP);
                return;
            }
        } else if (climber && !player.onGround() && !shift && gap <= CEILING_GRAB) {
            mode = CEILING;
        } else if (shift && (player.onGround() || localMode == CRAWL)) {
            mode = CRAWL;
        } else {
            mode = UPRIGHT;
        }

        setLocalMode(player, mode);

        if (mode == CEILING) {
            ceilingMove(player, gap);
        } else if (mode == UPRIGHT && jumpPressed && !player.onGround() && SlideAndGlideQuirk.canWallSlide(player)) {
            wallKick(player);
        }
    }

    private static void setLocalMode(LocalPlayer player, int mode) {
        if (mode != UPRIGHT) {
            player.setForcedPose(Pose.SWIMMING);
            forcedLocal = true;
        } else if (forcedLocal) {
            forcedLocal = false;
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
        }

        if (mode == localMode) {
            return;
        }

        localMode = mode;
        if (mode == UPRIGHT) {
            MODES.remove(player.getId());
        } else {
            MODES.put(player.getId(), mode);
        }
        PacketDistributor.sendToServer(new SlideAndGlideMotionPayload(player.getId(), mode));
    }

    private static void startJump(LocalPlayer player, int kind) {
        JUMPS.put(player.getId(), new Jump(kind, player.level().getGameTime()));
        PacketDistributor.sendToServer(new SlideAndGlideMotionPayload(player.getId(), kind));
    }

    private static double ceilingGap(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        AABB above = new AABB(box.minX + 0.01D, box.maxY, box.minZ + 0.01D, box.maxX - 0.01D, box.maxY + CEILING_SCAN, box.maxZ - 0.01D);
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

    private static void ceilingMove(LocalPlayer player, double gap) {
        Vec3 fwd = forward(player.getYRot());
        Vec3 left = right(player.getYRot()).scale(-1.0D);
        Vec3 wish = fwd.scale(player.input.forwardImpulse).add(left.scale(player.input.leftImpulse));
        if (wish.lengthSqr() > 1.0D) {
            wish = wish.normalize();
        }

        Vec3 target = wish.scale(player.getAttributeValue(Attributes.MOVEMENT_SPEED) * WALK_PER_SPEED);
        Vec3 motion = player.getDeltaMovement();
        double up = Math.min(0.5D, gap + CEILING_PULL);
        player.setDeltaMovement(Mth.lerp(0.5D, motion.x, target.x), up, Mth.lerp(0.5D, motion.z, target.z));
        player.fallDistance = 0.0F;
    }

    private static void wallKick(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        Vec3 away = Vec3.ZERO;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 step = Vec3.atLowerCornerOf(direction.getNormal()).scale(0.1D);
            if (!player.level().noCollision(player, box.move(step))) {
                away = away.subtract(step.scale(10.0D));
            }
        }

        if (away.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 push = away.normalize().scale(KICK_AWAY).add(forward(player.getYRot()).scale(KICK_LOOK));
        player.setDeltaMovement(push.x, KICK_UP, push.z);
        startJump(player, SlideAndGlideMotionPayload.JUMP_WALL_KICK);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player
                || SlideAndGlideQuirk.getSlideAndGlide(player).filter(SlideAndGlideQuirk::isSliding).isEmpty()) {
            return;
        }

        double boost = 1.0D + SlideAndGlideQuirk.getSlideAndGlide(player).map(SlideAndGlideQuirk::getSlideSpeed).orElse(0.0D) * 0.5D;
        Vec3 look = forward(player.getYRot());
        Vec3 motion = player.getDeltaMovement();
        if (localMode == CRAWL) {
            Vec3 pounce = look.scale(POUNCE_FORWARD * boost);
            player.setDeltaMovement(pounce.x, POUNCE_UP, pounce.z);
            startJump(player, SlideAndGlideMotionPayload.JUMP_POUNCE);
            return;
        }

        Vec3 flat = new Vec3(motion.x, 0.0D, motion.z);
        Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : look;
        Vec3 leap = flat.add(dir.scale(FLIP_FORWARD * boost));
        player.setDeltaMovement(leap.x, motion.y * FLIP_UP, leap.z);
        startJump(player, SlideAndGlideMotionPayload.JUMP_FLIP);
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks) {
        if (ageInTicks == 0.0F || !(entity instanceof Player)) {
            return;
        }

        float partial = ageInTicks - entity.tickCount;
        Jump jump = JUMPS.get(entity.getId());
        float progress = jumpProgress(jump, entity, partial);
        if (!isSliding(entity) && progress < 0.0F) {
            return;
        }

        int mode = getMode(entity);
        if (mode == CRAWL || mode == CEILING) {
            poseCrawl(model, limbSwing, limbSwingAmount, ageInTicks, jump != null && jump.kind() == SlideAndGlideMotionPayload.JUMP_POUNCE ? progress : -1.0F);
        } else if (progress >= 0.0F) {
            poseAirborne(model, jump.kind(), progress);
        } else if (isOnWall(entity)) {
            poseWall(model, entity, ageInTicks);
        } else {
            poseGlide(model, entity, ageInTicks);
        }

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static void poseCrawl(PlayerModel<?> model, float limbSwing, float limbSwingAmount, float t, float pounce) {
        float stride = Math.min(1.0F, limbSwingAmount);
        float gait = Mth.sin(limbSwing * 0.5F) * stride;
        float breathe = Mth.sin(t * 0.15F) * 0.04F;
        model.head.xRot = -1.15F + breathe;
        model.head.yRot *= 0.5F;
        model.rightArm.xRot = -2.5F + gait * 0.25F;
        model.leftArm.xRot = -2.5F - gait * 0.25F;
        model.rightArm.yRot = 0.0F;
        model.leftArm.yRot = 0.0F;
        model.rightArm.zRot = -0.55F - breathe;
        model.leftArm.zRot = 0.55F + breathe;
        model.rightLeg.xRot = -0.7F - gait * 0.06F;
        model.leftLeg.xRot = -0.7F + gait * 0.06F;
        model.rightLeg.yRot = 0.0F;
        model.leftLeg.yRot = 0.0F;
        model.rightLeg.zRot = 0.45F;
        model.leftLeg.zRot = -0.45F;

        if (pounce >= 0.0F) {
            float stretch = Mth.sin(pounce * Mth.PI);
            model.rightArm.xRot = Mth.lerp(stretch, model.rightArm.xRot, -Mth.PI);
            model.leftArm.xRot = Mth.lerp(stretch, model.leftArm.xRot, -Mth.PI);
            model.rightArm.zRot = Mth.lerp(stretch, model.rightArm.zRot, -0.15F);
            model.leftArm.zRot = Mth.lerp(stretch, model.leftArm.zRot, 0.15F);
            model.rightLeg.xRot = Mth.lerp(stretch, model.rightLeg.xRot, 0.0F);
            model.leftLeg.xRot = Mth.lerp(stretch, model.leftLeg.xRot, 0.0F);
            model.rightLeg.zRot = Mth.lerp(stretch, model.rightLeg.zRot, 0.1F);
            model.leftLeg.zRot = Mth.lerp(stretch, model.leftLeg.zRot, -0.1F);
        }
    }

    private static void poseGlide(PlayerModel<?> model, LivingEntity entity, float t) {
        float k = glideAmount(entity);
        if (k <= 0.0F) {
            return;
        }

        float sway = Mth.sin(t * 0.25F) * 0.08F;
        model.head.xRot -= lean(entity);
        model.rightLeg.xRot = Mth.lerp(k, model.rightLeg.xRot, -0.5F);
        model.leftLeg.xRot = Mth.lerp(k, model.leftLeg.xRot, 0.4F);
        model.rightLeg.zRot = Mth.lerp(k, model.rightLeg.zRot, 0.08F);
        model.leftLeg.zRot = Mth.lerp(k, model.leftLeg.zRot, -0.12F);
        model.rightArm.xRot = Mth.lerp(k, model.rightArm.xRot, 0.9F + sway);
        model.leftArm.xRot = Mth.lerp(k, model.leftArm.xRot, 0.75F - sway);
        model.rightArm.zRot = Mth.lerp(k, model.rightArm.zRot, 0.35F + sway * 0.5F);
        model.leftArm.zRot = Mth.lerp(k, model.leftArm.zRot, -0.35F - sway * 0.5F);
        model.body.yRot = Mth.lerp(k, model.body.yRot, sway * 0.6F);
    }

    private static void poseWall(PlayerModel<?> model, LivingEntity entity, float t) {
        float climb = Mth.sin(t * 0.45F) * 0.5F * glideAmount(entity);
        model.head.xRot = -0.4F;
        model.rightArm.xRot = -2.7F + climb;
        model.leftArm.xRot = -2.7F - climb;
        model.rightArm.zRot = 0.2F;
        model.leftArm.zRot = -0.2F;
        model.rightLeg.xRot = -0.35F - climb * 0.6F;
        model.leftLeg.xRot = -0.35F + climb * 0.6F;
    }

    public static float glideAmount(LivingEntity entity) {
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        return VFXHelper.MathHelper.smoothstep(GLIDE_START, GLIDE_FULL, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static void poseAirborne(PlayerModel<?> model, int kind, float progress) {
        float tuck = Mth.sin(progress * Mth.PI);
        if (kind == SlideAndGlideMotionPayload.JUMP_WALL_KICK) {
            model.rightArm.xRot = -0.3F * tuck;
            model.leftArm.xRot = -0.3F * tuck;
            model.rightArm.zRot = 1.2F * tuck;
            model.leftArm.zRot = -1.2F * tuck;
            model.rightLeg.zRot = 0.3F * tuck;
            model.leftLeg.zRot = -0.3F * tuck;
            model.rightLeg.xRot = 0.2F * tuck;
            model.leftLeg.xRot = -0.4F * tuck;
            return;
        }

        model.rightArm.xRot = -0.6F * tuck;
        model.leftArm.xRot = -0.6F * tuck;
        model.rightArm.zRot = 0.2F * tuck;
        model.leftArm.zRot = -0.2F * tuck;
        model.rightLeg.xRot = -1.3F * tuck;
        model.leftLeg.xRot = -1.3F * tuck;
        model.head.xRot = 0.4F * tuck;
    }

    private static float lean(LivingEntity entity) {
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        return (float) Math.min(MAX_LEAN, Math.sqrt(dx * dx + dz * dz) * 1.4D);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (event.isCanceled()) {
            return;
        }

        float partial = event.getPartialTick();
        Jump jump = JUMPS.get(player.getId());
        float progress = jumpProgress(jump, player, partial);
        if (!isSliding(player) && progress < 0.0F) {
            return;
        }

        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        TRANSFORMED.push(player.getId());

        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 fwd = forward(yaw);
        Vec3 right = right(yaw);
        int mode = getMode(player);
        float ease = progress < 0.0F ? 0.0F : easeInOut(progress);

        if (mode == CRAWL || mode == CEILING) {
            if (mode == CEILING) {
                rotateAbout(poses, fwd, Mth.PI, CRAWL_CENTER);
            }
            if (jump != null && jump.kind() == SlideAndGlideMotionPayload.JUMP_POUNCE && progress >= 0.0F) {
                rotateAbout(poses, fwd, Mth.TWO_PI * ease, CRAWL_CENTER + CRAWL_RAISE);
            }
            poses.translate(0.0F, CRAWL_RAISE, 0.0F);
            return;
        }

        float center = player.getBbHeight() * 0.5F;
        if (progress >= 0.0F) {
            switch (jump.kind()) {
                case SlideAndGlideMotionPayload.JUMP_FLIP -> rotateAbout(poses, right, -Mth.TWO_PI * ease, center);
                case SlideAndGlideMotionPayload.JUMP_WALL_KICK -> rotateAbout(poses, right, Mth.TWO_PI * ease, center);
                case SlideAndGlideMotionPayload.JUMP_CEILING_DROP -> rotateAbout(poses, fwd, Mth.PI * (1.0F - ease), center);
                default -> {
                }
            }
            return;
        }

        if (!isOnWall(player)) {
            rotateAbout(poses, right, -lean(player), 0.0F);
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

    private static float easeInOut(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
