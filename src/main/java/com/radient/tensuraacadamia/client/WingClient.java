package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.WeldQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.WingQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSelf;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class WingClient {

    private static final double ACCELERATION = 0.25D;
    private static final double POSE_START_SPEED = 0.08D;
    private static final double POSE_FULL_SPEED = 0.35D;
    private static final float POSE_EASE = 0.25F;
    private static final double TURN_EASE = 0.35D;
    private static final float GROW_PER_TICK = 0.12F;
    private static final float BODY_MIDDLE = 0.9F;
    private static final float FLAT_MIDDLE = 0.3F;
    private static final double MIN_TURN = 0.1D;
    private static final float SPIN_SPEED = -75.0F;
    private static final float KEEP_SPRINTING = 1.0E-4F;

    private static final class Flight {
        private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
        private float blend;
        private float blendO;
        private float yaw;
        private float yawO;
        private float pitch;
        private float pitchO;
        private float growth;
        private float growthO;
    }

    private static final Map<Integer, Flight> FLIGHTS = new HashMap<>();
    private static Vec3 velocity = Vec3.ZERO;

    private WingClient() {
    }

    private static @Nullable CompoundTag getWingTag(LivingEntity entity) {
        ManasSkillInstance wing = WingQuirk.getWing(entity).filter(ManasSkillInstance::isToggled).orElse(null);
        return wing == null ? null : wing.getTag();
    }

    private static boolean isFlying(Player player, CompoundTag tag) {
        return player.isLocalPlayer() ? player.getAbilities().flying : tag.getBoolean(WingQuirk.FLYING_TAG);
    }

    private static @Nullable CompoundTag getActiveTag(LivingEntity entity) {
        ManasSkillInstance wing = WingQuirk.getWing(entity).orElse(null);
        return wing == null ? null : wing.getTag();
    }

    private static boolean isBoosting(Player player, @Nullable CompoundTag tag) {
        return tag != null && tag.getBoolean(WingQuirk.BOOSTING_TAG) && isFlying(player, tag);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        CompoundTag tag = getWingTag(player);
        boolean wing = isBoosting(player, tag);
        boolean rule = !wing && NewOrderSelf.isFlyBoosting(player);
        if (!wing && !rule || player.isPassenger() || WeldQuirk.isClientRooted()) {
            velocity = player.getDeltaMovement();
            return;
        }

        Input input = event.getInput();
        double speed = (wing ? tag.getDouble(WingQuirk.BOOST_SPEED_TAG) : NewOrderSelf.getFlySpeed(player)) / 20.0D;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        int vertical = (input.jumping ? 1 : 0) - (input.shiftKeyDown ? 1 : 0);
        Vec3 wish = player.getLookAngle().scale(input.forwardImpulse).add(left.scale(input.leftImpulse)).add(0.0D, vertical, 0.0D);
        if (wish.lengthSqr() > 1.0D) {
            wish = wish.normalize();
        }

        velocity = velocity.lerp(wish.scale(speed), ACCELERATION);
        double vanilla = vertical * player.getAbilities().getFlyingSpeed() * 3.0D;
        player.setDeltaMovement(velocity.x, velocity.y - vanilla, velocity.z);
        input.forwardImpulse = rule && input.forwardImpulse > 0.0F ? KEEP_SPRINTING : 0.0F;
        input.leftImpulse = 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            FLIGHTS.clear();
            return;
        }

        for (AbstractClientPlayer player : level.players()) {
            CompoundTag tag = getWingTag(player);
            CompoundTag active = getActiveTag(player);
            boolean diving = active != null && active.getBoolean(WingQuirk.DIVING_TAG);
            boolean airborne = isAirborne(player);
            boolean boosting = (isBoosting(player, tag) || NewOrderSelf.isFlyBoosting(player)) && !player.isPassenger();
            boolean wingsOut = !player.isPassenger() && (tag != null && isFlying(player, tag) || diving || airborne);
            WingQuirk.setFlat(player, boosting);
            Flight flight = FLIGHTS.get(player.getId());
            if (flight == null) {
                if (!wingsOut && !boosting) {
                    continue;
                }

                flight = new Flight();
                flight.yaw = flight.yawO = player.yBodyRot;
                FLIGHTS.put(player.getId(), flight);
            }

            flight.growthO = flight.growth;
            flight.growth = Mth.clamp(flight.growth + (wingsOut ? GROW_PER_TICK : -GROW_PER_TICK), 0.0F, 1.0F);

            Vec3 moved = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
            double speed = moved.length();
            float target = boosting ? 1.0F : diving && !player.isPassenger() ? (float) Mth.clamp((speed - POSE_START_SPEED) / (POSE_FULL_SPEED - POSE_START_SPEED), 0.0D, 1.0D) : 0.0F;
            flight.blendO = flight.blend;
            flight.blend += (target - flight.blend) * POSE_EASE;
            if (speed > 0.02D) {
                flight.direction = flight.direction.lerp(moved.normalize(), TURN_EASE).normalize();
            }

            flight.yawO = flight.yaw;
            flight.pitchO = flight.pitch;
            Vec3 direction = flight.direction;
            if (direction.horizontalDistance() > MIN_TURN) {
                flight.yaw = flight.yawO + Mth.wrapDegrees((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F - flight.yawO);
            }
            flight.pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);

            if (!wingsOut && flight.growth <= 0.0F && flight.blend < 0.01F) {
                FLIGHTS.remove(player.getId());
            }
        }

        FLIGHTS.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
    }

    public static float getWingGrowth(LivingEntity entity, float partialTick) {
        Flight flight = FLIGHTS.get(entity.getId());
        return flight == null ? 0.0F : Mth.lerp(partialTick, flight.growthO, flight.growth);
    }

    private static float getBlend(Flight flight, float partialTick) {
        return Mth.lerp(partialTick, flight.blendO, flight.blend);
    }

    private static boolean isAirborne(LivingEntity entity) {
        if (entity instanceof Player player && player.isLocalPlayer()) {
            return WingQuirk.isClientRising();
        }

        CompoundTag active = getActiveTag(entity);
        return active != null && active.getBoolean(WingQuirk.AIRBORNE_TAG);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity().isLocalPlayer()) {
            WingQuirk.tickClientRise(event.getEntity());
        }
    }

    public static void rotateBody(AbstractClientPlayer player, PoseStack poseStack, float bodyYaw, float partialTick) {
        if (isAirborne(player) && !player.isPassenger()) {
            poseStack.mulPose(Axis.YP.rotationDegrees((player.tickCount + partialTick) * SPIN_SPEED));
            return;
        }

        Flight flight = FLIGHTS.get(player.getId());
        float blend = flight == null ? 0.0F : getBlend(flight, partialTick);
        if (flight == null || blend <= 0.0F) {
            return;
        }

        float yaw = Mth.rotLerp(partialTick, flight.yawO, flight.yaw);
        float pitch = Mth.lerp(partialTick, flight.pitchO, flight.pitch);
        float middle = WingQuirk.isFlat(player) ? FLAT_MIDDLE : BODY_MIDDLE;
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - yaw) * blend));
        poseStack.translate(0.0F, Mth.lerp(blend, BODY_MIDDLE, middle), 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees((-90.0F - pitch) * blend));
        poseStack.translate(0.0F, -BODY_MIDDLE, 0.0F);
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity) {
        Flight flight = FLIGHTS.get(entity.getId());
        float blend = isAirborne(entity) ? 1.0F : flight == null ? 0.0F : getBlend(flight, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (blend <= 0.01F) {
            return;
        }

        model.head.xRot = Mth.lerp(blend, model.head.xRot, -Mth.PI / 3.0F);
        model.rightArm.xRot = Mth.lerp(blend, model.rightArm.xRot, 0.0F);
        model.rightArm.yRot = Mth.lerp(blend, model.rightArm.yRot, 0.0F);
        model.rightArm.zRot = Mth.lerp(blend, model.rightArm.zRot, 0.1F);
        model.leftArm.xRot = Mth.lerp(blend, model.leftArm.xRot, 0.0F);
        model.leftArm.yRot = Mth.lerp(blend, model.leftArm.yRot, 0.0F);
        model.leftArm.zRot = Mth.lerp(blend, model.leftArm.zRot, -0.1F);
        model.rightLeg.xRot = Mth.lerp(blend, model.rightLeg.xRot, 0.05F);
        model.leftLeg.xRot = Mth.lerp(blend, model.leftLeg.xRot, 0.2F);
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WingQuirk.clearClient();
        FLIGHTS.clear();
        velocity = Vec3.ZERO;
    }

}
