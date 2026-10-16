package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class EngineKickClient {
    private static final float KICK_ANGLE = 1.65F;

    private EngineKickClient() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getSwingProgress() <= 0.0F || !hasEngine(player)) return;

        event.setCanceled(true);
        InteractionHand kickingHand = player.getMainArm() == HumanoidArm.RIGHT
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (event.getHand() != kickingHand) return;

        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(player);
        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) renderer.getModel();
        float kick = Mth.sin(event.getSwingProgress() * Mth.PI);
        HumanoidArm side = player.getMainArm();
        var leg = side == HumanoidArm.RIGHT ? model.rightLeg : model.leftLeg;
        var pants = side == HumanoidArm.RIGHT ? model.rightPants : model.leftPants;
        var originalLeg = leg.storePose();
        var originalPants = pants.storePose();

        // The first-person renderer reuses a model that may still carry the
        // walking pose; set the kick pose explicitly instead of adding to it.
        leg.xRot = -KICK_ANGLE * 0.72F * kick;
        leg.yRot = (side == HumanoidArm.RIGHT ? -0.12F : 0.12F) * kick;
        leg.zRot = (side == HumanoidArm.RIGHT ? -0.04F : 0.04F) * kick;
        pants.copyFrom(leg);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate((side == HumanoidArm.RIGHT ? 1.0F : -1.0F) * 0.05F, -1.45F, -0.55F);
        poseStack.mulPose(Axis.YP.rotationDegrees((side == HumanoidArm.RIGHT ? 1.0F : -1.0F) * 8.0F));
        poseStack.scale(1.2F, 1.2F, 1.2F);
        RenderType renderType = RenderType.entityTranslucent(player.getSkin().texture());
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(renderType);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        leg.render(poseStack, consumer, event.getPackedLight(), overlay);
        pants.render(poseStack, consumer, event.getPackedLight(), overlay);
        poseStack.popPose();

        leg.loadPose(originalLeg);
        pants.loadPose(originalPants);
    }

    private static boolean hasEngine(AbstractClientPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.ENGINE.get()).isPresent();
    }
}
