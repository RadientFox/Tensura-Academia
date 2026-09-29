package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.BodyMorphQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;

import java.util.Optional;

// Body Morph's two big arms
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class BodyMorphClient {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackArmsLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    private static class BackArmsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private static final float SHOULDER_X = 2.0F;
        private static final float SHOULDER_Y = 3.0F;
        private static final float SHOULDER_Z = 3.0F;
        private static final float ARM_WIDTH = 1.2F;
        private static final float ARM_LENGTH = 1.3F;
        private static final float SLAM_LENGTH = 2.3F;

        private final ItemInHandRenderer itemInHandRenderer;

        BackArmsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }

            Optional<ManasSkillInstance> bodyMorph = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.BODY_MORPH.get()).filter(instance -> instance.getMastery() >= 0.0D);
            if (bodyMorph.isEmpty()) {
                return;
            }

            ManasSkillInstance instance = bodyMorph.get();
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);
            ModelPart body = model.body;

            long smashStart = BodyMorphQuirk.getSmashStart(instance);
            float smash = smashStart == Long.MIN_VALUE ? -1.0F : player.level().getGameTime() - smashStart + partialTick;
            boolean grabbing = BodyMorphQuirk.isGrabbing(instance);
            boolean squeezing = BodyMorphQuirk.isSqueezing(instance);

            for (int index = 0; index < 2; index++) {
                boolean left = index == 0;
                float side = left ? 1.0F : -1.0F;
                Vector3f pivot = MultiArmsClient.onBody(body, side * SHOULDER_X * body.xScale, SHOULDER_Y, SHOULDER_Z);

                // Hanging behind the shoulders
                float xRot = 0.8F + Mth.sin(ageInTicks * 0.06F + index) * 0.05F;
                float yRot = body.yRot;
                float zRot = -side * 0.6F;
                float length = ARM_LENGTH;
                if (smash >= 0.0F && smash < BodyMorphQuirk.SMASH_TICKS) {
                    // Raised up and out to the sides, slammed down into the ground beside the body, then back
                    float raise = Mth.clamp(smash / (BodyMorphQuirk.SMASH_HIT - 1.0F), 0.0F, 1.0F);
                    float slam = Mth.clamp((smash - BodyMorphQuirk.SMASH_HIT + 1.0F) / 2.0F, 0.0F, 1.0F);
                    float settle = Mth.clamp((smash - BodyMorphQuirk.SMASH_HIT - 2.0F) / (BodyMorphQuirk.SMASH_TICKS - BodyMorphQuirk.SMASH_HIT - 2.0F), 0.0F, 1.0F);
                    float out = Mth.lerp(slam, Mth.lerp(raise, 0.6F, 2.5F), 0.35F);
                    xRot = Mth.lerp(settle, Mth.lerp(slam, Mth.lerp(raise, xRot, 0.2F), 0.25F), xRot);
                    zRot = -side * Mth.lerp(settle, out, 0.6F);
                    length = Mth.lerp(slam * (1.0F - settle), ARM_LENGTH, SLAM_LENGTH);
                } else if (grabbing) {
                    // Holding up overhead
                    xRot = -3.05F;
                    zRot = side * 0.3F;
                } else if (squeezing) {
                    // Wrapped around the front
                    xRot = -1.35F;
                    yRot = body.yRot + side * 0.55F;
                    zRot = 0.0F;
                }

                MultiArmsClient.renderArm(context, left, PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), xRot + (grabbing ? 0.0F : body.xRot), yRot, zRot), ARM_WIDTH, length, ItemStack.EMPTY);
            }
        }

    }

}
