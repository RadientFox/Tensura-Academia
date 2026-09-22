package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.StrongarmQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class StrongarmClient {

    private static boolean offHandNext = false;

    // Alternating punches
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isAttack() || player == null || minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        if (!StrongarmQuirk.canAlternatePunch(player)) {
            return;
        }

        event.setSwingHand(false);
        player.swing(offHandNext ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        offHandNext = !offHandNext;
    }

    // First person offhand punch
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getHand() != InteractionHand.OFF_HAND || event.getSwingProgress() <= 0.0F || player == null || player.isInvisible() || player.isScoping()) {
            return;
        }

        if (!StrongarmQuirk.canAlternatePunch(player)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        renderArm(event, player, player.getMainArm().getOpposite());
        poseStack.popPose();
    }

    // Same as the vanilla empty hand
    private static void renderArm(RenderHandEvent event, LocalPlayer player, HumanoidArm arm) {
        PoseStack poseStack = event.getPoseStack();
        float equipProgress = event.getEquipProgress();
        float swingProgress = event.getSwingProgress();

        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float swingRoot = Mth.sqrt(swingProgress);
        float swingX = -0.3F * Mth.sin(swingRoot * (float) Math.PI);
        float swingY = 0.4F * Mth.sin(swingRoot * (float) (Math.PI * 2));
        float swingZ = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(side * (swingX + 0.64000005F), swingY - 0.6F + equipProgress * -0.6F, swingZ - 0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));

        float swingRoll = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float swingYaw = Mth.sin(swingRoot * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * swingYaw * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * swingRoll * -20.0F));
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
    }

}
