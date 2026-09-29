package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ExtraArmsQuirk;
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

// Extra Arms' two arms hanging from the back
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class ExtraArmsClient {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackArmsLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    private static class BackArmsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private static final float SHOULDER_X = 2.2F;
        private static final float SHOULDER_Y = 2.5F;
        private static final float SHOULDER_Z = 2.6F;
        private static final float ARM_WIDTH = 0.8F;
        private static final float ARM_LENGTH = 0.85F;
        private static final float COUNTER_TICKS = 6.0F;

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

            Optional<ManasSkillInstance> extraArms = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.EXTRA_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
            if (extraArms.isEmpty()) {
                return;
            }

            ManasSkillInstance instance = extraArms.get();
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);
            ModelPart body = model.body;

            long counterTime = ExtraArmsQuirk.getCounterTime(instance);
            float counter = counterTime == Long.MIN_VALUE ? 1.0F : (player.level().getGameTime() - counterTime + partialTick) / COUNTER_TICKS;
            boolean climbing = ExtraArmsQuirk.isClimbing(instance) && player.onClimbable() && !player.onGround();

            for (int slot = 0; slot < ExtraArmsQuirk.ARMS; slot++) {
                boolean left = slot == 0;
                float side = left ? 1.0F : -1.0F;
                Vector3f pivot = MultiArmsClient.onBody(body, side * SHOULDER_X * body.xScale, SHOULDER_Y, SHOULDER_Z);

                // Hanging down behind the back
                float xRot = 0.35F + Mth.sin(ageInTicks * 0.08F + slot) * 0.05F;
                float yRot = body.yRot;
                float zRot = 0.0F;
                if (climbing) {
                    // Hand over hand up the wall
                    xRot = -2.95F + Mth.sin(ageInTicks * 0.5F + slot * Mth.PI) * 0.25F;
                    zRot = side * 0.55F;
                } else if (counter >= 0.0F && counter < 1.0F) {
                    // Punching back at whoever hit from behind
                    xRot += Mth.sin(counter * Mth.PI) * 1.3F;
                }

                MultiArmsClient.renderArm(context, left, PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), xRot + body.xRot, yRot, zRot), ARM_WIDTH, ARM_LENGTH, ItemStack.EMPTY);
            }
        }

    }

}
