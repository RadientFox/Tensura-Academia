package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ToolArmsQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

//Im genuinely tired of arm quirks

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class ToolArmsClient {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new ToolArmsLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ToolArmsQuirk.MENU, ToolArmsScreen::new);
    }

    private static class ToolArmsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private static final float[] SHOULDER_X = {1.5F, 1.5F, 2.0F, 2.0F};
        private static final float[] SHOULDER_Y = {1.0F, 1.0F, 5.0F, 5.0F};
        private static final float SHOULDER_Z = 2.2F;
        private static final float[] UPPER_SPREAD = {2.4F, 2.4F, 1.65F, 1.65F};
        private static final float[] FOREARM_SPREAD = {2.95F, 2.95F, 2.15F, 2.15F};
        private static final float UPPER_TILT = 0.3F;
        private static final float[] FOREARM_TILT = {-0.4F, -0.4F, -0.7F, -0.7F};
        private static final float ARM_WIDTH = 1.0F;
        private static final float UPPER_LENGTH = 1.0F;
        private static final float FOREARM_LENGTH = 0.95F;
        private static final float ELBOW = 10.0F;

        private final ItemInHandRenderer itemInHandRenderer;

        ToolArmsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ManasSkillInstance instance = ToolArmsQuirk.getToolArms(player).orElse(null);
            int arms = instance == null ? 0 : ToolArmsQuirk.getArms(instance);
            if (player.isInvisible() || arms <= 0) {
                return;
            }

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);
            ModelPart body = model.body;
            float swing = player.getAttackAnim(partialTick);
            double time = player.level().getGameTime() + (double) partialTick;

            for (int slot = 0; slot < arms; slot++) {
                boolean left = slot % 2 == 0;
                float side = left ? 1.0F : -1.0F;
                float center = MultiArmsClient.getArmCenter(left, context.slim()) * ARM_WIDTH;

                float dig = swing > 0.0F ? Mth.sin(Mth.clamp(swing * 1.3F - slot * 0.1F, 0.0F, 1.0F) * Mth.PI) : 0.0F;
                float sway = Mth.sin(ageInTicks * 0.08F + slot) * 0.04F;

                float parry = ToolArmsQuirk.getParrySwing(instance, slot, time);
                float lash = parry < 0.0F ? 0.0F : Mth.sin(parry * Mth.PI);

                Vector3f shoulder = MultiArmsClient.onBody(body, side * SHOULDER_X[slot] * body.xScale, SHOULDER_Y[slot], SHOULDER_Z);
                Quaternionf upperTurn = turn(body, UPPER_TILT + sway - lash * 0.8F, -side * UPPER_SPREAD[slot] * (1.0F - lash * 0.4F));
                Vector3f elbow = new Vector3f(center, ELBOW * UPPER_LENGTH, 0.0F);
                upperTurn.transform(elbow).add(shoulder);

                Quaternionf forearmTurn = turn(body, FOREARM_TILT[slot] - dig * 1.1F - lash * 1.6F + sway, -side * FOREARM_SPREAD[slot]);
                Vector3f forearm = new Vector3f(-center, 0.0F, 0.0F);
                forearmTurn.transform(forearm).add(elbow);

                MultiArmsClient.renderArm(context, left, pose(shoulder, upperTurn), ARM_WIDTH, UPPER_LENGTH, ItemStack.EMPTY);
                MultiArmsClient.renderArm(context, left, pose(forearm, forearmTurn), ARM_WIDTH, FOREARM_LENGTH, ToolArmsQuirk.getTool(player, slot));
            }
        }

        private static Quaternionf turn(ModelPart body, float tilt, float spread) {
            return new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot).mul(new Quaternionf().rotationZYX(spread, 0.0F, tilt));
        }

        private static PartPose pose(Vector3f pivot, Quaternionf turn) {
            Vector3f angles = turn.getEulerAnglesZYX(new Vector3f());
            return PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), angles.x(), angles.y(), angles.z());
        }

    }

    public static class ToolArmsScreen extends AbstractContainerScreen<ToolArmsQuirk.ToolArmsMenu> {

        private static final ResourceLocation HOPPER = ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");
        private static final int BACKGROUND = 0xFFC6C6C6;
        private static final int NOT_OUT = 0x60000000;

        public ToolArmsScreen(ToolArmsQuirk.ToolArmsMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.imageHeight = 133;
            this.inventoryLabelY = this.imageHeight - 94;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            this.renderTooltip(graphics, mouseX, mouseY);
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            int x = this.leftPos;
            int y = this.topPos;
            graphics.blit(HOPPER, x, y, 0, 0, this.imageWidth, this.imageHeight);
            graphics.fill(x + 43, y + 19, x + 133, y + 37, BACKGROUND);

            int arms = Minecraft.getInstance().player == null ? 0 : ToolArmsQuirk.getToolArms(Minecraft.getInstance().player).map(ToolArmsQuirk::getArms).orElse(0);
            for (int slot = 0; slot < ToolArmsQuirk.ARMS; slot++) {
                int slotX = x + ToolArmsQuirk.ToolArmsMenu.SLOT_X - 1 + slot * 18;
                int slotY = y + ToolArmsQuirk.ToolArmsMenu.SLOT_Y - 1;
                graphics.blit(HOPPER, slotX, slotY, 43, 19, 18, 18);
                if (slot >= arms) {
                    graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, NOT_OUT);
                }
            }
        }

    }

}
