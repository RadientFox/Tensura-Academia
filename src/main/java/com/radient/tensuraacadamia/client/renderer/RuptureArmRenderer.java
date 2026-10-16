package com.radient.tensuraacadamia.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.entity.RuptureArmEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RuptureArmRenderer extends EntityRenderer<RuptureArmEntity> {

    private static final float ARM_MIDDLE = 0.25F;

    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public RuptureArmRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    private static PlayerSkin getSkin(RuptureArmEntity entity) {
        if (entity.getOwner() instanceof AbstractClientPlayer player) {
            return player.getSkin();
        }
        return DefaultPlayerSkin.get(entity.getOwner() != null ? entity.getOwner().getUUID() : entity.getUUID());
    }

    @Override
    public void render(RuptureArmEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        PlayerSkin skin = getSkin(entity);
        PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? this.slim : this.wide;
        ModelPart arm = entity.isRightArm() ? model.rightArm : model.leftArm;
        ModelPart sleeve = entity.isRightArm() ? model.rightSleeve : model.leftSleeve;
        arm.resetPose();
        arm.x = 0.0F;
        arm.y = 0.0F;
        arm.z = 0.0F;
        sleeve.copyFrom(arm);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        poseStack.translate(0.0F, -ARM_MIDDLE, 0.0F);
        RenderType type = RenderType.entityCutoutNoCull(skin.texture());
        arm.render(poseStack, buffers.getBuffer(type), packedLight, OverlayTexture.NO_OVERLAY);
        sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RuptureArmEntity entity) {
        return getSkin(entity).texture();
    }

}
