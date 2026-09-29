package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.MultiArms;
import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class QuadArmsClient {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new QuadArmsLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    private static Optional<ManasSkillInstance> getQuadArms(AbstractClientPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.QUAD_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
    public static class Input {
        @SubscribeEvent
        public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !event.isUseItem() || event.getHand() != InteractionHand.OFF_HAND || !QuadArmsQuirk.canUseQuadShield(player)) {
                return;
            }

            PacketDistributor.sendToServer(new QuadArmsQuirk.QuadShieldPayload());
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static class QuadArmsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private static final float SHOULDER_OFFSET = 5.0F;
        private static final float SHOULDER_HEIGHT = 2.0F;
        private static final float ARM_SPREAD = 0.5F;
        private static final float ARM_DROP = 5.0F;
        private static final float ARM_BACK = 1.0F;
        private static final float ARM_TILT = 0.3F;
        private static final float ARM_SCALE = 0.9F;

        private static final double HOLD_SHOULDER_DROP = 0.62D;
        private static final double HOLD_TARGET_RADIUS = 0.3D;

        // Extra offhand slots
        private static final int LOWER_LEFT_SLOT = 0;
        private static final int LOWER_RIGHT_SLOT = 1;

        private final ItemInHandRenderer itemInHandRenderer;

        QuadArmsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }

            Optional<ManasSkillInstance> quadArms = getQuadArms(player);
            if (quadArms.isEmpty()) {
                return;
            }

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            PartPose[] rest = MultiArmsClient.getRestPoses(model, player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            boolean holding = QuadArmsQuirk.isHolding(quadArms.get());
            ItemStack lowerRight = holding ? ItemStack.EMPTY : MultiArms.getExtraOffhand(player, LOWER_RIGHT_SLOT);
            ItemStack lowerLeft = holding ? ItemStack.EMPTY : MultiArms.getExtraOffhand(player, LOWER_LEFT_SLOT);
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);

            float impactSwing = MultiArmsClient.getSwing(player, QuadArmsQuirk.getImpactSwingTime(quadArms.get(), false), partialTick);
            float impactSwingRight = MultiArmsClient.getSwing(player, QuadArmsQuirk.getImpactSwingTime(quadArms.get(), true), partialTick);

            float holdPitch = holding ? getHoldPitch(player, headPitch) : 0.0F;
            float holdYaw = netHeadYaw * Mth.DEG_TO_RAD;

            renderLowerArm(context, rest[0], -1.0F, holding, holdPitch, holdYaw, impactSwingRight, lowerRight);
            renderLowerArm(context, rest[1], 1.0F, holding, holdPitch, holdYaw, impactSwing, lowerLeft);
        }

        private static float getHoldPitch(AbstractClientPlayer player, float headPitch) {
            float pitch = headPitch * Mth.DEG_TO_RAD;
            double reach = QuadArmsQuirk.getHoldDistance() + HOLD_TARGET_RADIUS;
            double rise = HOLD_SHOULDER_DROP * player.getScale() - Mth.sin(pitch) * reach;
            double forward = Mth.cos(pitch) * reach + ARM_BACK / 16.0D * player.getScale();
            return (float) Math.atan2(rise, forward);
        }

        private static float getHoldSpread(AbstractClientPlayer player) {
            double reach = QuadArmsQuirk.getHoldDistance() + HOLD_TARGET_RADIUS;
            return (float) Math.atan2((SHOULDER_OFFSET + ARM_SPREAD) / 16.0D * player.getScale(), reach);
        }

        private static void renderLowerArm(MultiArmsClient.ArmContext context, PartPose rest, float side, boolean holding, float holdPitch, float holdYaw, float swing, ItemStack stack) {
            ModelPart body = context.model().body;
            Vector3f shoulder = MultiArmsClient.onBody(body, side * (SHOULDER_OFFSET * body.xScale + ARM_SPREAD), SHOULDER_HEIGHT + ARM_DROP, ARM_BACK);

            float xRot;
            float yRot;
            float zRot;
            if (holding) {
                xRot = -Mth.HALF_PI - holdPitch;
                yRot = holdYaw + side * getHoldSpread(context.player());
                zRot = 0.0F;
            } else {
                xRot = stack.isEmpty() ? rest.xRot : rest.xRot * 0.5F - (float) Math.PI / 10.0F;
                yRot = rest.yRot + body.yRot;
                zRot = rest.zRot - side * ARM_TILT;
            }

            xRot += MultiArmsClient.getSwingXRot(context.model(), swing);
            zRot += MultiArmsClient.getSwingZRot(swing);
            MultiArmsClient.renderArm(context, side > 0.0F, PartPose.offsetAndRotation(shoulder.x(), shoulder.y(), shoulder.z(), xRot, yRot, zRot), ARM_SCALE, 1.0F, stack);
        }

    }

}
