package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.network.QuirkAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class QuirkAnimClient {
    private static final Map<Integer, Map<Integer, Long>> PLAYING = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> STATES = new ConcurrentHashMap<>();
    private static final Deque<Integer> TRANSFORMED = new ArrayDeque<>();

    private QuirkAnimClient() {
    }

    public static void receive(int entityId, int anim, boolean on) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        if (anim == QuirkAnimPayload.SPIDER_CRAWL || anim == QuirkAnimPayload.SPIDER_WALDOS) {
            if (on) {
                STATES.computeIfAbsent(entityId, id -> new HashSet<>()).add(anim);
            } else {
                Set<Integer> states = STATES.get(entityId);
                if (states != null) {
                    states.remove(anim);
                }
            }
            return;
        }

        PLAYING.computeIfAbsent(entityId, id -> new HashMap<>()).put(anim, level.getGameTime());
    }

    private static float length(int anim) {
        return switch (anim) {
            case QuirkAnimPayload.SPIDER_SHOT -> 8.0F;
            case QuirkAnimPayload.SPIDER_BLOSSOM -> 16.0F;
            case QuirkAnimPayload.SPIDER_FLIP -> 14.0F;
            case QuirkAnimPayload.WINGS_CANNON -> 12.0F;
            case QuirkAnimPayload.WINGS_FEATHER_BOOST -> 12.0F;
            default -> 10.0F;
        };
    }

    private static float progress(LivingEntity entity, int anim, float partial) {
        Map<Integer, Long> playing = PLAYING.get(entity.getId());
        Long start = playing == null ? null : playing.get(anim);
        if (start == null) {
            return -1.0F;
        }

        float age = entity.level().getGameTime() - start + partial;
        float length = length(anim);
        return age < 0.0F || age > length ? -1.0F : age / length;
    }

    private static boolean hasState(LivingEntity entity, int anim) {
        return hasState(entity.getId(), anim);
    }

    public static boolean hasState(int entityId, int anim) {
        Set<Integer> states = STATES.get(entityId);
        return states != null && states.contains(anim);
    }

    private static long startOf(LivingEntity entity, int anim) {
        Map<Integer, Long> playing = PLAYING.get(entity.getId());
        Long start = playing == null ? null : playing.get(anim);
        return start == null ? 0L : start;
    }

    private static boolean isWingFlying(LivingEntity entity) {
        return entity.isFallFlying() && !entity.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            PLAYING.clear();
            STATES.clear();
            return;
        }

        long now = level.getGameTime();
        PLAYING.values().forEach(anims -> anims.entrySet().removeIf(entry -> now - entry.getValue() > length(entry.getKey()) + 1L));
        PLAYING.values().removeIf(Map::isEmpty);
        STATES.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity));
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float envelope(float p, float in, float out) {
        return Math.min(ease(p / in), 1.0F - ease((p - (1.0F - out)) / out));
    }

    private static void aimArm(ModelPart arm, Vec3 direction, double yaw, float weight) {
        Vec3 dir = direction.normalize();
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        double f = dir.dot(fwd);
        double r = dir.dot(right);
        double u = dir.y;
        float x = (float) Math.asin(Mth.clamp(-f, -1.0D, 1.0D));
        float z = (float) Math.atan2(r, -u);
        arm.xRot = Mth.lerp(weight, arm.xRot, x);
        arm.yRot = Mth.lerp(weight, arm.yRot, 0.0F);
        arm.zRot = Mth.lerp(weight, arm.zRot, z);
    }

    private static void blend(ModelPart part, float weight, float x, float y, float z) {
        part.xRot = Mth.lerp(weight, part.xRot, x);
        part.yRot = Mth.lerp(weight, part.yRot, y);
        part.zRot = Mth.lerp(weight, part.zRot, z);
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks) {
        if (ageInTicks == 0.0F || !(entity instanceof Player)) {
            return;
        }

        float partial = ageInTicks - entity.tickCount;
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
        int ceiling = SpiderCeilingClient.getMode(entity);
        boolean posed = ceiling == SpiderCeilingClient.CRAWL ? poseCeilingCrawl(model, limbSwing, limbSwingAmount, ageInTicks)
                : ceiling == SpiderCeilingClient.HANG ? poseHang(model, entity, partial, yaw, ageInTicks)
                : poseSpider(model, entity, partial, yaw, ageInTicks);
        posed |= poseWings(model, entity, partial, ageInTicks);
        if (!posed) {
            return;
        }

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static boolean poseSpider(PlayerModel<?> model, LivingEntity entity, float partial, double yaw, float t) {
        boolean posed = false;
        Vec3 anchor = SpiderClient.getAnchor(entity.getId());
        if (anchor != null) {
            Vec3 shoulder = entity.getPosition(partial).add(0.0D, entity.getBbHeight() * 0.85D, 0.0D);
            aimArm(model.rightArm, anchor.subtract(shoulder), yaw, 1.0F);
            float stride = Mth.sin(t * 0.25F) * 0.35F;
            blend(model.leftArm, 1.0F, 0.4F, 0.0F, -0.9F);
            blend(model.rightLeg, 1.0F, -0.9F + stride, 0.0F, 0.1F);
            blend(model.leftLeg, 1.0F, 0.35F - stride, 0.0F, -0.1F);
            posed = true;
        } else if (hasState(entity, QuirkAnimPayload.SPIDER_CRAWL)) {
            float climb = Mth.sin(t * 0.4F) * 0.45F;
            blend(model.rightArm, 1.0F, -2.6F + climb, 0.0F, 0.55F);
            blend(model.leftArm, 1.0F, -2.6F - climb, 0.0F, -0.55F);
            blend(model.rightLeg, 1.0F, -0.7F - climb, 0.0F, 0.45F);
            blend(model.leftLeg, 1.0F, -0.7F + climb, 0.0F, -0.45F);
            model.head.xRot -= 0.4F;
            posed = true;
        }

        float blossom = progress(entity, QuirkAnimPayload.SPIDER_BLOSSOM, partial);
        if (blossom >= 0.0F) {
            float w = envelope(blossom, 0.15F, 0.25F);
            blend(model.rightArm, w, -0.3F, 0.0F, 2.0F);
            blend(model.leftArm, w, -0.3F, 0.0F, -2.0F);
            blend(model.rightLeg, w, 0.2F, 0.0F, 0.7F);
            blend(model.leftLeg, w, 0.2F, 0.0F, -0.7F);
            posed = true;
        }

        float flip = progress(entity, QuirkAnimPayload.SPIDER_FLIP, partial);
        if (flip >= 0.0F) {
            float w = envelope(flip, 0.15F, 0.3F);
            blend(model.rightArm, w, -0.7F, 0.0F, 0.35F);
            blend(model.leftArm, w, -0.7F, 0.0F, -0.35F);
            blend(model.rightLeg, w, -1.4F, 0.0F, 0.1F);
            blend(model.leftLeg, w, -1.2F, 0.0F, -0.1F);
            posed = true;
        }

        float shot = progress(entity, QuirkAnimPayload.SPIDER_SHOT, partial);
        if (shot >= 0.0F) {
            aimArm(model.rightArm, entity.getViewVector(partial), yaw, envelope(shot, 0.2F, 0.4F));
            posed = true;
        }

        float trip = progress(entity, QuirkAnimPayload.SPIDER_TRIP, partial);
        if (trip >= 0.0F) {
            float w = envelope(trip, 0.15F, 0.3F);
            float swing = ease(trip / 0.6F);
            blend(model.rightArm, w, Mth.lerp(swing, -2.9F, -1.1F), 0.0F, Mth.lerp(swing, 0.4F, -0.2F));
            blend(model.body, w, 0.0F, Mth.lerp(swing, 0.35F, -0.3F), 0.0F);
            posed = true;
        }
        return posed;
    }

    private static boolean poseCeilingCrawl(PlayerModel<?> model, float limbSwing, float limbSwingAmount, float t) {
        float stride = Math.min(1.0F, limbSwingAmount);
        float gait = Mth.sin(limbSwing * 0.6F) * stride;
        float breathe = Mth.sin(t * 0.15F) * 0.04F;
        model.head.xRot = -1.15F + breathe;
        model.head.yRot *= 0.5F;
        blend(model.rightArm, 1.0F, -2.45F + gait * 0.4F, 0.0F, -0.7F - breathe);
        blend(model.leftArm, 1.0F, -2.45F - gait * 0.4F, 0.0F, 0.7F + breathe);
        blend(model.rightLeg, 1.0F, -0.6F - gait * 0.2F, 0.0F, 0.6F);
        blend(model.leftLeg, 1.0F, -0.6F + gait * 0.2F, 0.0F, -0.6F);
        return true;
    }

    private static boolean poseHang(PlayerModel<?> model, LivingEntity entity, float partial, double yaw, float t) {
        float breathe = Mth.sin(t * 0.1F) * 0.04F;
        model.head.xRot = -model.head.xRot;
        model.head.yRot = -model.head.yRot;
        blend(model.body, 1.0F, 0.0F, 0.0F, 0.0F);
        blend(model.rightArm, 1.0F, -1.05F + breathe, -0.75F, 0.1F);
        blend(model.leftArm, 1.0F, -0.95F - breathe, 0.75F, -0.1F);
        blend(model.rightLeg, 1.0F, 0.0F, 0.0F, 0.04F);
        blend(model.leftLeg, 1.0F, -0.6F, 0.0F, 0.35F);

        float shot = progress(entity, QuirkAnimPayload.SPIDER_SHOT, partial);
        if (shot >= 0.0F) {
            Vec3 view = entity.getViewVector(partial);
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            aimArm(model.rightArm, fwd.scale(2.0D * view.dot(fwd)).subtract(view), yaw, envelope(shot, 0.2F, 0.4F));
        }
        return true;
    }

    private static boolean poseWings(PlayerModel<?> model, LivingEntity entity, float partial, float t) {
        boolean posed = false;
        if (isWingFlying(entity)) {
            float drift = Mth.sin(t * 0.15F) * 0.05F;
            blend(model.rightArm, 1.0F, 0.35F + drift, 0.0F, 0.25F);
            blend(model.leftArm, 1.0F, 0.35F - drift, 0.0F, -0.25F);
            blend(model.rightLeg, 1.0F, 0.05F, 0.0F, 0.04F);
            blend(model.leftLeg, 1.0F, 0.05F, 0.0F, -0.04F);
            posed = true;
        }

        float launch = progress(entity, QuirkAnimPayload.WINGS_LAUNCH, partial);
        if (launch >= 0.0F) {
            float w = envelope(launch, 0.2F, 0.3F);
            blend(model.rightArm, w, -2.9F, 0.0F, 0.25F);
            blend(model.leftArm, w, -2.9F, 0.0F, -0.25F);
            blend(model.rightLeg, w, 0.15F, 0.0F, 0.0F);
            blend(model.leftLeg, w, 0.15F, 0.0F, 0.0F);
            posed = true;
        }

        float cannon = progress(entity, QuirkAnimPayload.WINGS_CANNON, partial);
        if (cannon >= 0.0F) {
            float w = envelope(cannon, 0.12F, 0.25F);
            float pump = Mth.sin(cannon * Mth.PI * 5.0F) * 0.25F;
            blend(model.rightArm, w, -1.55F + pump, -0.15F, 0.0F);
            blend(model.leftArm, w, -1.55F - pump, 0.15F, 0.0F);
            posed = true;
        }

        float blade = progress(entity, QuirkAnimPayload.WINGS_BLADE, partial);
        if (blade >= 0.0F) {
            float w = envelope(blade, 0.15F, 0.25F);
            float slash = ease((blade - 0.45F) / 0.3F);
            blend(model.rightArm, w, Mth.lerp(slash, -2.7F, -1.0F), Mth.lerp(slash, 0.2F, -0.6F), Mth.lerp(slash, -0.4F, 0.1F));
            posed = true;
        }

        float boost = progress(entity, QuirkAnimPayload.WINGS_FEATHER_BOOST, partial);
        if (boost >= 0.0F) {
            float w = envelope(boost, 0.15F, 0.3F);
            blend(model.rightArm, w, -0.4F, 0.0F, 1.5F);
            blend(model.leftArm, w, -0.4F, 0.0F, -1.5F);
            model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35F);
            posed = true;
        }
        return posed;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled()) {
            return;
        }

        Player player = event.getEntity();
        float partial = event.getPartialTick();
        float blossom = progress(player, QuirkAnimPayload.SPIDER_BLOSSOM, partial);
        float roll = isWingFlying(player) ? progress(player, QuirkAnimPayload.WINGS_BOOST, partial) : -1.0F;
        float flip = progress(player, QuirkAnimPayload.SPIDER_FLIP, partial);
        Vec3 anchor = SpiderClient.getAnchor(player.getId());
        if (blossom < 0.0F && roll < 0.0F && flip < 0.0F && anchor == null) {
            return;
        }

        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        TRANSFORMED.push(player.getId());
        float center = player.getBbHeight() * 0.5F;
        Vec3 moved = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        if (anchor != null) {
            float chest = player.getBbHeight() * 0.85F;
            Vec3 toAnchor = anchor.subtract(player.getPosition(partial).add(0.0D, chest, 0.0D));
            if (toAnchor.lengthSqr() > 1.0E-4D) {
                Vec3 dir = toAnchor.normalize();
                Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D).cross(dir);
                if (axis.lengthSqr() > 1.0E-4D) {
                    float lean = (float) Math.acos(Mth.clamp(dir.y, -1.0D, 1.0D)) * 0.7F;
                    axis = axis.normalize();
                    poses.translate(0.0F, chest, 0.0F);
                    poses.mulPose(new Quaternionf().rotationAxis(lean, (float) axis.x, (float) axis.y, (float) axis.z));
                    poses.translate(0.0F, -chest, 0.0F);
                }
            }
        }
        if (flip >= 0.0F) {
            Vec3 fwd = moved.horizontalDistanceSqr() < 1.0E-4D ? player.getLookAngle() : new Vec3(moved.x, 0.0D, moved.z).normalize();
            Vec3 axis = switch ((int) (startOf(player, QuirkAnimPayload.SPIDER_FLIP) % 3L)) {
                case 0 -> new Vec3(0.0D, 1.0D, 0.0D).cross(fwd).normalize();
                case 1 -> fwd;
                default -> new Vec3(0.0D, 1.0D, 0.0D);
            };
            poses.translate(0.0F, center, 0.0F);
            poses.mulPose(new Quaternionf().rotationAxis(Mth.TWO_PI * ease(flip), (float) axis.x, (float) axis.y, (float) axis.z));
            poses.translate(0.0F, -center, 0.0F);
        }
        if (blossom >= 0.0F) {
            poses.translate(0.0F, center, 0.0F);
            poses.mulPose(new Quaternionf().rotationY(-Mth.TWO_PI * ease(blossom)));
            poses.translate(0.0F, -center, 0.0F);
        }
        if (roll >= 0.0F) {
            Vec3 axis = moved.lengthSqr() < 1.0E-6D ? player.getLookAngle() : moved.normalize();
            poses.translate(0.0F, center, 0.0F);
            poses.mulPose(new Quaternionf().rotationAxis(Mth.TWO_PI * ease(roll), (float) axis.x, (float) axis.y, (float) axis.z));
            poses.translate(0.0F, -center, 0.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (!TRANSFORMED.isEmpty() && TRANSFORMED.peek().equals(event.getEntity().getId())) {
            TRANSFORMED.pop();
            event.getPoseStack().popPose();
        }
    }
}
