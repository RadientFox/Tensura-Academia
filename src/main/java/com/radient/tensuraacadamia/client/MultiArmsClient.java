package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.MultiArms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// IM GETTING SICK AND TIRED OF THESE ARMS
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class MultiArmsClient {

    public static final float HAND_LENGTH = 10.0F;
    private static final float SWING_TICKS = 6.0F;

    public record ArmContext(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, ItemInHandRenderer items, PoseStack poseStack, MultiBufferSource buffer, RenderType renderType, int light, int overlay, boolean slim) {
        public static ArmContext of(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, ItemInHandRenderer items, PoseStack poseStack, MultiBufferSource buffer, int light) {
            return new ArmContext(model, player, items, poseStack, buffer, RenderType.entityTranslucent(player.getSkin().texture()), light,
                    LivingEntityRenderer.getOverlayCoords(player, 0.0F), player.getSkin().model() == PlayerSkin.Model.SLIM);
        }
    }

    // Held entities
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || MultiArms.getClientHolds().isEmpty()) {
            return;
        }

        MultiArms.getClientHolds().forEach((targetId, hold) -> {
            if (!(level.getEntity(targetId) instanceof LivingEntity target) || !(level.getEntity(hold.holderId()) instanceof LivingEntity holder)) {
                return;
            }

            Vec3 now = MultiArms.getHoldPos(holder, hold.offset(), hold.frame(), 1.0F);
            Vec3 before = MultiArms.getHoldPos(holder, hold.offset(), hold.frame(), 0.0F);
            target.setPos(now);
            target.xo = before.x;
            target.yo = before.y;
            target.zo = before.z;
            target.xOld = before.x;
            target.yOld = before.y;
            target.zOld = before.z;
            target.setDeltaMovement(Vec3.ZERO);
            if (hold.frame() == MultiArms.HOLD_BODY) {
                target.yBodyRotO = holder.yBodyRotO;
                target.setYBodyRot(holder.yBodyRot);
            }
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MultiArms.getClientHolds().clear();
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks, float netHeadYaw, float headPitch) {
        if (ageInTicks == 0.0F || !(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (DupliArmsClient.poseMainArm(model, player, ageInTicks, netHeadYaw, headPitch)) {
            model.rightSleeve.copyFrom(model.rightArm);
        }
    }

    // Extra offhand slot in the survival and creative inventory
    @SubscribeEvent
    public static void onInventoryBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (screen instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen()) {
            return;
        }

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof MultiArms.OffhandContainer && slot.isActive()) {
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

    // Vanilla arm poses
    public static PartPose[] getRestPoses(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float attackTime = model.attackTime;
        HumanoidModel.ArmPose rightArmPose = model.rightArmPose;
        HumanoidModel.ArmPose leftArmPose = model.leftArmPose;
        model.attackTime = 0.0F;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        PartPose[] rest = {model.rightArm.storePose(), model.leftArm.storePose()};

        model.attackTime = attackTime;
        model.rightArmPose = rightArmPose;
        model.leftArmPose = leftArmPose;
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        return rest;
    }

    public static Vector3f onBody(ModelPart body, float x, float y, float z) {
        Vector3f offset = new Vector3f(x, y, z);
        new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot).transform(offset);
        return offset.add(body.x, body.y, body.z);
    }

    public static float getSwing(AbstractClientPlayer player, long swingTime, float partialTick) {
        return swingTime == Long.MIN_VALUE ? -1.0F : (player.level().getGameTime() - swingTime + partialTick) / SWING_TICKS;
    }

    public static float getSwingXRot(PlayerModel<?> model, float swing) {
        if (swing < 0.0F || swing >= 1.0F) {
            return 0.0F;
        }

        float eased = 1.0F - (1.0F - swing) * (1.0F - swing) * (1.0F - swing) * (1.0F - swing);
        float lift = Mth.sin(swing * (float) Math.PI) * -(model.head.xRot - 0.7F) * 0.75F;
        return -(Mth.sin(eased * (float) Math.PI) * 1.2F + lift);
    }

    public static float getSwingZRot(float swing) {
        return swing < 0.0F || swing >= 1.0F ? 0.0F : Mth.sin(swing * (float) Math.PI) * -0.4F;
    }

    // Where the middle of the arm is
    public static float getArmCenter(boolean left, boolean slim) {
        return (left ? 1.0F : -1.0F) * (slim ? 0.5F : 1.0F);
    }

    public static void renderArm(ArmContext context, boolean left, PartPose pose, float width, float length, ItemStack stack) {
        ModelPart arm = left ? context.model().leftArm : context.model().rightArm;
        ModelPart sleeve = left ? context.model().leftSleeve : context.model().rightSleeve;
        PartPose armPose = arm.storePose();
        PartPose sleevePose = sleeve.storePose();
        float[] armScale = {arm.xScale, arm.yScale, arm.zScale};
        float[] sleeveScale = {sleeve.xScale, sleeve.yScale, sleeve.zScale};

        arm.loadPose(pose);
        arm.xScale = width;
        arm.yScale = length;
        arm.zScale = width;
        sleeve.copyFrom(arm);

        VertexConsumer consumer = context.buffer().getBuffer(context.renderType());
        arm.render(context.poseStack(), consumer, context.light(), context.overlay());
        sleeve.render(context.poseStack(), consumer, context.light(), context.overlay());

        if (!stack.isEmpty()) {
            renderItem(context, arm, stack, left);
        }

        arm.loadPose(armPose);
        sleeve.loadPose(sleevePose);
        arm.xScale = armScale[0];
        arm.yScale = armScale[1];
        arm.zScale = armScale[2];
        sleeve.xScale = sleeveScale[0];
        sleeve.yScale = sleeveScale[1];
        sleeve.zScale = sleeveScale[2];
    }

    public static void transform(PoseStack poseStack, PartPose pose, float width, float length) {
        poseStack.translate(pose.x / 16.0F, pose.y / 16.0F, pose.z / 16.0F);
        poseStack.mulPose(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
        poseStack.scale(width, length, width);
    }

    // Moves the pose stack to the end of an arm
    public static void toHand(PoseStack poseStack, PartPose pose, float length) {
        transform(poseStack, pose, 1.0F, 1.0F);
        poseStack.translate(0.0F, HAND_LENGTH * length / 16.0F, 0.0F);
    }

    // Same hand transform as vanilla
    private static void renderItem(ArmContext context, ModelPart arm, ItemStack stack, boolean left) {
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();

        float slimShift = context.slim() ? (left ? -0.5F : 0.5F) : 0.0F;
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
        context.items().renderItem(context.player(), stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, poseStack, context.buffer(), context.light());

        poseStack.popPose();
    }

}
