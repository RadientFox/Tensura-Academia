package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsOffhands;
import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
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

        // More Hands slot frames in the survival and creative inventory
        @SubscribeEvent
        public static void onInventoryBackground(ContainerScreenEvent.Render.Background event) {
            AbstractContainerScreen<?> screen = event.getContainerScreen();
            if (screen instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()) {
                return;
            }

            for (Slot slot : screen.getMenu().slots) {
                if (slot.container instanceof QuadArmsOffhands.OffhandContainer && slot.isActive()) {
                    drawSlotFrame(event.getGuiGraphics(), screen.getGuiLeft() + slot.x - 1, screen.getGuiTop() + slot.y - 1);
                }
            }
        }

        private static void drawSlotFrame(GuiGraphics graphics, int x, int y) {
            graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
            graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
            graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
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

        private static final float HOLD_RAISE = -1.35F;
        private static final float HOLD_INWARD = 0.15F;

        private static final float IMPACT_SWING_TICKS = 6.0F;

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

            // Vanilla arm poses
            float attackTime = model.attackTime;
            HumanoidModel.ArmPose rightArmPose = model.rightArmPose;
            HumanoidModel.ArmPose leftArmPose = model.leftArmPose;
            model.attackTime = 0.0F;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            PartPose restRight = model.rightArm.storePose();
            PartPose restLeft = model.leftArm.storePose();

            model.attackTime = attackTime;
            model.rightArmPose = rightArmPose;
            model.leftArmPose = leftArmPose;
            model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            boolean holding = QuadArmsQuirk.isHolding(quadArms.get());
            ItemStack lowerRight = holding ? ItemStack.EMPTY : QuadArmsQuirk.getExtraOffhand(player, LOWER_RIGHT_SLOT);
            ItemStack lowerLeft = holding ? ItemStack.EMPTY : QuadArmsQuirk.getExtraOffhand(player, LOWER_LEFT_SLOT);
            boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
            RenderType renderType = RenderType.entityTranslucent(player.getSkin().texture());
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

            long swingTime = QuadArmsQuirk.getImpactSwingTime(quadArms.get());
            float impactSwing = swingTime == Long.MIN_VALUE ? -1.0F : (player.level().getGameTime() - swingTime + partialTick) / IMPACT_SWING_TICKS;

            renderLowerArm(model, player, model.rightArm, model.rightSleeve, restRight, -1.0F, holding, -1.0F, lowerRight, slim, poseStack, buffer, renderType, packedLight, overlay);
            renderLowerArm(model, player, model.leftArm, model.leftSleeve, restLeft, 1.0F, holding, impactSwing, lowerLeft, slim, poseStack, buffer, renderType, packedLight, overlay);
        }

        private void renderLowerArm(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, PartPose rest, float side, boolean holding, float swing, ItemStack stack, boolean slim, PoseStack poseStack, MultiBufferSource buffer, RenderType renderType, int light, int overlay) {
            PartPose armPose = arm.storePose();
            PartPose sleevePose = sleeve.storePose();
            float armXScale = arm.xScale;
            float armZScale = arm.zScale;
            float sleeveXScale = sleeve.xScale;
            float sleeveZScale = sleeve.zScale;

            // Attach to the torso so crouching, swimming, and twisting carry over
            ModelPart body = model.body;
            Vector3f offset = new Vector3f(side * (SHOULDER_OFFSET * body.xScale + ARM_SPREAD), SHOULDER_HEIGHT + ARM_DROP, ARM_BACK);
            new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot).transform(offset);
            arm.x = body.x + offset.x();
            arm.y = body.y + offset.y();
            arm.z = body.z + offset.z();

            if (holding) {
                arm.xRot = HOLD_RAISE;
                arm.yRot = body.yRot;
                arm.zRot = side * HOLD_INWARD;
            } else {
                arm.xRot = stack.isEmpty() ? rest.xRot : rest.xRot * 0.5F - (float) Math.PI / 10.0F;
                arm.yRot = rest.yRot + body.yRot;
                arm.zRot = rest.zRot - side * ARM_TILT;
            }

            // Same motion as vanilla
            if (swing >= 0.0F && swing < 1.0F) {
                float eased = 1.0F - (1.0F - swing) * (1.0F - swing) * (1.0F - swing) * (1.0F - swing);
                float lift = Mth.sin(swing * (float) Math.PI) * -(model.head.xRot - 0.7F) * 0.75F;
                arm.xRot -= Mth.sin(eased * (float) Math.PI) * 1.2F + lift;
                arm.zRot += Mth.sin(swing * (float) Math.PI) * -0.4F;
            }

            arm.xScale = ARM_SCALE;
            arm.zScale = ARM_SCALE;
            sleeve.copyFrom(arm);

            VertexConsumer consumer = buffer.getBuffer(renderType);
            arm.render(poseStack, consumer, light, overlay);
            sleeve.render(poseStack, consumer, light, overlay);

            if (!stack.isEmpty()) {
                renderItem(player, arm, stack, side > 0.0F, slim, poseStack, buffer, light);
            }

            arm.loadPose(armPose);
            sleeve.loadPose(sleevePose);
            arm.xScale = armXScale;
            arm.zScale = armZScale;
            sleeve.xScale = sleeveXScale;
            sleeve.zScale = sleeveZScale;
        }

        // Same hand transform as vanilla
        private void renderItem(AbstractClientPlayer player, ModelPart arm, ItemStack stack, boolean left, boolean slim, PoseStack poseStack, MultiBufferSource buffer, int light) {
            poseStack.pushPose();

            float slimShift = slim ? (left ? -0.5F : 0.5F) : 0.0F;
            float xScale = arm.xScale;
            float zScale = arm.zScale;
            arm.x += slimShift;
            arm.xScale = 1.0F;
            arm.zScale = 1.0F;
            arm.translateAndRotate(poseStack);
            arm.x -= slimShift;
            arm.xScale = xScale;
            arm.zScale = zScale;

            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            this.itemInHandRenderer.renderItem(player, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, poseStack, buffer, light);

            poseStack.popPose();
        }

    }

}
