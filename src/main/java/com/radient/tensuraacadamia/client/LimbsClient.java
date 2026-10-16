package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.client.renderer.RuptureArmRenderer;
import com.radient.tensuraacadamia.util.Limbs;
import com.radient.tensuraacadamia.entity.RuptureArmEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// Missing limbs from Burst and Rupture
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class LimbsClient {

    private static final float HIDDEN = 0.03F;
    private static final float LEG_LENGTH = 12.0F;
    private static final double HOP = 0.3D;

    private LimbsClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RuptureArmEntity.TYPE.get(), RuptureArmRenderer::new);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Limbs.clearClientSizes();
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (ageInTicks == 0.0F || !(entity instanceof Player)) {
            return;
        }

        double time = entity.level().getGameTime() + (double) (ageInTicks - entity.tickCount);
        grow(model.leftArm, model.leftSleeve, Limbs.getLimb(entity, Limbs.LEFT_ARM, time));
        grow(model.rightArm, model.rightSleeve, Limbs.getLimb(entity, Limbs.RIGHT_ARM, time));
        grow(model.leftLeg, model.leftPants, Limbs.getLimb(entity, Limbs.LEFT_LEG, time));
        grow(model.rightLeg, model.rightPants, Limbs.getLimb(entity, Limbs.RIGHT_LEG, time));

        // when the player has no legs tey basically "stand" on their torso
        // Inspo taken from the nbc heroes mod
        // lowkey cool what they did
        float drop = LEG_LENGTH * (1.0F - Limbs.getLegs(entity, time));
        if (drop > 0.0F) {
            for (ModelPart part : new ModelPart[]{model.head, model.hat, model.body, model.jacket, model.leftArm, model.leftSleeve,
                    model.rightArm, model.rightSleeve, model.leftLeg, model.leftPants, model.rightLeg, model.rightPants}) {
                part.y += drop;
            }
        }
    }

    private static void grow(ModelPart limb, ModelPart layer, float grown) {
        if (grown >= 1.0F) {
            return;
        }

        if (grown <= HIDDEN) {
            limb.xScale = 0.0F;
            limb.yScale = 0.0F;
            limb.zScale = 0.0F;
            limb.visible = false;
        } else {
            limb.yScale *= grown;
        }

        boolean shown = layer.visible;
        layer.copyFrom(limb);
        layer.visible = shown && limb.visible;
    }

    private static boolean isMissing(Player player, int limb) {
        return Limbs.getLimbRendered(player, limb, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) < 1.0F;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && isMissing(player, Limbs.getArm(player, event.getHand() == InteractionHand.MAIN_HAND))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (isMissing(event.getPlayer(), event.getArm() == HumanoidArm.LEFT ? Limbs.LEFT_ARM : Limbs.RIGHT_ARM)) {
            event.setCanceled(true);
        }
    }

    // You can still jump and hop around lmao
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (!Limbs.isLegless(player)) {
            return;
        }

        player.setSprinting(false);
        if (!player.onGround()) {
            return;
        }

        Input input = event.getInput();
        float forward = input.forwardImpulse;
        float strafe = input.leftImpulse;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        if (!input.jumping || forward == 0.0F && strafe == 0.0F) {
            return;
        }

        double length = Math.sqrt(forward * forward + strafe * strafe);
        double sideways = strafe / length;
        double ahead = forward / length;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement((sideways * Mth.cos(yaw) - ahead * Mth.sin(yaw)) * HOP, motion.y, (ahead * Mth.cos(yaw) + sideways * Mth.sin(yaw)) * HOP);
    }

}
