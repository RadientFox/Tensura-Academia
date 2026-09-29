package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.TailQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class TailClient {

    private static Optional<ManasSkillInstance> getTail(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TAIL.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static float getAge(long start, AbstractClientPlayer player, float partialTick) {
        return start == Long.MIN_VALUE ? -1.0F : player.level().getGameTime() - start + partialTick;
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new TailLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    private static float getSpin(AbstractClientPlayer player, float partialTick) {
        Optional<ManasSkillInstance> tail = getTail(player);
        if (tail.isEmpty()) {
            return 0.0F;
        }

        float spiral = getAge(TailQuirk.getSpiralStart(tail.get()), player, partialTick);
        if (spiral >= 0.0F && spiral < TailQuirk.SPIRAL_TICKS) {
            return spiral / TailQuirk.SPIRAL_TICKS * 360.0F;
        }

        float whip = getAge(TailQuirk.getWhipStart(tail.get()), player, partialTick);
        if (whip >= 0.0F && whip < TailQuirk.WHIP_TICKS) {
            return -Mth.sin(whip / TailQuirk.WHIP_TICKS * Mth.PI) * 150.0F;
        }

        return 0.0F;
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            float spin = getSpin(player, event.getPartialTick());
            event.getPoseStack().pushPose();
            if (spin != 0.0F) {
                event.getPoseStack().mulPose(Axis.YP.rotationDegrees(spin));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() instanceof AbstractClientPlayer) {
            event.getPoseStack().popPose();
        }
    }

    private static class TailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private static final float BASE_Y = 10.5F;
        private static final float BASE_Z = 2.0F;
        private static final float TAIL_WIDTH = 0.9F;
        private static final int SKIN_U = 10;
        private static final int SKIN_V = 13;
        private static final int TEXEL_SPLIT = 40;
        private static final ModelPart TAIL = createTail();

        private final ItemInHandRenderer itemInHandRenderer;

        TailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }

            Optional<ManasSkillInstance> tail = getTail(player);
            if (tail.isEmpty()) {
                return;
            }

            ManasSkillInstance instance = tail.get();
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            ModelPart body = model.body;
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);
            Vector3f base = MultiArmsClient.onBody(body, 0.0F, BASE_Y, BASE_Z);

            float whip = getAge(TailQuirk.getWhipStart(instance), player, partialTick);
            float spiral = getAge(TailQuirk.getSpiralStart(instance), player, partialTick);
            float leap = getAge(TailQuirk.getLeapStart(instance), player, partialTick);
            LivingEntity wrapped = TailQuirk.isWrapping(instance) && player.level().getEntity(TailQuirk.getWrapTargetId(instance)) instanceof LivingEntity target ? target : null;

            float xRot = 0.7F + body.xRot + Mth.sin(ageInTicks * 0.08F) * 0.08F;
            float yRot = body.yRot + Mth.sin(ageInTicks * 0.06F) * (0.15F + limbSwingAmount * 0.3F);
            float length = 1.0F;
            if (wrapped != null) {
                float scale = player.getScale();
                double back = (-TailQuirk.getWrapOffset(player, wrapped).z) * 16.0D / scale - BASE_Z;
                double up = ((0.15D + wrapped.getBbHeight() * 0.5D) * 16.0D / scale) - (24.0D - BASE_Y);
                xRot = (float) Mth.atan2(back, -up);
                yRot = body.yRot;
                length = (float) ((Math.sqrt(back * back + up * up) - wrapped.getBbWidth() * 8.0D / scale) / MultiArmsClient.HAND_LENGTH);
            } else if ((whip >= 0.0F && whip < TailQuirk.WHIP_TICKS) || (spiral >= 0.0F && spiral < TailQuirk.SPIRAL_TICKS)) {
                xRot = 1.5F;
                yRot = body.yRot;
                length = 1.2F;
            } else if (leap >= 0.0F && leap < TailQuirk.LEAP_PUSH_TICKS) {
                xRot = 0.1F;
                yRot = body.yRot;
            } else if (leap >= 0.0F && !player.onGround()) {
                xRot = 1.2F;
            }

            TAIL.loadPose(PartPose.offsetAndRotation(base.x(), base.y(), base.z(), xRot, yRot, 0.0F));
            TAIL.xScale = TAIL_WIDTH;
            TAIL.yScale = length;
            TAIL.zScale = TAIL_WIDTH;
            TAIL.render(poseStack, buffer.getBuffer(context.renderType()), packedLight, context.overlay());
        }

        private static ModelPart createTail() {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("tail", CubeListBuilder.create().texOffs(SKIN_U * TEXEL_SPLIT, SKIN_V * TEXEL_SPLIT).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.ZERO);
            return LayerDefinition.create(mesh, 64 * TEXEL_SPLIT, 64 * TEXEL_SPLIT).bakeRoot().getChild("tail");
        }

    }

}
